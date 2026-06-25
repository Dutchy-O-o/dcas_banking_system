package org.otp.dcas_banking_system.dto.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transfer tamamlandiginda outbox uzerinden Kafka'ya yayinlanan event.
 *
 * GUVENLIK NOTU: APW (anti-phishing word) gibi sirlar payload'a KONULMAZ —
 * Kafka log'lari kalicidir. Consumer, kullaniciyi DB'den cekip sirri
 * gonderim aninda cozer.
 */
public record TransferCompletedEvent(
        Long txId,
        String senderUsername,
        String senderAccountNumber,
        BigDecimal amount,
        String receiverFullName,
        LocalDateTime occurredAt
) {
}
