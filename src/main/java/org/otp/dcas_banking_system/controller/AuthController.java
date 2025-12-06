package org.otp.dcas_banking_system.controller;

import org.otp.dcas_banking_system.model.User;
import org.otp.dcas_banking_system.repository.UserRepository;
import org.otp.dcas_banking_system.service.DcasService;
import org.otp.dcas_banking_system.service.EncryptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {

    @Autowired private UserRepository userRepository;
    @Autowired private EncryptionService encryptionService;
    @Autowired private DcasService dcasService;
    @Autowired private PasswordEncoder passwordEncoder;

    @GetMapping("/login")
    public String showLogin() { return "login"; }

    @GetMapping("/register")
    public String showRegister() { return "register"; }

    @PostMapping("/register")
    public String registerUser(@RequestParam String username,
                               @RequestParam String password,
                               @RequestParam String tsw, // Transaction Security Word
                               Model model) {

        if (userRepository.findByUsername(username).isPresent()) {
            model.addAttribute("error", "Bu kullanıcı adı zaten alınmış.");
            return "register";
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));

        // TSW'yi şifrele
        user.setTswEncrypted(encryptionService.encrypt(tsw));

        // TOTP Secret üret ve şifrele
        String secret = dcasService.generateSecretKey();
        user.setTotpSecretEncrypted(encryptionService.encrypt(secret));

        userRepository.save(user);

// DÜZELTİLEN KISIM: URLEncoder kullanarak güvenli URL oluşturma
        String otpAuthUrl = "otpauth://totp/DCAS_Bank:" + username + "?secret=" + secret + "&issuer=DCAS_Bank";
// Google Charts API'sine gönderirken URL'yi encode etmemiz gerekmez, ancak parametreleri düzgün ekleyelim.
        String qrUrl = "https://chart.googleapis.com/chart?chs=200x200&chld=M|0&cht=qr&chl=" + otpAuthUrl;

        model.addAttribute("qrUrl", qrUrl);
        model.addAttribute("secret", secret);

        return "register_success";
    }
}
