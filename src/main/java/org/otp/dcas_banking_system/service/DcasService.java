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
        public String instruction; // Ekranda yazacak talimat
        public String expectedResponse; // Sunucunun hesapladığı doğru cevap
    }

    public DcasChallenge generateChallenge(User user) {
        // 1. Verileri Çöz
        String tsw = encryptionService.decrypt(user.getTswEncrypted());
        String totpSecret = encryptionService.decrypt(user.getTotpSecretEncrypted());

        // 2. O anki geçerli TOTP kodunu Hesapla
        int code = gAuth.getTotpPassword(totpSecret);
        String totp = String.format("%06d", code); // Örn: 012345

        // 3. Rastgele Kural Belirle (Toplam 6 hane olacak)
        int tswLen = random.nextInt(3) + 2; // TSW'den 2, 3 veya 4 hane al
        int totpLen = 6 - tswLen;

        // Baştan mı sondan mı alınacak?
        boolean tswStart = random.nextBoolean();
        boolean totpStart = random.nextBoolean();

        // Sıralama: Önce TSW mi yoksa TOTP mi istenecek?
        boolean tswFirst = random.nextBoolean();

        // Parçaları kesip al
        String tswPart = tswStart ? tsw.substring(0, tswLen) : tsw.substring(tsw.length() - tswLen);
        String totpPart = totpStart ? totp.substring(0, totpLen) : totp.substring(6 - totpLen);

        // 4. Talimat Metnini ve Cevabı Oluştur
        String tswText = tswStart ? "TSW'nizin İLK " + tswLen + " harfi" : "TSW'nizin SON " + tswLen + " harfi";
        String totpText = totpStart ? "TOTP kodunun İLK " + totpLen + " hanesi" : "TOTP kodunun SON " + totpLen + " hanesi";

        DcasChallenge challenge = new DcasChallenge();
        if (tswFirst) {
            challenge.instruction = tswText + " ve ardından " + totpText;
            challenge.expectedResponse = tswPart + totpPart;
        } else {
            challenge.instruction = totpText + " ve ardından " + tswText;
            challenge.expectedResponse = totpPart + tswPart;
        }

        return challenge;
    }

    public String generateSecretKey() {
        return gAuth.createCredentials().getKey();
    }
}
