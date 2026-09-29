package org.cloudback.order.service;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.constant.SystemConstants;
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
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
    @Value("${cloud.internal.token:}")
    private String internalToken;

    private static final int MAX_COMPENSATE_ATTEMPTS = 10;

    private TransactionTemplate transaction() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(10);
        return tx;
    }

    /**
     * 补偿专用事务：补偿过程会逐个商品远程调用库存回滚（含重试的 Feign 延迟），
     * 超时必须远大于普通状态流转事务，否则会误回滚导致计数白白消耗。
     */
    private TransactionTemplate compensationTransaction() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(60);
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
        OrderRequest visible = owned(userId, token);
        if ("SUCCEEDED".equals(visible.getStatus())) return completed(visible);
        if (!"READY".equals(visible.getStatus())) throw new BusinessException(message(visible.getStatus()));
        if (!visible.getExpiresAt().isAfter(LocalDateTime.now())) throw new BusinessException("下单请求已过期，请重新获取");

        // prepare 内部是多次 Feign 调用，必须移出行锁事务，避免长事务耗尽连接池。
        // 该回调无副作用，校验失败不触碰请求状态，READY 仍可复用。
        Order preparedOrder;
        try {
            preparedOrder = prepare.apply(visible.getOrderNo());
        } catch (BusinessException validation) {
            // Only this side-effect-free callback may authorize editing a failed request.
            throw new BusinessException(42201, validation.getMessage());
        }

        // 行锁内重新校验后保存快照（无锁读取与加锁之间状态可能已变化）
        boolean claimed = Boolean.TRUE.equals(transaction().execute(tx -> {
            OrderRequest request = lockOwned(userId, token);
            if ("SUCCEEDED".equals(request.getStatus())) return false;
            if (!"READY".equals(request.getStatus())) throw new BusinessException(message(request.getStatus()));
            if (!request.getExpiresAt().isAfter(LocalDateTime.now())) throw new BusinessException("下单请求已过期，请重新获取");
            request.setSnapshot(JSON.toJSONString(preparedOrder));
            request.setStatus("PROCESSING");
            save(request);
            return true;
        }));
        if (!claimed) return completed(owned(userId, token));

        try {
            // 事务 A：仅校验状态并读取快照
            Order order = transaction().execute(tx -> {
                OrderRequest request = lockOwned(userId, token);
                if ("SUCCEEDED".equals(request.getStatus())) return completed(request);
                if (!"PROCESSING".equals(request.getStatus())) throw new BusinessException(message(request.getStatus()));
                return snapshot(request);
            });
            // 事务外执行远程扣减（幂等由 product 端 tombstone 保证）
            execute.accept(order);
            // 事务 B：确认终态
            return transaction().execute(tx -> {
                OrderRequest request = lockOwned(userId, token);
                if ("SUCCEEDED".equals(request.getStatus())) return completed(request);
                if (!"PROCESSING".equals(request.getStatus())) throw new BusinessException(message(request.getStatus()));
                terminal(request, "SUCCEEDED");
                return order;
            });
        } catch (Exception failure) {
            // A failed commit response is ambiguous. Re-read under a lock before deciding to compensate.
            BusinessException passthrough = null;
            try {
                markCompensating(token, false);
                compensate(token);
                OrderRequest latest = owned(userId, token);
                if ("SUCCEEDED".equals(latest.getStatus())) return completed(latest);
                if (failure instanceof BusinessException business) {
                    // 补偿完成后把真实失败原因留给用户查询，并原样抛出
                    recordFailureReason(token, business.getMessage());
                    passthrough = new BusinessException(business.getCode(), business.getMessage());
                }
            } catch (Exception deferred) {
                log.warn("Checkout recovery deferred: token={}", token, deferred);
            }
            if (passthrough != null) throw passthrough;
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
        // 独立小事务递增补偿计数：超过上限直接终态 FAILED，防止无限重试锁死用户下单位
        boolean allowed = Boolean.TRUE.equals(transaction().execute(tx -> {
            OrderRequest request = requests.lock(token);
            if (request == null || !"COMPENSATING".equals(request.getStatus())) return false;
            int attempts = request.getCompensateCount() == null ? 0 : request.getCompensateCount();
            if (attempts >= MAX_COMPENSATE_ATTEMPTS) {
                request.setFailureReason("库存自动恢复失败，请联系客服或等待人工处理");
                terminal(request, "FAILED");
                log.error("库存补偿达到上限，转人工处理: token={}, orderNo={}", token, request.getOrderNo());
                return false;
            }
            request.setCompensateCount(attempts + 1);
            save(request);
            return true;
        }));
        if (!allowed) return;
        // 补偿事务内含逐个商品的远程 restoreStock 调用，不能套用 10 秒的短超时
        compensationTransaction().executeWithoutResult(tx -> {
            OrderRequest request = requests.lock(token);
            if (request == null || !"COMPENSATING".equals(request.getStatus())) return;
            Order order = snapshot(request);
            // Restore every snapshot item, including requests whose remote outcome was unknown.
            // The product service creates a RESTORED tombstone even when deduction never arrived.
            for (OrderItem item : order.getOrderItems()) {
                R<String> response = products.restoreStock(item.getProductId(), item.getQuantity(), request.getOrderNo(), internalToken);
                if (response == null || response.getCode() != 200) throw new BusinessException("库存恢复尚未完成");
            }
            // 下单已实际落库但结果未知时，库存已退回，必须同步取消这笔孤儿订单，
            // 否则它仍处于待支付状态可被付款，造成"钱付了而库存已退还"的超卖资损。
            cancelOrphanOrder(request);
            terminal(request, "FAILED");
        });
    }

    private void recordFailureReason(String token, String reason) {
        transaction().executeWithoutResult(tx -> {
            OrderRequest request = requests.lock(token);
            if (request == null) return;
            request.setFailureReason(reason);
            save(request);
        });
    }

    /** 仅对待支付订单生效；订单尚未落库时影响 0 行，属正常情况 */
    private void cancelOrphanOrder(OrderRequest request) {
        try {
            int rows = orders.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getOrderNo, request.getOrderNo())
                    .eq(Order::getStatus, SystemConstants.ORDER_STATUS_UNPAID)
                    .set(Order::getStatus, SystemConstants.ORDER_STATUS_CANCELLED));
            if (rows > 0) {
                log.warn("下单结果未知已回滚库存，孤儿订单同步取消: orderNo={}", request.getOrderNo());
            }
        } catch (Exception e) {
            log.error("取消孤儿订单失败，需人工处理: orderNo={}", request.getOrderNo(), e);
            throw new BusinessException("孤儿订单取消失败，请人工核对");
        }
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
