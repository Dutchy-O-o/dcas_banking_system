package org.otp.dcas_banking_system.controller;

import org.otp.dcas_banking_system.model.User;
import org.otp.dcas_banking_system.repository.UserRepository;
import org.otp.dcas_banking_system.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.UUID;

@Controller
public class AuthController {

    @Autowired private UserRepository userRepository;
    @Autowired private EncryptionService encryptionService;
    @Autowired private DcasService dcasService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EmailService emailService;

    @GetMapping("/login")
    public String showLogin() { return "login"; }
    @GetMapping("/register")
    public String showRegister() { return "register"; }

    @PostMapping("/register")
    public String registerUser(@RequestParam String fullName, @RequestParam String email,
                               @RequestParam String password, @RequestParam String tsw,
                               @RequestParam String apw, Model model) {
        if (userRepository.existsByUsername(email)) {
            model.addAttribute("error", "Email already registered.");
            return "register";
        }

        User user = new User();
        user.setFullName(fullName);
        user.setUsername(email);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setAccountNumber("TR" + (new Random().nextLong(899999999999999999L) + 100000000000000000L));
        user.setTswEncrypted(encryptionService.encrypt(tsw));
        user.setApwEncrypted(encryptionService.encrypt(apw));

        String secret = dcasService.generateSecretKey();
        user.setTotpSecretEncrypted(encryptionService.encrypt(secret));

        userRepository.save(user);

        emailService.sendWelcomeEmail(email, fullName, apw);

        String otpAuthUrl = String.format("otpauth://totp/DCAS_Bank:%s?secret=%s&issuer=DCAS_Bank",email, secret);
        model.addAttribute("otpAuthUrl", otpAuthUrl);
        model.addAttribute("secret", secret);

        return "register_success";
    }

    @GetMapping("/login-check")
    public String loginCheck(Authentication auth, HttpSession session) {
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();

        if (user.isAccountLocked()) {
            if (user.getLockTime() != null && ChronoUnit.MINUTES.between(user.getLockTime(), LocalDateTime.now()) >= 5) {
                user.setAccountLocked(false);
                user.setFailedAttempts(0);
                userRepository.save(user);
            } else {
                return "redirect:/locked-page";
            }
        }

        if (user.isLoginSecurityEnabled()) {
            DcasService.ChallengeRule rule = dcasService.generateChallengeRule(user);
            session.setAttribute("login_rule", rule);
            session.setAttribute("challenge_start_time", LocalDateTime.now());

            String apw = encryptionService.decrypt(user.getApwEncrypted());
            emailService.sendDcasChallenge(user.getEmail(), user.getFullName(), apw, rule.instruction);
            return "redirect:/verify-login";
        }
        return "redirect:/dashboard";
    }

    @GetMapping("/verify-login") public String showVerifyLogin() { return "verify_login"; }

    @PostMapping("/verify-login-process")
    public String verifyLoginProcess(@RequestParam String userResponse, Authentication auth, HttpSession session, Model model) {
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();

        LocalDateTime startTime = (LocalDateTime) session.getAttribute("challenge_start_time");
        if (startTime != null && ChronoUnit.MINUTES.between(startTime, LocalDateTime.now()) > 3) {
            model.addAttribute("error", "Time expired!");
            model.addAttribute("timeout", true);
            return "verify_login";
        }

        DcasService.ChallengeRule rule = (DcasService.ChallengeRule) session.getAttribute("login_rule");
        if (dcasService.verifyChallenge(user, rule, userResponse)) {
            session.removeAttribute("login_rule");
            user.setFailedAttempts(0);
            userRepository.save(user);
            return "redirect:/dashboard";
        } else {
            handleFailure(user);
            if (user.isAccountLocked()) return "redirect:/locked-page";
            model.addAttribute("error", "Incorrect code! Attempts left: " + (3 - user.getFailedAttempts()));
            return "verify_login";
        }
    }

    @PostMapping("/resend-login-code")
    public String resendLoginCode() { return "redirect:/login-check"; }

    @GetMapping("/unlock-account")
    public String unlockAccount(@RequestParam String token, Model model) {
        User user = userRepository.findAll().stream().filter(u -> token.equals(u.getUnlockToken())).findFirst().orElse(null);
        if (user != null) {
            user.setAccountLocked(false);
            user.setFailedAttempts(0);
            user.setUnlockToken(null);
            userRepository.save(user);
            model.addAttribute("success", "Account UNLOCKED.");
            return "login";
        }
        model.addAttribute("error", "Invalid link.");
        return "login";
    }

    @GetMapping("/locked-page") public String lockedPage() { return "locked"; }

    private void handleFailure(User user) {
        int attempts = user.getFailedAttempts() + 1;
        user.setFailedAttempts(attempts);
        if (attempts >= 3) {
            user.setAccountLocked(true);
            user.setLockTime(LocalDateTime.now());
            String token = UUID.randomUUID().toString();
            user.setUnlockToken(token);
            String apw = encryptionService.decrypt(user.getApwEncrypted());
            emailService.sendLockNotification(user.getEmail(), user.getFullName(), apw, "https://localhost:8443/unlock-account?token="+token);
        }
        userRepository.save(user);
    }
}