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

    public static class DcasChallenge {
        public String instruction;
        public String expectedResponse;
    }

    public DcasChallenge generateChallenge(User user) {
        String tsw = encryptionService.decrypt(user.getTswEncrypted());
        String totpSecret = encryptionService.decrypt(user.getTotpSecretEncrypted());

        int code = gAuth.getTotpPassword(totpSecret);
        String totp = String.format("%06d", code);

        int tswLen = random.nextInt(3) + 2;
        int totpLen = 6 - tswLen;

        boolean tswStart = random.nextBoolean();
        boolean totpStart = random.nextBoolean();
        boolean tswFirst = random.nextBoolean();

        String tswPart = tswStart ? tsw.substring(0, tswLen) : tsw.substring(tsw.length() - tswLen);
        String totpPart = totpStart ? totp.substring(0, totpLen) : totp.substring(6 - totpLen);

        String tswText = tswStart ? "FIRST " + tswLen + " chars of TSW" : "LAST " + tswLen + " chars of TSW";
        String totpText = totpStart ? "FIRST " + totpLen + " digits of TOTP" : "LAST " + totpLen + " digits of TOTP";

        DcasChallenge challenge = new DcasChallenge();
        if (tswFirst) {
            challenge.instruction = tswText + " AND THEN " + totpText;
            challenge.expectedResponse = tswPart + totpPart;
        } else {
            challenge.instruction = totpText + " AND THEN " + tswText;
            challenge.expectedResponse = totpPart + tswPart;
        }
        return challenge;
    }

    public String generateSecretKey() { return gAuth.createCredentials().getKey(); }
}