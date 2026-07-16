package org.cloudback.order.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.common.entity.OutboxMessage;
import org.cloudback.common.service.OutboxService;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final OutboxService outboxService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final int BATCH_SIZE = 20;
    private static final List<String> TOPICS = List.of(
            SystemConstants.KAFKA_TOPIC_ORDER_CREATE,
            SystemConstants.KAFKA_TOPIC_STOCK_RESTORE
    );

    @Scheduled(fixedRate = 5000)
    public void sendPendingMessages() {
        for (String topic : TOPICS) {
            outboxService.recoverTimedOutSending(topic);
            List<OutboxMessage> pending = outboxService.pollPending(topic, BATCH_SIZE);
            for (OutboxMessage msg : pending) {
                if (!outboxService.claimMessage(msg.getId())) {
                    continue;
                }
                try {
                    kafkaTemplate.send(msg.getTopic(), msg.getMessageKey(), msg.getPayload()).get(3, TimeUnit.SECONDS);
                    outboxService.markSent(msg.getId());
                    log.debug("Outbox sent: id={}, topic={}, key={}", msg.getId(), msg.getTopic(), msg.getMessageKey());
                } catch (Exception e) {
                    log.error("Outbox send failed: id={}, topic={}, key={}, error={}",
                            msg.getId(), msg.getTopic(), msg.getMessageKey(), e.getMessage());
                    outboxService.markFailed(msg.getId(), msg.getRetryCount());
                }
            }
        }
    }
}
