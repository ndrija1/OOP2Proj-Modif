package logic;

import model.Airport;
import model.Flight;
import util.TimeUtil;

/**
 * One flight as the simulation sees it: the original {@link Flight} plus
 * resolved airports and the ACTUAL takeoff time assigned by
 * {@link DepartureScheduler}. Immutable - the timetable never changes
 * mid-run (X4 delay swaps in shifted COPIES instead).
 */
public class ScheduledFlight {

    public final Flight flight;
    public final Airport from;
    public final Airport to;
    public final int actualDepartureMinutes;

    public ScheduledFlight(Flight flight, Airport from, Airport to, int actualDepartureMinutes) {
        this.flight = flight;
        this.from = from;
        this.to = to;
        this.actualDepartureMinutes = actualDepartureMinutes;
    }

    /** Time the plane reaches its destination, in simulated minutes. */
    public int arrivalMinutes() {
        return actualDepartureMinutes + flight.getDurationMinutes();
    }

    @Override
    public String toString() {
        return flight.getLabel() + " scheduled " + flight.getDepartureText()
                + ", actual takeoff " + TimeUtil.formatClock(actualDepartureMinutes);
    }
}
