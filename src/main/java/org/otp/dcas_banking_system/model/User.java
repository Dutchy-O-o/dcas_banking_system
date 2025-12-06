package org.otp.dcas_banking_system.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String username;

    private String password; // Giriş parolası (Hashli)

    // DCAS İçin Kritik Alanlar (AES ile Şifreli Saklanacak)
    private String tswEncrypted; // Transaction Security Word
    private String totpSecretEncrypted; // Google Auth Secret Key

    private int failedAttempts = 0; // Hatalı deneme sayısı
    private boolean accountLocked = false; // Kilit durumu
}
