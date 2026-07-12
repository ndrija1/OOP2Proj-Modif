package util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Tiny console logger used across the whole application so that every layer
 * reports what it is doing in one consistent format:
 *
 *     12:34:56.789 [INFO ] Engine: simulation started, 27 flights scheduled
 *
 * INFO and ERROR are always printed. DEBUG lines (per-tick details, takeoffs,
 * timer resets...) are only printed when the application is started with
 *
 *     java -Dapp.debug=true app.Main      (or run-debug.bat)
 *
 * which makes the program easy to trace at the defense without flooding the
 * console during normal use.
 */
public final class Log {

    private static final boolean DEBUG = Boolean.getBoolean("app.debug");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private Log() {} // static utility, never instantiated

    public static boolean isDebugEnabled() {
        return DEBUG;
    }

    public static void debug(String tag, String message) {
        if (DEBUG) print("DEBUG", tag, message);
    }

    public static void info(String tag, String message) {
        print("INFO ", tag, message);
    }

    public static void error(String tag, String message, Throwable cause) {
        print("ERROR", tag, message);
        if (cause != null) cause.printStackTrace(System.out);
    }

    private static void print(String level, String tag, String message) {
        System.out.println(LocalTime.now().format(TIME) + " [" + level + "] " + tag + ": " + message);
    }
}
