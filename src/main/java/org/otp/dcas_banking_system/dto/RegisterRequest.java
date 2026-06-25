package org.otp.dcas_banking_system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100)
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 6, max = 100, message = "Password must be 6-100 chars")
        String password,

        @NotBlank(message = "TSW is required")
        @Size(min = 4, max = 16, message = "TSW must be 4-16 chars")
        String tsw,

        @NotBlank(message = "APW is required")
        @Size(min = 2, max = 32, message = "APW must be 2-32 chars")
        String apw
) {}
