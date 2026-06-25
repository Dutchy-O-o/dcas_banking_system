package org.otp.dcas_banking_system.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Transactional Outbox kaydi.
 *
 * Is verisi (transfer) ile event, AYNI veritabani transaction'inda yazilir.
 * Boylece "DB'ye yazildi ama Kafka'ya gidemedi" (dual-write) tutarsizligi olusamaz.
 * Ayri bir poller ({@code OutboxPublisher}) bu tabloyu okuyup Kafka'ya yayinlar.
 */
@Entity
@Table(name = "outbox_event")
@Data
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Event'in ait oldugu aggregate turu, orn. "TRANSFER" */
    private String aggregateType;

    /** Kafka partition key olarak kullanilir — ayni hesabin event'leri sirali kalir */
    private String aggregateId;

    /** Orn. "TRANSFER_COMPLETED" */
    private String eventType;

    /** Serialize edilmis JSON payload */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    private LocalDateTime createdAt;

    /** null ise henuz Kafka'ya yayinlanmadi */
    private LocalDateTime publishedAt;
}
