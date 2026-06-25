package org.otp.dcas_banking_system.controller.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.otp.dcas_banking_system.dto.TransactionResponse;
import org.otp.dcas_banking_system.dto.TransferRequest;
import org.otp.dcas_banking_system.model.Transaction;
import org.otp.dcas_banking_system.service.TransferService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/transfer")
@RequiredArgsConstructor
public class TransferRestController {

    private final TransferService transferService;

    @PostMapping
    public ResponseEntity<TransactionResponse> transfer(@Valid @RequestBody TransferRequest req,
                                                        Authentication auth) {
        log.info("REST transfer request: from={}, to={}, amount={}",
                auth.getName(), req.receiverAccount(), req.amount());
        transferService.validateReceiver(req.receiverAccount(), req.receiverName());
        Transaction tx = transferService.executeTransfer(
                auth.getName(), req.receiverAccount(), req.amount(), req.description());
        return ResponseEntity.ok(TransactionResponse.from(tx));
    }
}
