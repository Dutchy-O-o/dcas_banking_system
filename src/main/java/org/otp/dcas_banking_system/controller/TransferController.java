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
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Controller
public class TransferController {

    @Autowired private UserRepository userRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private DcasService dcasService;
    @Autowired private EmailService emailService;
    @Autowired private EncryptionService encryptionService;

    @GetMapping("/transfer")
    public String transferPage(Authentication auth, Model model) {
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();
        if (user.isAccountLocked()) return "locked"; // Basit kontrol
        model.addAttribute("balance", user.getBalance());
        return "transfer";
    }

    @PostMapping("/init-transfer")
    public String initTransfer(@RequestParam String receiverAccount, @RequestParam String receiverName,
                               @RequestParam BigDecimal amount, @RequestParam String description,
                               Authentication auth, HttpSession session, Model model) {
        User sender = userRepository.findByUsername(auth.getName()).orElseThrow();
        User receiver = userRepository.findByAccountNumber(receiverAccount).orElse(null);

        if (receiver == null || !receiver.getFullName().equalsIgnoreCase(receiverName)) {
            model.addAttribute("error", "Account/Name mismatch or not found.");
            model.addAttribute("balance", sender.getBalance());
            return "transfer";
        }
        if (sender.getBalance().compareTo(amount) < 0) {
            model.addAttribute("error", "Insufficient balance.");
            model.addAttribute("balance", sender.getBalance());
            return "transfer";
        }

        // Limit ve Ayar Kontrolü
        if (!sender.isTransferSecurityEnabled() || amount.compareTo(sender.getDcasTransactionLimit()) < 0) {
            return performTransfer(sender, receiverAccount, amount, description);
        }

        // DCAS Başlat
        DcasService.ChallengeRule rule = dcasService.generateChallengeRule(sender);
        session.setAttribute("transfer_rule", rule);
        session.setAttribute("challenge_start_time", LocalDateTime.now());
        session.setAttribute("tx_receiver_account", receiverAccount);
        session.setAttribute("tx_amount", amount);
        session.setAttribute("tx_description", description);

        String apw = encryptionService.decrypt(sender.getApwEncrypted());
        emailService.sendDcasChallenge(sender.getEmail(), sender.getFullName(), apw, rule.instruction);

        model.addAttribute("info", "Instruction sent to EMAIL.");
        return "verify";
    }

    @PostMapping("/verify-transfer")
    public String verifyTransfer(@RequestParam String userResponse, Authentication auth, HttpSession session, Model model) {
        User sender = userRepository.findByUsername(auth.getName()).orElseThrow();

        LocalDateTime startTime = (LocalDateTime) session.getAttribute("challenge_start_time");
        if (startTime == null || ChronoUnit.MINUTES.between(startTime, LocalDateTime.now()) > 3) {
            model.addAttribute("error", "Time expired!");
            model.addAttribute("timeout", true);
            return "verify_retry";
        }

        DcasService.ChallengeRule rule = (DcasService.ChallengeRule) session.getAttribute("transfer_rule");
        if (rule == null) return "redirect:/transfer";

        if (dcasService.verifyChallenge(sender, rule, userResponse)) {
            String acc = (String) session.getAttribute("tx_receiver_account");
            BigDecimal amt = (BigDecimal) session.getAttribute("tx_amount");
            String desc = (String) session.getAttribute("tx_description");
            session.removeAttribute("transfer_rule");
            session.removeAttribute("tx_description");
            return performTransfer(sender, acc, amt, desc);
        } else {
            // BAŞARISIZ - RETRY MANTIĞI
            int attempts = sender.getFailedAttempts() + 1;
            sender.setFailedAttempts(attempts);

            if (attempts >= 3) {
                // KİLİTLEME İŞLEMİ
                sender.setAccountLocked(true);
                sender.setLockTime(LocalDateTime.now());

                // Unlock Token Üret
                String token = UUID.randomUUID().toString();
                sender.setUnlockToken(token);
                userRepository.save(sender);

                // Kilit Maili Gönder (Link ile)
                String apw = encryptionService.decrypt(sender.getApwEncrypted());
                String unlockLink = "https://localhost:8443/unlock-account?token=" + token;
                emailService.sendLockNotification(sender.getEmail(), sender.getFullName(), apw, unlockLink);

                return "locked";
            }

            userRepository.save(sender);

            // KULLANICI AYNI SAYFADA KALSIN (RETRY)
            model.addAttribute("error", "Incorrect code! You have " + (3 - attempts) + " attempts left.");
            model.addAttribute("info", "Please check your email again for the instruction.");
            return "verify"; // verify.html'e geri dön, yeni challenge üretme
        }
    }

    @PostMapping("/resend-transfer-code")
    public String resendTransferCode(Authentication auth, HttpSession session) {
        User sender = userRepository.findByUsername(auth.getName()).orElseThrow();
        DcasService.ChallengeRule rule = dcasService.generateChallengeRule(sender);
        session.setAttribute("transfer_rule", rule);
        session.setAttribute("challenge_start_time", LocalDateTime.now());

        String apw = encryptionService.decrypt(sender.getApwEncrypted());
        emailService.sendDcasChallenge(sender.getEmail(), sender.getFullName(), apw, rule.instruction);
        return "redirect:/verify-page-redirect";
    }

    // verify.html sayfasına GET ile dönmek için yardımcı bir endpoint
    @GetMapping("/verify-page-redirect")
    public String verifyPageRedirect(Model model) {
        model.addAttribute("info", "New code sent to EMAIL.");
        return "verify";
    }

    private String performTransfer(User sender, String receiverAccount, BigDecimal amount, String description) {
        User receiver = userRepository.findByAccountNumber(receiverAccount).orElseThrow();
        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));
        sender.setFailedAttempts(0);
        userRepository.save(sender);
        userRepository.save(receiver);

        Transaction tx = new Transaction();
        tx.setSenderUsername(sender.getFullName());
        tx.setReceiverUsername(receiver.getFullName());
        tx.setAmount(amount);
        tx.setTimestamp(LocalDateTime.now());
        tx.setStatus("SUCCESS");
        tx.setDescription(description);
        transactionRepository.save(tx);

        String apw = encryptionService.decrypt(sender.getApwEncrypted());
        emailService.sendSecurityAlert(sender.getEmail(), sender.getFullName(), "Transfer Success", apw, "Sent $" + amount + " to " + receiver.getFullName());
        return "redirect:/success";
    }

    @GetMapping("/success") public String success() { return "success"; }
}