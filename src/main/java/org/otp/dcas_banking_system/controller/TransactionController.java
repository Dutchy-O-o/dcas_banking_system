package org.otp.dcas_banking_system.controller;

import org.otp.dcas_banking_system.model.User;
import org.otp.dcas_banking_system.repository.UserRepository;
import org.otp.dcas_banking_system.service.DcasService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class TransactionController {

    @Autowired private UserRepository userRepository;
    @Autowired private DcasService dcasService;

    @GetMapping("/transfer")
    public String transferPage(Authentication auth, Model model) {
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();
        if (user.isAccountLocked()) return "locked";
        return "transfer";
    }

    @PostMapping("/init-transfer")
    public String initTransfer(Authentication auth, HttpSession session, Model model) {
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();

        if (user.isAccountLocked()) return "locked";

        // DCAS: Dinamik Meydan Okuma Üret
        DcasService.DcasChallenge challenge = dcasService.generateChallenge(user);

        // Beklenen cevabı session'da sakla (Stateful)
        session.setAttribute("expectedResponse", challenge.expectedResponse);

        model.addAttribute("instruction", challenge.instruction);
        return "verify";
    }

    @PostMapping("/verify-transfer")
    public String verifyTransfer(@RequestParam String userResponse, Authentication auth, HttpSession session, Model model) {
        String expected = (String) session.getAttribute("expectedResponse");
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();

        if (expected != null && expected.equals(userResponse)) {
            // BAŞARILI
            user.setFailedAttempts(0);
            userRepository.save(user);
            return "success";
        } else {
            // BAŞARISIZ
            int attempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(attempts);
            if (attempts >= 3) user.setAccountLocked(true);
            userRepository.save(user);

            if (user.isAccountLocked()) return "locked";

            model.addAttribute("error", "Yanlış giriş! Kalan hak: " + (3 - attempts));
            return "verify_retry";
        }
    }
}
