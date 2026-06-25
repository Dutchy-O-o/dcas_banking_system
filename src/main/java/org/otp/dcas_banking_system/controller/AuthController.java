package org.otp.dcas_banking_system.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.otp.dcas_banking_system.dto.RegisterRequest;
import org.otp.dcas_banking_system.model.User;
import org.otp.dcas_banking_system.service.AuthService;
import org.otp.dcas_banking_system.service.DcasService;
import org.otp.dcas_banking_system.service.EmailService;
import org.otp.dcas_banking_system.service.EncryptionService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final DcasService dcasService;
    private final EmailService emailService;
    private final EncryptionService encryptionService;

    @GetMapping("/login")
    public String showLogin() {
        return "login";
    }

    @GetMapping("/register")
    public String showRegister() {
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@Valid @ModelAttribute RegisterRequest req,
                               BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("error", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "register";
        }

        if (authService.isEmailRegistered(req.email())) {
            model.addAttribute("error", "Email already registered.");
            return "register";
        }

        AuthService.RegisterResult result = authService.register(
                req.fullName(), req.email(), req.password(), req.tsw(), req.apw());

        model.addAttribute("otpAuthUrl", result.otpAuthUrl());
        model.addAttribute("secret", result.secret());
        return "register_success";
    }

    @GetMapping("/login-check")
    public String loginCheck(Authentication auth, HttpSession session) {
        User user = authService.loadUser(auth.getName());

        if (user.isAccountLocked() && !authService.unlockIfExpired(user)) {
            return "redirect:/locked-page";
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

    @GetMapping("/verify-login")
    public String showVerifyLogin() {
        return "verify_login";
    }

    @PostMapping("/verify-login-process")
    public String verifyLoginProcess(@RequestParam String userResponse,
                                     Authentication auth, HttpSession session, Model model) {
        User user = authService.loadUser(auth.getName());

        LocalDateTime startTime = (LocalDateTime) session.getAttribute("challenge_start_time");
        if (startTime != null && ChronoUnit.MINUTES.between(startTime, LocalDateTime.now()) > 3) {
            model.addAttribute("error", "Time expired!");
            model.addAttribute("timeout", true);
            return "verify_login";
        }

        DcasService.ChallengeRule rule = (DcasService.ChallengeRule) session.getAttribute("login_rule");
        if (dcasService.verifyChallenge(user, rule, userResponse)) {
            session.removeAttribute("login_rule");
            authService.resetFailedAttempts(user);
            return "redirect:/dashboard";
        }

        boolean locked = authService.registerFailedAttempt(user);
        if (locked) return "redirect:/locked-page";

        model.addAttribute("error",
                "Incorrect code! Attempts left: " + (3 - user.getFailedAttempts()));
        return "verify_login";
    }

    @PostMapping("/resend-login-code")
    public String resendLoginCode() {
        return "redirect:/login-check";
    }

    @GetMapping("/unlock-account")
    public String unlockAccount(@RequestParam String token, Model model) {
        if (authService.unlockByToken(token)) {
            model.addAttribute("success", "Account UNLOCKED.");
        } else {
            model.addAttribute("error", "Invalid link.");
        }
        return "login";
    }

    @GetMapping("/locked-page")
    public String lockedPage() {
        return "locked";
    }
}
