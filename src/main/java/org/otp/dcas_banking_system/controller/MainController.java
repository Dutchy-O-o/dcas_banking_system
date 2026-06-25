package org.otp.dcas_banking_system.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.otp.dcas_banking_system.model.Transaction;
import org.otp.dcas_banking_system.model.User;
import org.otp.dcas_banking_system.repository.*;
import org.otp.dcas_banking_system.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class MainController {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final EncryptionService encryptionService;
    private final EmailService emailService;

    @GetMapping("/") public String root() { return "redirect:/dashboard"; }

    @GetMapping("/dashboard")
    public String dashboard(Authentication auth, HttpSession session, Model model) {
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();

        log.info("Dashboard accessed by {}", auth.getName());
        if (session.getAttribute("login_alert_sent") == null) {
            String apw = encryptionService.decrypt(user.getApwEncrypted());


            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
            String formattedTime = java.time.LocalDateTime.now().format(formatter);

            emailService.sendLoginNotification(
                    user.getEmail(),
                    user.getFullName(),
                    apw,
                    formattedTime // Artık düzeltilmiş saati gönderiyoruz
            );



            session.setAttribute("login_alert_sent", true);
        }

        List<Transaction> transactions = transactionRepository
                .findBySenderUsernameOrReceiverUsernameOrderByTimestampDesc(user.getFullName(), user.getFullName());

        model.addAttribute("user", user);
        model.addAttribute("transactions", transactions);
        return "dashboard";
    }
}