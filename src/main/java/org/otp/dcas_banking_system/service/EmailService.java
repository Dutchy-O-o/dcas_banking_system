package org.otp.dcas_banking_system.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    // Gönderici Adı (Mail kutusunda bu isim görünecek)
    private static final String SENDER_NAME = "DCAS Bank Security Team";

    // 1. Genel Bildirim
    public void sendSecurityAlert(String toEmail, String toName, String subject, String apw, String body) {
        sendEmail(toEmail, toName, subject, apw, body);
    }

    // 2. DCAS Challenge
    public void sendDcasChallenge(String toEmail, String toName, String apw, String instruction) {
        String body = "You initiated a secure transaction.\n\n" +
                "⚠️ INSTRUCTION TO VERIFY: \n" +
                "👉 " + instruction + " 👈\n\n" +
                "Please enter the combined code on the banking screen.";
        sendEmail(toEmail, toName, "Action Required: Complete Your Transaction", apw, body);
    }

    // 3. Kilitlenme
    public void sendLockNotification(String toEmail, String toName, String apw, String unlockLink) {
        String body = "Your account has been LOCKED due to 3 consecutive failed attempts.\n\n" +
                "Click the link below to unlock immediately:\n" +
                unlockLink + "\n\n" +
                "If this wasn't you, please contact support immediately.";
        sendEmail(toEmail, toName, "SECURITY ALERT: Account Locked", apw, body);
    }

    // Ortak Gönderim Metodu (MimeMessage ile)
    private void sendEmail(String to, String toName, String subject, String apw, String body) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // Gönderen: "DCAS Bank Security Team <mail@gmail.com>"
            helper.setFrom(senderEmail, SENDER_NAME);

            // Alıcı: "Ahmet Yılmaz <ahmet@gmail.com>"
            helper.setTo(new jakarta.mail.internet.InternetAddress(to, toName));

            helper.setSubject("DCAS Bank: " + subject);

            String fullMessage = """
                    Dear %s,
                    
                    [ SECURITY WORD: %s ]
                    
                    %s
                    
                    ------------------------------------------------------
                    DCAS Bank Security Team
                    """.formatted(toName, apw, body);

            helper.setText(fullMessage); // true parametresi eklenirse HTML gönderilebilir

            javaMailSender.send(message);
            System.out.println("✅ Email Sent to: " + toName);

        } catch (Exception e) {
            System.err.println("❌ Mail Error: " + e.getMessage());
        }
    }

    // 4. Hoş Geldin ve Güvenlik Bilgilendirmesi (YENİ)
    public void sendWelcomeEmail(String toEmail, String toName, String apw) {
        String body = """
                Welcome to the DCAS Banking System!
                
                Your account has been successfully created.
                
                🔒 WHAT IS THE "SECURITY WORD" ABOVE?
                As you can see at the top of this email, we have included the Anti-Phishing Word you chose during registration.
                
                ⚠️ CRITICAL SECURITY RULE:
                From now on, EVERY official email from DCAS Bank will contain this specific word.
                
                1. Always check for this word before reading any email from us.
                2. If an email claims to be from DCAS Bank but DOES NOT contain this word, it is a SCAM (Phishing Attack).
                3. Never share your passwords or OTP codes with anyone, even bank staff.
                
                Thank you for banking securely with us.
                """;

        sendEmail(toEmail, toName, "Welcome - Important Security Information", apw, body);
    }
}