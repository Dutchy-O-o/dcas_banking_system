package org.otp.dcas_banking_system.controller;

import org.otp.dcas_banking_system.model.Transaction;
import org.otp.dcas_banking_system.model.User;
import org.otp.dcas_banking_system.repository.*;
import org.otp.dcas_banking_system.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
public class MainController {

    @Autowired private UserRepository userRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private EncryptionService encryptionService;
    @Autowired private EmailService emailService;

    @GetMapping("/") public String root() { return "redirect:/dashboard"; }

    @GetMapping("/dashboard")
    public String dashboard(Authentication auth, HttpSession session, Model model) {
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();

        if (session.getAttribute("login_alert_sent") == null) {
            String apw = encryptionService.decrypt(user.getApwEncrypted());

            // --- DEĞİŞİKLİK BURADA BAŞLIYOR ---

            // Tarihi daha okunaklı bir formata çeviriyoruz
            // Örnek Çıktı: 14-12-2025 17:46:21
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
            String formattedTime = java.time.LocalDateTime.now().format(formatter);

            emailService.sendLoginNotification(
                    user.getEmail(),
                    user.getFullName(),
                    apw,
                    formattedTime // Artık düzeltilmiş saati gönderiyoruz
            );

            // --- DEĞİŞİKLİK BURADA BİTİYOR ---

            session.setAttribute("login_alert_sent", true);
        }

        List<Transaction> transactions = transactionRepository
                .findBySenderUsernameOrReceiverUsernameOrderByTimestampDesc(user.getFullName(), user.getFullName());

        model.addAttribute("user", user);
        model.addAttribute("transactions", transactions);
        return "dashboard";
    }
}