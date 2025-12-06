package org.otp.dcas_banking_system.controller;

import org.otp.dcas_banking_system.model.User;
import org.otp.dcas_banking_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/home")
    public String dashboard(Authentication auth, Model model) {
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();

        // Ekrana basılacak bilgiler
        model.addAttribute("username", user.getUsername());

        // Gerçek bir Transaction tablosu yapmadığımız için bakiyeyi şimdilik statik gösteriyoruz
        // İleride User tablosuna 'balance' alanı ekleyip oradan çekebilirsin.
        model.addAttribute("balance", "24.500,00");

        return "home";
    }

    // Kök dizine gelen isteği de ana sayfaya yönlendirelim (Giriş yapmışsa)
    @GetMapping("/")
    public String root() {
        return "redirect:/home";
    }
}
