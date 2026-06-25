package org.otp.dcas_banking_system.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.otp.dcas_banking_system.dto.TransferRequest;
import org.otp.dcas_banking_system.exception.InsufficientBalanceException;
import org.otp.dcas_banking_system.exception.TransferValidationException;
import org.otp.dcas_banking_system.model.User;
import org.otp.dcas_banking_system.service.AuthService;
import org.otp.dcas_banking_system.service.DcasService;
import org.otp.dcas_banking_system.service.EmailService;
import org.otp.dcas_banking_system.service.EncryptionService;
import org.otp.dcas_banking_system.service.TransferService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Controller
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;
    private final AuthService authService;
    private final DcasService dcasService;
    private final EmailService emailService;
    private final EncryptionService encryptionService;

    @GetMapping("/transfer")
    public String transferPage(Authentication auth, Model model) {
        User user = transferService.loadSender(auth.getName());
        if (user.isAccountLocked()) return "locked";
        model.addAttribute("balance", user.getBalance());
        return "transfer";
    }

    @PostMapping("/init-transfer")
    public String initTransfer(@Valid @ModelAttribute TransferRequest req,
                               BindingResult bindingResult,
                               Authentication auth, HttpSession session, Model model) {
        User sender = transferService.loadSender(auth.getName());

        if (bindingResult.hasErrors()) {
            model.addAttribute("error", bindingResult.getAllErrors().get(0).getDefaultMessage());
            model.addAttribute("balance", sender.getBalance());
            return "transfer";
        }

        try {
            transferService.validateReceiver(req.receiverAccount(), req.receiverName());
            transferService.checkBalance(sender, req.amount());
        } catch (TransferValidationException | InsufficientBalanceException ex) {
            log.warn("Transfer pre-check failed for {}: {}", auth.getName(), ex.getMessage());
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("balance", sender.getBalance());
            return "transfer";
        }

        if (!transferService.requiresChallenge(sender, req.amount())) {
            transferService.executeTransfer(auth.getName(), req.receiverAccount(),
                    req.amount(), req.description());
            return "redirect:/success";
        }

        DcasService.ChallengeRule rule = dcasService.generateChallengeRule(sender);
        session.setAttribute("transfer_rule", rule);
        session.setAttribute("challenge_start_time", LocalDateTime.now());
        session.setAttribute("tx_receiver_account", req.receiverAccount());
        session.setAttribute("tx_amount", req.amount());
        session.setAttribute("tx_description", req.description());

        String apw = encryptionService.decrypt(sender.getApwEncrypted());
        emailService.sendDcasChallenge(sender.getEmail(), sender.getFullName(), apw, rule.instruction);

        model.addAttribute("info", "Instruction sent to EMAIL.");
        return "verify";
    }

    @PostMapping("/verify-transfer")
    public String verifyTransfer(@RequestParam String userResponse,
                                 Authentication auth, HttpSession session, Model model) {
        User sender = transferService.loadSender(auth.getName());

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
            transferService.executeTransfer(auth.getName(), acc, amt, desc);
            return "redirect:/success";
        }

        boolean locked = authService.registerFailedAttempt(sender);
        if (locked) return "locked";

        model.addAttribute("error",
                "Incorrect code! You have " + (3 - sender.getFailedAttempts()) + " attempts left.");
        model.addAttribute("info", "Please check your email again for the instruction.");
        return "verify";
    }

    @PostMapping("/resend-transfer-code")
    public String resendTransferCode(Authentication auth, HttpSession session) {
        User sender = transferService.loadSender(auth.getName());
        DcasService.ChallengeRule rule = dcasService.generateChallengeRule(sender);
        session.setAttribute("transfer_rule", rule);
        session.setAttribute("challenge_start_time", LocalDateTime.now());

        String apw = encryptionService.decrypt(sender.getApwEncrypted());
        emailService.sendDcasChallenge(sender.getEmail(), sender.getFullName(), apw, rule.instruction);
        return "redirect:/verify-page-redirect";
    }

    @GetMapping("/verify-page-redirect")
    public String verifyPageRedirect(Model model) {
        model.addAttribute("info", "New code sent to EMAIL.");
        return "verify";
    }

    @GetMapping("/success")
    public String success() {
        return "success";
    }
}
