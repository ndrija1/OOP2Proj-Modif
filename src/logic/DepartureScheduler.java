package logic;

import java.util.*;

import model.Airport;
import model.Flight;
import model.World;
import util.Log;
import util.Mods;

/**
 * Computes every flight's ACTUAL takeoff time BEFORE the simulation starts:
 * group by origin, sort by scheduled time, assign
 * actual = max(scheduled, previous + 10). Doing it up front keeps the engine
 * trivial and the whole run deterministic. Pure logic - no GUI, no threads
 * (see app.SelfTest for the acceptance checks).
 */
public final class DepartureScheduler {

    /** Result of scheduling: the timetable plus warnings about skipped flights. */
    public static class Result {
        public final List<ScheduledFlight> timetable;
        public final List<String> warnings;

        Result(List<ScheduledFlight> timetable, List<String> warnings) {
            this.timetable = timetable;
            this.warnings = warnings;
        }
    }

    /** Minimum gap between two takeoffs from the same airport, in sim minutes. */
    public static final int RUNWAY_SLOT_MINUTES = 10;

    /** [MOD X6] takeoffs are banned before this minute of the first sim day. */
    public static final int NIGHT_END_MINUTES = 4 * 60;

    private DepartureScheduler() {} // static utility, never instantiated

    public static Result schedule(World world) {
        //   Hints:
        //     Map<String,List<Flight>> byOrigin = new LinkedHashMap<>();
        //     byOrigin.computeIfAbsent(f.getFrom(), k -> new ArrayList<>()).add(f);
        //     group.sort(Comparator.comparingInt(Flight::getDepartureMinutes));
        //     int actual = Math.max(f.getDepartureMinutes(), prev + RUNWAY_SLOT_MINUTES);
        //     timetable.sort(Comparator.comparingInt(sf -> sf.actualDepartureMinutes));
        List<ScheduledFlight> timetable = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String,List<Flight>> byOrigin=new HashMap<>();
        for(Flight f:world.getFlights()){
            if(world.findAirport(f.getFrom())==null||world.findAirport(f.getTo())==null){
                warnings.add("Flight " + f + " was skipped: it references an airport "
                        + "that no longer exists.");
                continue;
            }
            byOrigin.computeIfAbsent(f.getFrom(),k->new ArrayList<>()).add(f);
        }
        for(Map.Entry<String,List<Flight>> e:byOrigin.entrySet()){
            Airport origin=world.findAirport(e.getKey());
            List<Flight> flights=e.getValue();
            // [MOD X7] priority flights claim their slots FIRST; the ordinary
            // +10 rule then shifts everyone else around them.
            if (Mods.X7_PRIORITY) {
                flights.sort(Comparator
                        .comparing((Flight f) -> !world.isPriority(f)) // false < true
                        .thenComparingInt(Flight::getDepartureMinutes));
            } else {
                flights.sort(Comparator.comparingInt(Flight::getDepartureMinutes));
            }            int previousActual=Integer.MIN_VALUE;
            // [MOD X5] airports with more than 5 flights operate TWO runways.
            // The "last takeoff + 10" rule generalizes to "OLDEST of the last
            // k takeoffs + 10" (k = runway count). With k = 1 this is exactly
            // the original rule - the selftest guards that equivalence.
            int runways = (Mods.X5_TWO_RUNWAYS && flights.size() > 5) ? 2 : 1;
            Deque<Integer> recentTakeoffs = new ArrayDeque<>(); // last k actual times
            for(Flight f:flights){
                int earliest = recentTakeoffs.size() < runways
                        ? f.getDepartureMinutes()               // a runway is free
                        : recentTakeoffs.peekFirst() + RUNWAY_SLOT_MINUTES;
                int actual = Math.max(f.getDepartureMinutes(), earliest);
                // [MOD X6] night ban: bump to 04:00, then re-apply the runway rule
                if (Mods.X6_NIGHT_BAN && actual < NIGHT_END_MINUTES) {
                    actual = NIGHT_END_MINUTES;
                    if (recentTakeoffs.size() >= runways) {
                        actual = Math.max(actual, recentTakeoffs.peekFirst() + RUNWAY_SLOT_MINUTES);
                    }
                }
                recentTakeoffs.addLast(actual);
                if (recentTakeoffs.size() > runways) recentTakeoffs.removeFirst();
                previousActual=actual;
                Airport to=world.findAirport(f.getTo());
                ScheduledFlight sf=new ScheduledFlight(f,origin,to,actual);
                timetable.add(sf);
                if (actual != f.getDepartureMinutes()) {
                    Log.debug("Scheduler", sf + " (delayed "
                            + (actual - f.getDepartureMinutes()) + " min by runway congestion)");
                } else {
                    Log.debug("Scheduler", sf.toString());
                }
            }
        }
        timetable.sort(Comparator.comparingInt(sf->sf.actualDepartureMinutes));
        Log.info("Scheduler", "timetable computed: " + timetable.size() + " flights, "
                + warnings.size() + " skipped");
        return new Result(timetable, warnings);
    }
}
