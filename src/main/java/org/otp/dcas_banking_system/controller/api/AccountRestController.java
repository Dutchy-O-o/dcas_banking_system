package org.otp.dcas_banking_system.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.otp.dcas_banking_system.dto.BalanceResponse;
import org.otp.dcas_banking_system.dto.TransactionResponse;
import org.otp.dcas_banking_system.model.Transaction;
import org.otp.dcas_banking_system.model.User;
import org.otp.dcas_banking_system.repository.TransactionRepository;
import org.otp.dcas_banking_system.service.AuthService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountRestController {

    private final AuthService authService;
    private final TransactionRepository transactionRepository;

    @GetMapping("/balance")
    public BalanceResponse getBalance(Authentication auth) {
        User user = authService.loadUser(auth.getName());
        return new BalanceResponse(user.getAccountNumber(), user.getFullName(), user.getBalance());
    }

    @GetMapping("/transactions")
    public List<TransactionResponse> getTransactions(Authentication auth) {
        User user = authService.loadUser(auth.getName());
        List<Transaction> txs = transactionRepository
                .findBySenderUsernameOrReceiverUsernameOrderByTimestampDesc(
                        user.getFullName(), user.getFullName());
        return txs.stream().map(TransactionResponse::from).toList();
    }
}
