package org.otp.dcas_banking_system.controller;

import org.otp.dcas_banking_system.model.User;
import org.otp.dcas_banking_system.repository.UserRepository;
import org.otp.dcas_banking_system.service.EmailService;
import org.otp.dcas_banking_system.service.EncryptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.math.BigDecimal;
import java.util.UUID;

@Controller
public class SettingsController {

    @Autowired private UserRepository userRepository;
    @Autowired private EmailService emailService;
    @Autowired private EncryptionService encryptionService;

    @GetMapping("/settings")
    public String showSettings(Authentication auth, Model model) {
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();

        // Mevcut ayarları ön yüze gönder
        model.addAttribute("currentLimit", user.getDcasTransactionLimit());
        model.addAttribute("loginEnabled", user.isLoginSecurityEnabled());
        model.addAttribute("transferEnabled", user.isTransferSecurityEnabled());

        return "settings";
    }

    @PostMapping("/settings/update")
    public String updateSettings(
            @RequestParam BigDecimal limit,
            // Checkbox işaretli değilse null gelir, bunu false yapmak için defaultValue kullanıyoruz
            @RequestParam(defaultValue = "false") boolean loginEnabled,
            @RequestParam(defaultValue = "false") boolean transferEnabled,
            Authentication auth, Model model) {

        User user = userRepository.findByUsername(auth.getName()).orElseThrow();

        if (limit.compareTo(BigDecimal.ZERO) < 0) limit = BigDecimal.ZERO;

        // Ayarları güncelle
        user.setDcasTransactionLimit(limit);
        user.setLoginSecurityEnabled(loginEnabled);
        user.setTransferSecurityEnabled(transferEnabled);

        userRepository.save(user);

        model.addAttribute("success", "Security preferences updated successfully!");

        // Güncel değerleri tekrar sayfaya bas
        model.addAttribute("currentLimit", limit);
        model.addAttribute("loginEnabled", loginEnabled);
        model.addAttribute("transferEnabled", transferEnabled);

        return "settings";
    }

    // 1. Kullanıcı butona basınca Mail Gönder
    @PostMapping("/settings/request-qr")
    public String requestQrRecovery(Authentication auth, Model model) {
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();

        // Token oluştur ve kaydet
        String token = UUID.randomUUID().toString();
        user.setRecoveryToken(token);
        userRepository.save(user);

        // Linki oluştur (Lokalde çalıştığın için localhost)
        String recoveryLink = "https://localhost:8443/setup-2fa?token=" + token;

        // APW'yi çöz ve mail at
        String apw = encryptionService.decrypt(user.getApwEncrypted());
        emailService.sendQrRecoveryEmail(user.getEmail(), user.getFullName(), apw, recoveryLink);

        model.addAttribute("message", "A secure link has been sent to your email!");

        // Ayarlar sayfasına geri dönmek için gerekli dataları tekrar yükle
        model.addAttribute("currentLimit", user.getDcasTransactionLimit());
        model.addAttribute("loginEnabled", user.isLoginSecurityEnabled());
        model.addAttribute("transferEnabled", user.isTransferSecurityEnabled());

        return "settings";
    }

    // 2. Maildeki linke tıklayınca açılacak sayfa
    @GetMapping("/setup-2fa")
    public String viewQrCode(@RequestParam String token, Model model) {
        // Token'a sahip kullanıcıyı bul
        User user = userRepository.findAll().stream()
                .filter(u -> token.equals(u.getRecoveryToken()))
                .findFirst()
                .orElse(null);

        if (user == null) {
            return "redirect:/login?error=invalid_token";
        }

        // Güvenlik için token'ı sil (Tek kullanımlık olsun)
        user.setRecoveryToken(null);
        userRepository.save(user);

        // QR Kod verilerini hazırla
        String secret = encryptionService.decrypt(user.getTotpSecretEncrypted());
        String otpAuthUrl = String.format("otpauth://totp/DCAS_Bank:%s?secret=%s&issuer=DCAS_Bank", user.getEmail(), secret);

        model.addAttribute("otpAuthUrl", otpAuthUrl);
        model.addAttribute("secret", secret);

        return "recover_qr"; // Yeni HTML sayfasını döndür
    }
}