package org.otp.dcas_banking_system.controller;

import org.otp.dcas_banking_system.model.User;
import org.otp.dcas_banking_system.model.Transaction;
import org.otp.dcas_banking_system.repository.*;
import org.otp.dcas_banking_system.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;

@Controller
public class MainController {

    @Autowired private UserRepository userRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private EncryptionService encryptionService;
    @Autowired private EmailService emailService;

    @GetMapping("/")
    public String root() { return "redirect:/dashboard"; }

    @GetMapping("/dashboard")
    public String dashboard(Authentication auth, Model model) {
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();

        // Mail Gönder (Simülasyon - Login Alert)
        String apw = encryptionService.decrypt(user.getApwEncrypted());
        // Gerçekte her refresh'te değil, session başlangıcında atılmalı.
        // Demo olduğu için konsolda görmeniz adına buraya koyuyorum.
       emailService.sendSecurityAlert(user.getEmail(), "New Login Detected", apw,
              "We detected a new login to your DCAS Bank account. If this wasn't you, please contact support.");

        List<Transaction> transactions = transactionRepository
                .findBySenderUsernameOrReceiverUsernameOrderByTimestampDesc(user.getUsername(), user.getUsername());

        model.addAttribute("user", user);
        model.addAttribute("transactions", transactions);

        return "dashboard";
    }
}