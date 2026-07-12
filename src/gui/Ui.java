package gui;

import java.awt.Component;

import javax.swing.JOptionPane;

import util.Log;

/**
 * Small helpers so every dialog in the application looks the same and every
 * shown message also lands in the log (which makes "what did the user see?"
 * easy to answer when debugging).
 */
public final class Ui {

    private Ui() {} // static utility, never instantiated

    /** Shows an error dialog with a user friendly explanation. */
    public static void error(Component parent, String message) {
        Log.info("UI", "error dialog: " + message);
        JOptionPane.showMessageDialog(parent, wrap(message), "Error", JOptionPane.ERROR_MESSAGE);
    }

    /** Shows a warning that does not block the user's work. */
    public static void warn(Component parent, String message) {
        Log.info("UI", "warning dialog: " + message);
        JOptionPane.showMessageDialog(parent, wrap(message), "Warning", JOptionPane.WARNING_MESSAGE);
    }

    /** Shows a confirmation of a successful action. */
    public static void info(Component parent, String message) {
        Log.info("UI", "info dialog: " + message);
        JOptionPane.showMessageDialog(parent, wrap(message), "Information",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /** Asks a yes/no question; returns true when the user picks Yes. */
    public static boolean confirm(Component parent, String question) {
        int answer = JOptionPane.showConfirmDialog(parent, wrap(question), "Please confirm",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        Log.debug("UI", "confirm \"" + question + "\" -> " + (answer == JOptionPane.YES_OPTION));
        return answer == JOptionPane.YES_OPTION;
    }

    /**
     * JOptionPane does not wrap long lines on its own; rendering the message
     * as constrained HTML keeps even detailed error explanations readable.
     */
    private static String wrap(String message) {
        String escaped = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<html><body style='width: 360px'>" + escaped + "</body></html>";
    }
}
