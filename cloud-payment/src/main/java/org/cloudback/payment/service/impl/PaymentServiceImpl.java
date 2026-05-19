package org.cloudback.payment.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.exception.BusinessException;
import org.cloudback.common.result.R;
import org.cloudback.payment.mapper.PaymentMapper;
import org.cloudback.payment.model.entity.Payment;
import org.cloudback.payment.service.PaymentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付服务实现，处理支付记录创建和查询。
 * 模拟支付：生成雪花算法交易号，直接标记支付成功。
 * 对接支付宝沙箱时替换 processPayment 中逻辑。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentMapper paymentMapper;

    /** 根据订单号查询支付记录 */
    @Override
    public R<Payment> getPaymentByOrderNo(String orderNo) {
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
        if (payment == null) {
            throw new BusinessException("支付记录不存在");
        }
        return R.ok(payment);
    }

    /** 处理支付：生成交易号 → 创建支付记录 → 标记成功 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<String> processPayment(String orderNo, Long userId, BigDecimal amount) {
        // 幂等：已支付过的订单不重复创建
        Long existCount = paymentMapper.selectCount(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
        if (existCount > 0) {
            log.warn("支付记录已存在，跳过: orderNo={}", orderNo);
            return R.ok("已支付");
        }

        String tradeNo = IdUtil.getSnowflakeNextIdStr();

        Payment payment = new Payment();
        payment.setOrderNo(orderNo);
        payment.setUserId(userId);
        payment.setAmount(amount);
        payment.setPayMethod("ALIPAY");
        payment.setStatus(1);
        payment.setTradeNo(tradeNo);
        payment.setPayTime(LocalDateTime.now());

        paymentMapper.insert(payment);

        log.info("支付成功: orderNo={}, tradeNo={}, amount={}", orderNo, tradeNo, amount);
        return R.ok("支付成功");
    }
}
