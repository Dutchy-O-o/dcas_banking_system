package org.otp.dcas_banking_system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.otp.dcas_banking_system.dto.event.TransferCompletedEvent;
import org.otp.dcas_banking_system.exception.InsufficientBalanceException;
import org.otp.dcas_banking_system.exception.TransferValidationException;
import org.otp.dcas_banking_system.exception.UserNotFoundException;
import org.otp.dcas_banking_system.model.OutboxEvent;
import org.otp.dcas_banking_system.model.Transaction;
import org.otp.dcas_banking_system.model.TransactionStatus;
import org.otp.dcas_banking_system.model.User;
import org.otp.dcas_banking_system.repository.OutboxEventRepository;
import org.otp.dcas_banking_system.repository.TransactionRepository;
import org.otp.dcas_banking_system.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public User loadSender(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("Sender not found: " + username));
    }

    public User validateReceiver(String receiverAccount, String receiverName) {
        User receiver = userRepository.findByAccountNumber(receiverAccount)
                .orElseThrow(() -> new TransferValidationException("Account/Name mismatch or not found."));
        if (!receiver.getFullName().equalsIgnoreCase(receiverName)) {
            throw new TransferValidationException("Account/Name mismatch or not found.");
        }
        return receiver;
    }

    public void checkBalance(User sender, BigDecimal amount) {
        if (sender.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance.");
        }
    }

    public boolean requiresChallenge(User sender, BigDecimal amount) {
        return sender.isTransferSecurityEnabled()
                && amount.compareTo(sender.getDcasTransactionLimit()) >= 0;
    }

    @Transactional
    public Transaction executeTransfer(String senderUsername, String receiverAccount,
                                       BigDecimal amount, String description) {
        log.info("Executing transfer: sender={}, receiverAcc={}, amount={}",
                senderUsername, receiverAccount, amount);

        User sender = userRepository.findByUsername(senderUsername)
                .orElseThrow(() -> new UserNotFoundException("Sender not found"));
        User receiver = userRepository.findByAccountNumber(receiverAccount)
                .orElseThrow(() -> new UserNotFoundException("Receiver not found"));

        if (sender.getBalance().compareTo(amount) < 0) {
            log.warn("Transfer rejected (insufficient balance): sender={} balance={} amount={}",
                    senderUsername, sender.getBalance(), amount);
            throw new InsufficientBalanceException("Insufficient balance.");
        }

        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));
        sender.setFailedAttempts(0);
        userRepository.save(sender);
        userRepository.save(receiver);

        Transaction tx = new Transaction();
        tx.setSender(sender);
        tx.setReceiver(receiver);
        tx.setSenderUsername(sender.getFullName());
        tx.setReceiverUsername(receiver.getFullName());
        tx.setAmount(amount);
        tx.setTimestamp(LocalDateTime.now());
        tx.setStatus(TransactionStatus.SUCCESS);
        tx.setDescription(description);
        transactionRepository.save(tx);

        // Transactional Outbox: event, transfer ile AYNI transaction'da yazilir.
        // Transaction rollback olursa event de yazilmaz; commit olursa
        // OutboxPublisher event'i garanti olarak Kafka'ya tasir (at-least-once).
        saveOutboxEvent(tx, sender, receiver, amount);

        log.info("Transfer success: txId={}, sender={}, receiver={}, amount={}",
                tx.getId(), senderUsername, receiver.getUsername(), amount);
        return tx;
    }

    private void saveOutboxEvent(Transaction tx, User sender, User receiver, BigDecimal amount) {
        try {
            TransferCompletedEvent event = new TransferCompletedEvent(
                    tx.getId(),
                    sender.getUsername(),
                    sender.getAccountNumber(),
                    amount,
                    receiver.getFullName(),
                    LocalDateTime.now());

            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setAggregateType("TRANSFER");
            // Partition key: ayni hesabin event'leri ayni partition'da sirali kalir
            outboxEvent.setAggregateId(sender.getAccountNumber());
            outboxEvent.setEventType("TRANSFER_COMPLETED");
            outboxEvent.setPayload(objectMapper.writeValueAsString(event));
            outboxEvent.setCreatedAt(LocalDateTime.now());
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            // Serializasyon hatasi tum transferi geri almasin; bildirim is akisinin
            // parcasi degil. Loglayip devam ediyoruz.
            log.error("Failed to write outbox event for txId={}", tx.getId(), e);
        }
    }
}
