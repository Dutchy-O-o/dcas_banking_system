package org.otp.dcas_banking_system.exception;

public class TransferValidationException extends RuntimeException {
    public TransferValidationException(String message) {
        super(message);
    }
}
