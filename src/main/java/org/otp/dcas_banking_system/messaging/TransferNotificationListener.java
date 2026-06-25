package org.otp.dcas_banking_system.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.otp.dcas_banking_system.config.KafkaConfig;
import org.otp.dcas_banking_system.dto.event.TransferCompletedEvent;
import org.otp.dcas_banking_system.model.User;
import org.otp.dcas_banking_system.repository.UserRepository;
import org.otp.dcas_banking_system.service.EmailService;
import org.otp.dcas_banking_system.service.EncryptionService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * transfer-events konusunu dinleyen bildirim consumer'i.
 *
 * Ayni uygulama icinde calisir (event-driven monolith). Ileride ayri bir
 * notification-service'e tasinmak istenirse bu sinif oldugu gibi tasinir,
 * producer tarafinda hicbir degisiklik gerekmez.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransferNotificationListener {

    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final EncryptionService encryptionService;

    @KafkaListener(topics = KafkaConfig.TRANSFER_EVENTS_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void onTransferCompleted(String payload) throws Exception {
        TransferCompletedEvent event = objectMapper.readValue(payload, TransferCompletedEvent.class);
        log.info("Consuming transfer event: txId={}, sender={}", event.txId(), event.senderUsername());

        // APW gibi sirlar event'te tasinmaz; gonderim aninda DB'den cekilip cozulur
        User sender = userRepository.findByUsername(event.senderUsername())
                .orElseThrow(() -> new IllegalStateException(
                        "Sender not found for event txId=" + event.txId()));

        String apw = encryptionService.decrypt(sender.getApwEncrypted());
        emailService.sendSecurityAlert(sender.getEmail(), sender.getFullName(),
                "Transfer Success", apw,
                "Sent $" + event.amount() + " to " + event.receiverFullName());

        log.info("Transfer notification sent: txId={}", event.txId());
    }
}
