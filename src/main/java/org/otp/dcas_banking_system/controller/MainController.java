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

        if (user.isLoginSecurityEnabled() && session.getAttribute("login_alert_sent") == null) {
            String apw = encryptionService.decrypt(user.getApwEncrypted());
            emailService.sendSecurityAlert(user.getEmail(), user.getFullName(), "New Login", apw, "Login detected.");
            session.setAttribute("login_alert_sent", true);
        }

        List<Transaction> transactions = transactionRepository
                .findBySenderUsernameOrReceiverUsernameOrderByTimestampDesc(user.getFullName(), user.getFullName());

        model.addAttribute("user", user);
        model.addAttribute("transactions", transactions);
        return "dashboard";
    }
}