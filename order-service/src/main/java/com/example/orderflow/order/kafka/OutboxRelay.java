package com.example.orderflow.order.kafka;

import com.example.orderflow.order.model.OutboxEvent;
import com.example.orderflow.order.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private static final int BATCH_SIZE = 100;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:2000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outboxRepository.findUnpublishedBatch(BATCH_SIZE);
        if (batch.isEmpty()) {
            return;
        }

        log.debug("Outbox relay: found {} unpublished event(s)", batch.size());

        for (OutboxEvent event : batch) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload()).get();
                event.setPublishedAt(Instant.now());
                log.info("Outbox relay: published event id={}, aggregateId={}, topic={}",
                        event.getId(), event.getAggregateId(), event.getTopic());
            } catch (Exception e) {
                log.error("Outbox relay: failed to publish event id={}, will retry next tick: {}",
                        event.getId(), e.getMessage());
                // Przywroc flage przerwania, jesli czekanie na ACK zostalo przerwane (np. shutdown).
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                break;
            }
        }
    }
}
