package org.otp.dcas_banking_system.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Data
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String email;

    private String password;

    // Encrypted Security Fields
    private String tswEncrypted; // Transaction Security Word
    private String apwEncrypted; // Anti-Phishing Word (NEW)
    private String totpSecretEncrypted;

    // Banking Fields
    private BigDecimal balance = new BigDecimal("10000.00"); // Başlangıç bakiyesi 10k $
    private String accountNumber; // Rastgele hesap no

    private int failedAttempts = 0;
    private boolean accountLocked = false;
}