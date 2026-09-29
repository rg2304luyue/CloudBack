package org.cloudback.payment.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.common.service.OutboxService;
import org.cloudback.payment.config.AlipayProperties;
import org.cloudback.payment.feign.OrderFeignClient;
import org.cloudback.payment.mapper.PaymentMapper;
import org.cloudback.payment.model.entity.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentServiceImplTest {
    private PaymentMapper mapper;
    private AlipayClient alipay;
    private OutboxService outbox;
    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), Payment.class);
        mapper = mock(PaymentMapper.class);
        alipay = mock(AlipayClient.class);
        outbox = mock(OutboxService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenAnswer(ignored -> new SimpleTransactionStatus());
        service = new PaymentServiceImpl(mapper, alipay, new AlipayProperties(), mock(KafkaTemplate.class),
                outbox, mock(OrderFeignClient.class), transactionManager);
    }

    @Test
    void callbackWinningBeforeTimestampUpdateReturnsCurrentPaidRecordWithoutQueryingAlipay() {
        Payment pending = payment(0);
        Payment paid = payment(1);
        when(mapper.selectOne(any())).thenReturn(pending, paid);
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

        assertSame(paid, service.syncPaymentStatus("order-test", 7L).getData());

        ArgumentCaptor<Wrapper<Payment>> reads = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper, times(2)).selectOne(reads.capture());
        assertTrue(reads.getAllValues().get(1).getSqlSegment().contains("FOR UPDATE"),
                "The losing updater must bypass the old repeatable-read snapshot");
        verify(mapper, never()).updateById(any(Payment.class));
        verifyNoInteractions(alipay, outbox);
    }

    @Test
    void queryFailureOnlyWritesTimestampAndNeverWritesStaleStatus() throws Exception {
        when(mapper.selectOne(any())).thenReturn(payment(0));
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(alipay.execute(any(AlipayTradeQueryRequest.class))).thenThrow(new AlipayApiException("timeout"));

        service.syncPaymentStatus("order-test", 7L);

        ArgumentCaptor<Wrapper<Payment>> updates = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(isNull(), updates.capture());
        LambdaUpdateWrapper<Payment> update = (LambdaUpdateWrapper<Payment>) updates.getValue();
        assertTrue(update.getSqlSet().startsWith("last_sync_time="));
        assertFalse(update.getSqlSet().contains("status"));
        assertTrue(update.getSqlSegment().contains("status"));
        verify(mapper, never()).updateById(any(Payment.class));
        verifyNoInteractions(outbox);
    }

    @Test
    void successfulQueryPublishesOneOutboxAndRepeatedSyncDoesNotPublishAgain() throws Exception {
        Payment paid = payment(1);
        when(mapper.selectOne(any())).thenReturn(payment(0), paid, paid);
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        AlipayTradeQueryResponse response = new AlipayTradeQueryResponse();
        response.setCode("10000");
        response.setTradeStatus("TRADE_SUCCESS");
        response.setTradeNo("trade-test");
        when(alipay.execute(any(AlipayTradeQueryRequest.class))).thenReturn(response);

        assertSame(paid, service.syncPaymentStatus("order-test", 7L).getData());
        assertSame(paid, service.syncPaymentStatus("order-test", 7L).getData());

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(outbox).saveMessage(eq(SystemConstants.KAFKA_TOPIC_PAYMENT_RESULT), eq("order-test"), payload.capture());
        JSONObject event = JSONObject.parseObject(payload.getValue());
        assertEquals("SUCCESS", event.getString("status"));
        assertEquals("trade-test", event.getString("tradeNo"));
        assertEquals("order-test", event.getString("orderNo"));
        verify(alipay).execute(any(AlipayTradeQueryRequest.class));
    }

    @Test
    void outboxFailurePropagatesToTransactionBoundary() throws Exception {
        when(mapper.selectOne(any())).thenReturn(payment(0));
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        AlipayTradeQueryResponse response = new AlipayTradeQueryResponse();
        response.setCode("10000");
        response.setTradeStatus("TRADE_SUCCESS");
        response.setTradeNo("trade-test");
        when(alipay.execute(any(AlipayTradeQueryRequest.class))).thenReturn(response);
        doThrow(new IllegalStateException("outbox unavailable")).when(outbox).saveMessage(anyString(), anyString(), anyString());

        assertThrows(IllegalStateException.class, () -> service.syncPaymentStatus("order-test", 7L));
    }

    private Payment payment(int status) {
        Payment payment = new Payment();
        payment.setOrderNo("order-test");
        payment.setUserId(7L);
        payment.setStatus(status);
        payment.setAmount(new BigDecimal("12.34"));
        payment.setPayMethod("ALIPAY");
        if (status == 1) payment.setTradeNo("trade-test");
        return payment;
    }
}
