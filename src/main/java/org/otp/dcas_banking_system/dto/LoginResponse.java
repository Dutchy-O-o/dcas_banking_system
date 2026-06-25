package org.otp.dcas_banking_system.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresInSeconds
) {}
