package org.otp.dcas_banking_system.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender javaMailSender;

    // application.properties'deki mail adresini alır
    @Value("${spring.mail.username}")
    private String senderEmail;

    public void sendSecurityAlert(String toEmail, String subject, String antiPhishingWord, String messageBody) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();

            message.setFrom(senderEmail);
            message.setTo(toEmail);
            message.setSubject("DCAS Bank Security Alert: " + subject);

            // Mail İçeriği
            String fullMessage = """
                    Dear Customer,
                    
                    This is a security notification from DCAS Bank.
                    
                    [ SECURITY VERIFICATION: %s ]
                    (If this word does not match your chosen Anti-Phishing Word, please ignore this email.)
                    
                    ------------------------------------------------------
                    %s
                    ------------------------------------------------------
                    
                    If you did not perform this action, please contact us immediately.
                    
                    Securely yours,
                    DCAS Banking Team
                    """.formatted(antiPhishingWord, messageBody);

            message.setText(fullMessage);

            javaMailSender.send(message);
            System.out.println("✅ Real Email Sent to: " + toEmail);

        } catch (Exception e) {
            System.err.println("❌ Failed to send email: " + e.getMessage());
            // Gerçek hayatta burayı loglamalıyız ama akışı bozmamak için hatayı yutabiliriz
        }
    }
}