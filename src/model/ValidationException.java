package model;

/**
 * Thrown whenever user-supplied data (GUI or file) violates a model rule.
 * The message is always written for the END USER, so the GUI shows it as-is.
 */
public class ValidationException extends Exception {

    public ValidationException(String userFriendlyMessage) {
        super(userFriendlyMessage);
    }
}
