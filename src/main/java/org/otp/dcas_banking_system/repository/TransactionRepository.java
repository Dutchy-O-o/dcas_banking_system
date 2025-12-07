package org.otp.dcas_banking_system.repository;
import org.otp.dcas_banking_system.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    // Kullanıcının dahil olduğu (gönderen veya alan) işlemleri bul, tarihe göre sırala
    List<Transaction> findBySenderUsernameOrReceiverUsernameOrderByTimestampDesc(String sender, String receiver);
}