package org.otp.dcas_banking_system.dto;

import java.math.BigDecimal;

public record BalanceResponse(
        String accountNumber,
        String fullName,
        BigDecimal balance
) {
}
