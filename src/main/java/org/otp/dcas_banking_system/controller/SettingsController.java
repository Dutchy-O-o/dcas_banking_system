package org.otp.dcas_banking_system.controller;

import org.otp.dcas_banking_system.model.User;
import org.otp.dcas_banking_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.math.BigDecimal;

@Controller
public class SettingsController {

    @Autowired private UserRepository userRepository;

    @GetMapping("/settings")
    public String showSettings(Authentication auth, Model model) {
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();

        // Mevcut ayarları ön yüze gönder
        model.addAttribute("currentLimit", user.getDcasTransactionLimit());
        model.addAttribute("loginEnabled", user.isLoginSecurityEnabled());
        model.addAttribute("transferEnabled", user.isTransferSecurityEnabled());

        return "settings";
    }

    @PostMapping("/settings/update")
    public String updateSettings(
            @RequestParam BigDecimal limit,
            // Checkbox işaretli değilse null gelir, bunu false yapmak için defaultValue kullanıyoruz
            @RequestParam(defaultValue = "false") boolean loginEnabled,
            @RequestParam(defaultValue = "false") boolean transferEnabled,
            Authentication auth, Model model) {

        User user = userRepository.findByUsername(auth.getName()).orElseThrow();

        if (limit.compareTo(BigDecimal.ZERO) < 0) limit = BigDecimal.ZERO;

        // Ayarları güncelle
        user.setDcasTransactionLimit(limit);
        user.setLoginSecurityEnabled(loginEnabled);
        user.setTransferSecurityEnabled(transferEnabled);

        userRepository.save(user);

        model.addAttribute("success", "Security preferences updated successfully!");

        // Güncel değerleri tekrar sayfaya bas
        model.addAttribute("currentLimit", limit);
        model.addAttribute("loginEnabled", loginEnabled);
        model.addAttribute("transferEnabled", transferEnabled);

        return "settings";
    }
}