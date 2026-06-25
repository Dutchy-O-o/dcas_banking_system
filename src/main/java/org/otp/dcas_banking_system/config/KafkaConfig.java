package org.otp.dcas_banking_system.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    public static final String TRANSFER_EVENTS_TOPIC = "transfer-events";

    /**
     * 3 partition: aggregateId (hesap no) key oldugu icin ayni hesabin
     * event'leri ayni partition'a duser ve siralari korunur.
     */
    @Bean
    public NewTopic transferEventsTopic() {
        return TopicBuilder.name(TRANSFER_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Consumer hata stratejisi: 1 sn arayla 3 deneme, hala basarisizsa
     * mesaj "transfer-events.DLT" (Dead Letter Topic) konusuna tasinir.
     * Boylece zehirli mesaj (poison pill) tum kuyrugu kilitleyemez.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    }
}
