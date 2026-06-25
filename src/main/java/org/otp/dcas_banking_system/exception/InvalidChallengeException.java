package org.otp.dcas_banking_system.exception;

public class InvalidChallengeException extends RuntimeException {
    public InvalidChallengeException(String message) {
        super(message);
    }
}
