package org.otp.dcas_banking_system.dto;

import org.otp.dcas_banking_system.model.Transaction;
import org.otp.dcas_banking_system.model.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        String senderName,
        String receiverName,
        BigDecimal amount,
        LocalDateTime timestamp,
        TransactionStatus status,
        String description
) {
    public static TransactionResponse from(Transaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getSenderUsername(),
                tx.getReceiverUsername(),
                tx.getAmount(),
                tx.getTimestamp(),
                tx.getStatus(),
                tx.getDescription()
        );
    }
}
