package model;

import util.TimeUtil;

/**
 * Immutable scheduled flight; departure stored as minutes-since-midnight.
 * The constructor validates codes, origin != destination, departure range
 * and positive duration. Whether the airports EXIST is {@link World}'s job.
 */
public final class Flight {

    private final String from;
    private final String to;
    private final int departureMinutes;
    private final int durationMinutes;

    public Flight(String from, String to, int departureMinutes, int durationMinutes)
            throws ValidationException {
        from = from == null ? "" : from.trim();
        to = to == null ? "" : to.trim();

        if (!from.matches(Airport.CODE_PATTERN)) {
            throw new ValidationException(
                    "Flight origin must be a 3-letter uppercase airport code, but \""
                            + from + "\" was given.");
        }
        if (!to.matches(Airport.CODE_PATTERN)) {
            throw new ValidationException(
                    "Flight destination must be a 3-letter uppercase airport code, but \""
                            + to + "\" was given.");
        }
        if (from.equals(to)) {
            throw new ValidationException(
                    "Flight origin and destination must be different airports (both were \""
                            + from + "\").");
        }
        if (departureMinutes < 0 || departureMinutes >= 24 * 60) {
            throw new ValidationException(
                    "Departure time of flight " + from + "->" + to
                            + " must be between 00:00 and 23:59.");
        }
        if (durationMinutes <= 0) {
            throw new ValidationException(
                    "Duration of flight " + from + "->" + to
                            + " must be a positive number of minutes, but " + durationMinutes
                            + " was given.");
        }

        this.from = from;
        this.to = to;
        this.departureMinutes = departureMinutes;
        this.durationMinutes = durationMinutes;
    }

    public String getFrom()          { return from; }
    public String getTo()            { return to; }
    public int getDepartureMinutes() { return departureMinutes; }
    public int getDurationMinutes()  { return durationMinutes; }

    public String getDepartureText() {
        return TimeUtil.formatTimeOfDay(departureMinutes);
    }

    public String getLabel() {
        return from + "-" + to;
    }

    @Override
    public String toString() {
        return from + "->" + to + " at " + getDepartureText() + " (" + durationMinutes + " min)";
    }
}
