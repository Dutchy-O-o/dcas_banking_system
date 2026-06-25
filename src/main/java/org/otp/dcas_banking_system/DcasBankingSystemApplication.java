package org.otp.dcas_banking_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // OutboxPublisher'in @Scheduled poller'i icin gerekli
public class DcasBankingSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(DcasBankingSystemApplication.class, args);
    }

}
