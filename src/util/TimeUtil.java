package util;

import model.ValidationException;

/**
 * Conversions between "minutes" (the unit the model and the simulation work
 * with) and human readable "HH:MM" strings.
 */
public final class TimeUtil {

    private TimeUtil() {} // static utility, never instantiated

    /**
     * Parses a time-of-day string in HH:MM format.
     *
     * @return minutes since midnight (0..1439)
     * @throws ValidationException with a user friendly message if the text is
     *         not a valid time
     */
    public static int parseTimeOfDay(String text) throws ValidationException {
        String trimmed = text == null ? "" : text.trim();
        if (!trimmed.matches("\\d{1,2}:\\d{2}")) {
            throw new ValidationException(
                    "Departure time must be in HH:MM format (e.g. \"08:30\"), but \""
                    + trimmed + "\" was given.");
        }
        String[] parts = trimmed.split(":");
        int hours = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);
        if (hours > 23) {
            throw new ValidationException(
                    "Hour in departure time \"" + trimmed + "\" must be between 0 and 23.");
        }
        if (minutes > 59) {
            throw new ValidationException(
                    "Minutes in departure time \"" + trimmed + "\" must be between 0 and 59.");
        }
        return hours * 60 + minutes;
    }

    /** Formats minutes-since-midnight (0..1439) as "HH:MM". */
    public static String formatTimeOfDay(int minutesSinceMidnight) {
        return String.format("%02d:%02d", minutesSinceMidnight / 60, minutesSinceMidnight % 60);
    }

    /**
     * Formats an absolute amount of simulated minutes as a wall clock.
     * The simulation can run past midnight, so values >= 1440 wrap around and
     * the day number is appended, e.g. "09:10 (day 2)".
     */
    public static String formatClock(double totalSimMinutes) {
        int total = (int) totalSimMinutes;
        int day = total / (24 * 60) + 1;
        int ofDay = total % (24 * 60);
        String clock = formatTimeOfDay(ofDay);
        return day > 1 ? clock + " (day " + day + ")" : clock;
    }
}
