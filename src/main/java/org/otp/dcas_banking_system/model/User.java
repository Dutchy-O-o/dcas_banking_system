package org.otp.dcas_banking_system.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Spring Security için ID (E-posta olacak)
    @Column(unique = true, nullable = false)
    private String username;

    // Ekranda görünecek GERÇEK isim (Örn: Ahmet Yılmaz)
    private String fullName;

    @Column(nullable = false)
    private String email;

    private String password;

    // Güvenlik Alanları (Şifreli)
    private String tswEncrypted;
    private String apwEncrypted;
    private String totpSecretEncrypted;

    // Bankacılık Alanları
    private BigDecimal balance = new BigDecimal("10000.00");
    @Column(unique = true)
    private String accountNumber; // IBAN (TR...)

    // Kullanıcı Ayarları
    private BigDecimal dcasTransactionLimit = BigDecimal.ZERO; // 0 = Hep sor
    private boolean loginSecurityEnabled = false;    // Girişte OTP sorulsun mu?
    private boolean transferSecurityEnabled = true; // Transferde OTP sorulsun mu?

    // Kilit Mekanizması
    private int failedAttempts = 0;
    private boolean accountLocked = false;
    private LocalDateTime lockTime;
    private String unlockToken;
    // User.java dosyasının içine, diğer alanların altına ekle:
    private String recoveryToken;
}