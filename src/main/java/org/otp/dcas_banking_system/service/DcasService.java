package org.otp.dcas_banking_system.service;

import org.otp.dcas_banking_system.model.User;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Random;

@Service
public class DcasService {

    @Autowired private EncryptionService encryptionService;
    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();
    private final Random random = new Random();

    // Session'da saklanacak KURAL (Cevap değil!)
    public static class ChallengeRule {
        public String instruction;
        public int tswLen;
        public boolean tswStart;
        public int totpLen;
        public boolean totpStart;
        public boolean tswFirst;
    }

    public ChallengeRule generateChallengeRule(User user) {
        ChallengeRule rule = new ChallengeRule();

        rule.tswLen = random.nextInt(3) + 2; // 2, 3 veya 4
        rule.totpLen = 6 - rule.tswLen;

        rule.tswStart = random.nextBoolean();
        rule.totpStart = random.nextBoolean();
        rule.tswFirst = random.nextBoolean();

        String tswText = rule.tswStart ? "FIRST " + rule.tswLen + " chars of TSW" : "LAST " + rule.tswLen + " chars of TSW";
        String totpText = rule.totpStart ? "FIRST " + rule.totpLen + " digits of TOTP" : "LAST " + rule.totpLen + " digits of TOTP";

        if (rule.tswFirst) {
            rule.instruction = tswText + " AND THEN " + totpText;
        } else {
            rule.instruction = totpText + " AND THEN " + tswText;
        }
        return rule;
    }

    // Doğrulama: Şu an, 30sn öncesi ve 30sn sonrası kontrol edilir
    public boolean verifyChallenge(User user, ChallengeRule rule, String userResponse) {
        if (rule == null || userResponse == null) return false;

        String tsw = encryptionService.decrypt(user.getTswEncrypted());
        String totpSecret = encryptionService.decrypt(user.getTotpSecretEncrypted());

        String tswPart = rule.tswStart ? tsw.substring(0, rule.tswLen) : tsw.substring(tsw.length() - rule.tswLen);

        long currentTime = System.currentTimeMillis() / 30000;
        long[] windows = { currentTime, currentTime - 1, currentTime + 1 };

        for (long timeWindow : windows) {
            int code = gAuth.getTotpPassword(totpSecret, timeWindow * 30000);
            String totp = String.format("%06d", code);

            String totpPart = rule.totpStart ? totp.substring(0, rule.totpLen) : totp.substring(6 - rule.totpLen);
            String expected = rule.tswFirst ? tswPart + totpPart : totpPart + tswPart;

            if (expected.equals(userResponse)) return true;
        }
        return false;
    }

    public String generateSecretKey() { return gAuth.createCredentials().getKey(); }
}