package org.otp.dcas_banking_system.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String senderUsername;
    private String receiverUsername;
    private BigDecimal amount;
    private LocalDateTime timestamp;
    private String status; // SUCCESS, FAILED
    private String description;
}