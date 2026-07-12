package io;

/**
 * Thrown when a data file cannot be opened, read, parsed or written.
 * Like {@link model.ValidationException}, the message is written for the end
 * user: it says WHAT went wrong, WHERE (file/line/element) and what to do
 * about it, so the GUI can show it in a dialog without any extra processing.
 */
public class DataFileException extends Exception {

    public DataFileException(String userFriendlyMessage) {
        super(userFriendlyMessage);
    }

    public DataFileException(String userFriendlyMessage, Throwable cause) {
        super(userFriendlyMessage, cause);
    }
}
