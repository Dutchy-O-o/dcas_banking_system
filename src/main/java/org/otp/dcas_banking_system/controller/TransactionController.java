package org.otp.dcas_banking_system.controller;

import org.otp.dcas_banking_system.model.*;
import org.otp.dcas_banking_system.repository.*;
import org.otp.dcas_banking_system.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Controller
public class TransactionController {

    @Autowired private UserRepository userRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private DcasService dcasService;
    @Autowired private EmailService emailService;
    @Autowired private EncryptionService encryptionService;

    @GetMapping("/transfer")
    public String transferPage(Authentication auth, Model model) {
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();
        if (user.isAccountLocked()) return "locked";
        model.addAttribute("balance", user.getBalance());
        return "transfer";
    }

    @PostMapping("/init-transfer")
    public String initTransfer(@RequestParam String receiver,
                               @RequestParam BigDecimal amount,
                               Authentication auth, HttpSession session, Model model) {

        User sender = userRepository.findByUsername(auth.getName()).orElseThrow();

        if (sender.isAccountLocked()) return "locked";
        if (!userRepository.existsByUsername(receiver)) {
            model.addAttribute("error", "Receiver not found!");
            return "transfer";
        }
        if (sender.getBalance().compareTo(amount) < 0) {
            model.addAttribute("error", "Insufficient balance!");
            return "transfer";
        }

        // DCAS Challenge
        DcasService.DcasChallenge challenge = dcasService.generateChallenge(sender);

        // Save state to session
        session.setAttribute("expectedResponse", challenge.expectedResponse);
        session.setAttribute("tx_receiver", receiver);
        session.setAttribute("tx_amount", amount);

        model.addAttribute("instruction", challenge.instruction);
        return "verify";
    }

    @PostMapping("/verify-transfer")
    public String verifyTransfer(@RequestParam String userResponse, Authentication auth, HttpSession session, Model model) {
        String expected = (String) session.getAttribute("expectedResponse");
        String receiverName = (String) session.getAttribute("tx_receiver");
        BigDecimal amount = (BigDecimal) session.getAttribute("tx_amount");

        User sender = userRepository.findByUsername(auth.getName()).orElseThrow();

        if (expected != null && expected.equals(userResponse)) {
            // SUCCESS LOGIC
            User receiver = userRepository.findByUsername(receiverName).orElseThrow();

            // Update Balances
            sender.setBalance(sender.getBalance().subtract(amount));
            receiver.setBalance(receiver.getBalance().add(amount));
            sender.setFailedAttempts(0);

            userRepository.save(sender);
            userRepository.save(receiver);

            // Record Transaction
            Transaction tx = new Transaction();
            tx.setSenderUsername(sender.getUsername());
            tx.setReceiverUsername(receiver.getUsername());
            tx.setAmount(amount);
            tx.setTimestamp(LocalDateTime.now());
            tx.setStatus("SUCCESS");
            tx.setDescription("Money Transfer via DCAS");
            transactionRepository.save(tx);

            // Send Email
            String apw = encryptionService.decrypt(sender.getApwEncrypted());
          emailService.sendSecurityAlert(sender.getEmail(), "Transfer Successful", apw,
               "You have successfully transferred $" + amount + " to " + receiverName);

            return "redirect:/success";
        } else {
            // FAIL LOGIC
            int attempts = sender.getFailedAttempts() + 1;
            sender.setFailedAttempts(attempts);
            if (attempts >= 3) sender.setAccountLocked(true);
            userRepository.save(sender);

            if (sender.isAccountLocked()) return "locked";

            model.addAttribute("error", "Incorrect inputs! Remaining attempts: " + (3 - attempts));
            model.addAttribute("instruction", "Please restart the process for a new challenge.");
            return "verify_retry";
        }
    }

    @GetMapping("/success")
    public String successPage() { return "success"; }
}