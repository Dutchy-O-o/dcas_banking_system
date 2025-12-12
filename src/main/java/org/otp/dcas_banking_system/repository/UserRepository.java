package org.otp.dcas_banking_system.repository;
import org.otp.dcas_banking_system.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username); // E-posta ile bulacak
    boolean existsByUsername(String username);

    // YENİ: Hesap Numarası ile bulma
    Optional<User> findByAccountNumber(String accountNumber);
}