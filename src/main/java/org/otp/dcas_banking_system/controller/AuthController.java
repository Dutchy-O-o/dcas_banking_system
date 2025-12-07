package org.otp.dcas_banking_system.controller;

import org.otp.dcas_banking_system.model.User;
import org.otp.dcas_banking_system.repository.UserRepository;
import org.otp.dcas_banking_system.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Random;

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
    public String registerUser(@RequestParam String username,
                               @RequestParam String email,
                               @RequestParam String password,
                               @RequestParam String tsw,
                               @RequestParam String apw, // Anti-Phishing Word
                               Model model) {

        if (userRepository.existsByUsername(username)) {
            model.addAttribute("error", "Username already exists.");
            return "register";
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));

        // Generate Fake Account Number
        user.setAccountNumber("TR" + (new Random().nextLong(9000000000L) + 1000000000L));

        // Encrypt secrets
        user.setTswEncrypted(encryptionService.encrypt(tsw));
        user.setApwEncrypted(encryptionService.encrypt(apw));

        String secret = dcasService.generateSecretKey();
        user.setTotpSecretEncrypted(encryptionService.encrypt(secret));

        userRepository.save(user);

        // QR Fix
        String encodedIssuer = URLEncoder.encode("DCAS Bank", StandardCharsets.UTF_8);
        String encodedUser = URLEncoder.encode(username, StandardCharsets.UTF_8);
        String otpAuthUrl = "otpauth://totp/" + encodedIssuer + ":" + encodedUser + "?secret=" + secret + "&issuer=" + encodedIssuer;
        String qrUrl = "https://chart.googleapis.com/chart?chs=200x200&chld=M|0&cht=qr&chl=" + URLEncoder.encode(otpAuthUrl, StandardCharsets.UTF_8);

        model.addAttribute("qrUrl", qrUrl);
        model.addAttribute("secret", secret);

        return "register_success";
    }
}