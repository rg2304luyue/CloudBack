package org.cloudback.order.service;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.exception.BusinessException;
import org.cloudback.common.result.R;
import org.cloudback.order.dto.OrderRequestResult;
import org.cloudback.order.feign.ProductFeignClient;
import org.cloudback.order.mapper.OrderMapper;
import org.cloudback.order.mapper.OrderRequestMapper;
import org.cloudback.order.model.entity.Order;
import org.cloudback.order.model.entity.OrderItem;
import org.cloudback.order.model.entity.OrderRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/** Each remote inventory call is fenced by the durable request's database row lock. */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderRequestService {
    private final OrderRequestMapper requests;
    private final OrderMapper orders;
    private final ProductFeignClient products;
    private final PlatformTransactionManager transactionManager;
    @Value("${cloud.internal.token}")
    private String internalToken;

    private TransactionTemplate transaction() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx;
    }

    public String issue(Long userId) {
        if (userId == null) throw new BusinessException("用户不能为空");
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                String token = transaction().execute(tx -> {
                    OrderRequest active = requests.active(userId);
                    if (active != null) {
                        if (!"READY".equals(active.getStatus()) || active.getExpiresAt().isAfter(LocalDateTime.now())) {
                            return active.getToken();
                        }
                        OrderRequest locked = requests.lock(active.getToken());
                        if (locked == null) return null;
                        if (!"READY".equals(locked.getStatus()) || locked.getExpiresAt().isAfter(LocalDateTime.now())) {
                            return locked.getToken();
                        }
                        terminal(locked, "EXPIRED");
                    }
                    OrderRequest request = new OrderRequest();
                    request.setToken(UUID.randomUUID().toString());
                    request.setUserId(userId);
                    request.setActiveUserId(userId);
                    request.setOrderNo(IdUtil.getSnowflakeNextIdStr());
                    request.setStatus("READY");
                    request.setCreatedAt(LocalDateTime.now());
                    request.setUpdatedAt(request.getCreatedAt());
                    request.setExpiresAt(request.getCreatedAt().plusMinutes(30));
                    requests.insert(request);
                    return request.getToken();
                });
                if (token != null) return token;
            } catch (DuplicateKeyException concurrentIssue) {
                // The active-user unique key chooses one request across all service instances.
            }
        }
        throw new BusinessException("下单请求正在创建，请稍后重试");
    }

    public OrderRequestResult result(Long userId, String token) {
        OrderRequest visible = owned(userId, token);
        // READY may be the last committed value while another transaction is preparing
        // the snapshot. A non-blocking row lock prevents the client from treating that
        // stale value as permission to edit and resubmit the request.
        OrderRequest request = transaction().execute(tx -> {
            OrderRequest locked = requests.lock(token);
            if (locked == null) return null;
            if (!Objects.equals(userId, locked.getUserId())) throw new BusinessException("下单请求不存在");
            if ("READY".equals(locked.getStatus()) && !locked.getExpiresAt().isAfter(LocalDateTime.now())) {
                terminal(locked, "EXPIRED");
            }
            return locked;
        });
        if (request == null) {
            return new OrderRequestResult("PROCESSING", null, visible.getOrderNo(), message("PROCESSING"));
        }
        String state = request.getStatus();
        Order order = "SUCCEEDED".equals(state) ? completed(request) : null;
        return new OrderRequestResult(state, order == null ? null : order.getId().toString(),
                request.getOrderNo(), message(state));
    }

    public Order create(Long userId, String token, Function<String, Order> prepare, Consumer<Order> execute) {
        owned(userId, token);
        // No stock side effects in this transaction: validation failure leaves READY reusable.
        boolean prepared = Boolean.TRUE.equals(transaction().execute(tx -> {
            OrderRequest request = lockOwned(userId, token);
            if ("SUCCEEDED".equals(request.getStatus())) return false;
            if (!"READY".equals(request.getStatus())) throw new BusinessException(message(request.getStatus()));
            if (!request.getExpiresAt().isAfter(LocalDateTime.now())) throw new BusinessException("下单请求已过期，请重新获取");
            Order snapshot;
            try {
                snapshot = prepare.apply(request.getOrderNo());
            } catch (BusinessException validation) {
                // Only this side-effect-free callback may authorize editing a failed request.
                throw new BusinessException(42201, validation.getMessage());
            }
            request.setSnapshot(JSON.toJSONString(snapshot));
            request.setStatus("PROCESSING");
            save(request);
            return true;
        }));
        if (!prepared) return completed(owned(userId, token));
        try {
            return transaction().execute(tx -> {
                OrderRequest request = lockOwned(userId, token);
                if ("SUCCEEDED".equals(request.getStatus())) return completed(request);
                if (!"PROCESSING".equals(request.getStatus())) throw new BusinessException(message(request.getStatus()));
                Order order = snapshot(request);
                execute.accept(order);
                terminal(request, "SUCCEEDED");
                return order;
            });
        } catch (Exception failure) {
            // A failed commit response is ambiguous. Re-read under a lock before deciding to compensate.
            try {
                markCompensating(token, false);
                compensate(token);
                OrderRequest latest = owned(userId, token);
                if ("SUCCEEDED".equals(latest.getStatus())) return completed(latest);
            } catch (Exception deferred) {
                log.warn("Checkout recovery deferred: token={}", token, deferred);
            }
            throw new BusinessException("下单结果正在确认，请查询本次请求状态，勿重新下单");
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void recover() {
        for (String token : requests.recoverable(LocalDateTime.now().minusMinutes(5), LocalDateTime.now().minusSeconds(30))) {
            try {
                markCompensating(token, true);
                compensate(token);
            } catch (Exception failure) {
                log.warn("Checkout recovery will retry: token={}", token, failure);
            }
        }
    }

    private void markCompensating(String token, boolean staleOnly) {
        transaction().executeWithoutResult(tx -> {
            OrderRequest request = requests.lock(token);
            if (request == null) return;
            if ("COMPENSATING".equals(request.getStatus())) {
                if (!staleOnly || !request.getUpdatedAt().isAfter(LocalDateTime.now().minusSeconds(30))) save(request);
                return;
            }
            if (!"PROCESSING".equals(request.getStatus())) return;
            if (staleOnly && request.getUpdatedAt().isAfter(LocalDateTime.now().minusMinutes(5))) return;
            request.setStatus("COMPENSATING");
            request.setFailureReason("下单未完成，库存恢复后可重新下单");
            save(request);
        });
    }

    private void compensate(String token) {
        transaction().executeWithoutResult(tx -> {
            OrderRequest request = requests.lock(token);
            if (request == null || !"COMPENSATING".equals(request.getStatus())) return;
            Order order = snapshot(request);
            // Restore every snapshot item, including requests whose remote outcome was unknown.
            // The product service creates a RESTORED tombstone even when deduction never arrived.
            for (OrderItem item : order.getOrderItems()) {
                R<String> response = products.restoreStock(item.getProductId(), item.getQuantity(), request.getOrderNo(), internalToken);
                if (response == null || response.getCode() != 200) throw new BusinessException("库存恢复尚未完成");
            }
            terminal(request, "FAILED");
        });
    }

    private OrderRequest owned(Long userId, String token) {
        if (token == null || token.isBlank()) throw new BusinessException("下单请求编号不能为空");
        OrderRequest request = requests.find(token);
        if (request == null || !Objects.equals(userId, request.getUserId())) throw new BusinessException("下单请求不存在");
        return request;
    }

    private OrderRequest lockOwned(Long userId, String token) {
        OrderRequest request = requests.lock(token);
        if (request == null) throw new BusinessException("下单请求正在处理中，请查询结果");
        if (!Objects.equals(userId, request.getUserId())) throw new BusinessException("下单请求不存在");
        return request;
    }

    private Order completed(OrderRequest request) {
        Order order = orders.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, request.getOrderNo())
                .eq(Order::getUserId, request.getUserId()));
        if (order == null) throw new BusinessException("订单结果暂时无法读取，请稍后查询");
        return order;
    }

    private Order snapshot(OrderRequest request) {
        Order order = JSON.parseObject(request.getSnapshot(), Order.class);
        if (order == null || order.getOrderItems() == null || order.getOrderItems().isEmpty()
                || !Objects.equals(order.getOrderNo(), request.getOrderNo())
                || !Objects.equals(order.getUserId(), request.getUserId())) throw new BusinessException("下单快照异常，需要人工核对");
        return order;
    }

    private void terminal(OrderRequest request, String state) {
        request.setStatus(state);
        request.setActiveUserId(null);
        save(request);
    }

    private void save(OrderRequest request) {
        request.setUpdatedAt(LocalDateTime.now());
        if (requests.update(request) != 1) throw new BusinessException("下单请求状态保存失败");
    }

    private String message(String state) {
        return switch (state) {
            case "READY" -> "可以提交订单";
            case "SUCCEEDED" -> "下单成功";
            case "FAILED" -> "下单未完成，库存已恢复，可以重新下单";
            case "EXPIRED" -> "下单请求已过期，请重新获取";
            case "COMPENSATING" -> "正在恢复库存，请稍后查询";
            default -> "下单正在处理中，请稍后查询";
        };
    }
}
