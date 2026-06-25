package org.otp.dcas_banking_system.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.otp.dcas_banking_system.config.KafkaConfig;
import org.otp.dcas_banking_system.model.OutboxEvent;
import org.otp.dcas_banking_system.repository.OutboxEventRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Transactional Outbox'in "relay" tarafi.
 *
 * Outbox tablosundaki yayinlanmamis event'leri sirayla Kafka'ya gonderir.
 * Kafka erisilemezse event tabloda kalir, sonraki tick'te tekrar denenir —
 * yani teslimat garantisi "at-least-once"dir. Consumer'in idempotent
 * olmasi (ya da yan etkinin tekrarinin kabul edilebilir olmasi) beklenir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findTop50ByPublishedAtIsNullOrderByIdAsc();
        for (OutboxEvent event : pending) {
            try {
                // Senkron bekliyoruz: "gonderildi" isaretini ancak broker ack'lerse koyariz
                kafkaTemplate.send(KafkaConfig.TRANSFER_EVENTS_TOPIC,
                                event.getAggregateId(), event.getPayload())
                        .get(5, TimeUnit.SECONDS);
                event.setPublishedAt(LocalDateTime.now());
                log.info("Outbox event published: id={}, type={}", event.getId(), event.getEventType());
            } catch (Exception e) {
                // Sira bozulmasin diye burada durup sonraki tick'te bastan deniyoruz
                log.error("Failed to publish outbox event id={}, will retry: {}",
                        event.getId(), e.getMessage());
                break;
            }
        }
    }
}
