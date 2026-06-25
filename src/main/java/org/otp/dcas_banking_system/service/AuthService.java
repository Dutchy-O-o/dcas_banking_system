package org.otp.dcas_banking_system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.otp.dcas_banking_system.exception.UserNotFoundException;
import org.otp.dcas_banking_system.model.User;
import org.otp.dcas_banking_system.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final int LOCK_DURATION_MINUTES = 5;

    private final UserRepository userRepository;
    private final EncryptionService encryptionService;
    private final DcasService dcasService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public boolean isEmailRegistered(String email) {
        return userRepository.existsByUsername(email);
    }

    @Transactional
    public RegisterResult register(String fullName, String email, String password,
                                   String tsw, String apw) {
        log.info("Registering new user: {}", email);

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

        try {
            emailService.sendWelcomeEmail(email, fullName, apw);
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}", email, e);
        }

        String otpAuthUrl = String.format(
                "otpauth://totp/DCAS_Bank:%s?secret=%s&issuer=DCAS_Bank", email, secret);
        return new RegisterResult(otpAuthUrl, secret);
    }

    public User loadUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
    }

    @Transactional
    public boolean unlockIfExpired(User user) {
        if (!user.isAccountLocked()) return false;
        if (user.getLockTime() != null
                && ChronoUnit.MINUTES.between(user.getLockTime(), LocalDateTime.now())
                   >= LOCK_DURATION_MINUTES) {
            user.setAccountLocked(false);
            user.setFailedAttempts(0);
            userRepository.save(user);
            log.info("Auto-unlocked account after timeout: {}", user.getUsername());
            return true;
        }
        return false;
    }

    @Transactional
    public void resetFailedAttempts(User user) {
        user.setFailedAttempts(0);
        userRepository.save(user);
    }

    @Transactional
    public boolean registerFailedAttempt(User user) {
        int attempts = user.getFailedAttempts() + 1;
        user.setFailedAttempts(attempts);
        boolean locked = false;
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setAccountLocked(true);
            user.setLockTime(LocalDateTime.now());
            String token = UUID.randomUUID().toString();
            user.setUnlockToken(token);
            locked = true;

            try {
                String apw = encryptionService.decrypt(user.getApwEncrypted());
                String unlockLink = "https://localhost:8443/unlock-account?token=" + token;
                emailService.sendLockNotification(user.getEmail(), user.getFullName(), apw, unlockLink);
            } catch (Exception e) {
                log.error("Failed to send lock notification to {}", user.getUsername(), e);
            }
            log.warn("Account locked due to {} failed attempts: {}", attempts, user.getUsername());
        }
        userRepository.save(user);
        return locked;
    }

    @Transactional
    public boolean unlockByToken(String token) {
        User user = userRepository.findAll().stream()
                .filter(u -> token.equals(u.getUnlockToken()))
                .findFirst()
                .orElse(null);
        if (user == null) {
            log.warn("Invalid unlock token attempted");
            return false;
        }
        user.setAccountLocked(false);
        user.setFailedAttempts(0);
        user.setUnlockToken(null);
        userRepository.save(user);
        log.info("Account unlocked via token: {}", user.getUsername());
        return true;
    }

    public record RegisterResult(String otpAuthUrl, String secret) {}
}
