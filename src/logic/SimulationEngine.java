package logic;

import java.util.*;

import model.Airport;
import model.Flight;
import model.ValidationException;
import model.World;
import util.Log;
import util.Mods;
import util.TimeUtil;

/**
 * The heart of phase C: advances simulated time (1 real s = 10 sim min,
 * tick every 200 ms on a daemon background thread), launches planes from the
 * precomputed timetable and moves them along their routes.
 *
 * Threading contract: every mutable field is only touched inside synchronized
 * methods/blocks; the GUI reads through synchronized getters that hand out
 * COPIES (PlaneView/StormView); the listener is notified OUTSIDE the lock.
 * Simulated time is ACCUMULATED from per-tick nanoTime deltas, so late ticks
 * cause no drift and pause/resume/speed changes cause no jumps.
 */
public class SimulationEngine {

    public enum State { STOPPED, RUNNING, PAUSED }

    public static final int TICK_MS = 200;
    public static final double SIM_MINUTES_PER_REAL_SECOND = 10.0;

    private final Map<String,Airport> redirects=new LinkedHashMap<>(); // [MOD W3] redirection

    private final List<String> events = new ArrayList<>(); // [MOD X16]

    private final Map<String, int[]> airportStats = new HashMap<>(); // [MOD X17] {takeoffs, landings}

    private final List<String> reportLines = new ArrayList<>(); // [MOD X19]

    /** [MOD X20] how many landed planes one airport can park. */
    public static final int PARKING_CAPACITY = 3;

    /** Notified after every tick and every state change (on a background thread!). */
    public interface Listener {
        void simulationUpdated();
    }

    /**
     * Immutable snapshot of one plane, safe to hand to the painting code.
     * Mods widened it: id + departure texts [C1], redirected [C2/W3/W4],
     * route endpoints [W1], progress [X3]. Extra snapshot fields cost nothing
     * while the mods are off - "widen the snapshot" is the standard way to
     * give the GUI new engine data.
     */
    public static class PlaneView {
        public final String id;
        public final double x, y;
        public final String label;
        public final String scheduledDeparture; // "00:30"
        public final String actualDeparture;    // differs when delayed by the runway rule
        public final boolean redirected;        // [MOD C2/W3/W4] off the planned route
        public final double fromX, fromY, toX, toY; // [MOD W1] route endpoints
        public final double progress; // [MOD X3] 0..1 along the current leg


        PlaneView(String id, double x, double y, String label,
                  String scheduledDeparture, String actualDeparture, boolean redirected, double fromX, double fromY, double toX, double toY, double progress) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.label = label;
            this.scheduledDeparture = scheduledDeparture;
            this.actualDeparture = actualDeparture;
            this.redirected = redirected;
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
            this.progress = progress;
        }
    }

    // ===== [MOD C2] storms: data ============================================
    // The engine needs NO Mods flag: storms can only be CREATED through
    // MapPanel's right-click handler, which is gated. With the mod off, this
    // list stays empty and the tick-time storm check loops over nothing.

    /** Immutable snapshot of one storm for painting. */
    public static class StormView {
        public final double x, y, radius;   // world coordinates
        public StormView(double x, double y, double radius) {
            this.x = x;
            this.y = y;
            this.radius = radius;
        }
    }

    private static class Storm {
        final double x, y;
        double radius;
        Storm(double x, double y, double radius) {
            this.x = x;
            this.y = y;
            this.radius = radius;
        }
    }

    /** Radius a storm is born with, in world units (it grows while held). */
    public static final double STORM_START_RADIUS = 2.0;
    private final List<Storm> storms = new ArrayList<>();
    // ===== [end MOD C2] =====================================================
    private final World world;
    private final Listener listener;

    private State state = State.STOPPED;
    private double simMinutes;
    private long lastTickNanos;
    private Timer timer;

    private List<ScheduledFlight> pending = new ArrayList<>();
    private final List<ScheduledFlight> timetable = new ArrayList<>(); // [MOD X23] full, never destroyed
    private final List<ActivePlane> active = new ArrayList<>();
    private int totalFlights;
    private int completedFlights;
    private double speedFactor = 1.0;    // [MOD X8] multiplier on simulated time
    private int redirectedLanded;        // [MOD X2] landed planes that were redirected
    private double averageDelayMinutes;  // [MOD X2] computed once from the timetable
    private final Map<String, Double> lastLandingAt = new HashMap<>(); // [MOD X10]


    public SimulationEngine(World world, Listener listener) {
        this.world = world;
        this.listener = listener;
    }

    // ------------------------------------------------------------- controls

    public synchronized List<String> start() throws ValidationException {
        if(state!=State.STOPPED) {
            throw new ValidationException("Simulation already started");
        }
        DepartureScheduler.Result schedule=DepartureScheduler.schedule(world);
        if(schedule.timetable.isEmpty()) {
            throw new ValidationException("Departure timetable is empty");
        }
        pending=new ArrayList<>(schedule.timetable);
        timetable.clear();
        timetable.addAll(schedule.timetable); // [MOD X23] keep the full schedule for rewind
        active.clear();
        totalFlights=pending.size();
        completedFlights=0;
        redirectedLanded=0;
        // [MOD X2] average takeoff delay is known UP FRONT - it is a property
        // of the timetable, not something to measure at runtime
        double totalDelay = 0;
        for (ScheduledFlight sf : pending) {
            totalDelay += sf.actualDepartureMinutes - sf.flight.getDepartureMinutes();
        }
        averageDelayMinutes = totalDelay / pending.size();
        simMinutes=0;
        lastTickNanos=System.nanoTime();
        state = State.RUNNING;
        startTimer();
        notifyListener();
        return schedule.warnings;
    }

    public synchronized void pause() {
        if(state!=State.RUNNING) {
            return;
        }
        stopTimer();
        state = State.PAUSED;
        notifyListener();
    }

    /**
     * [DEBUG] Jump the simulated clock FORWARD by the given minutes. The tick
     * loop is level-based (it compares against simMinutes), so the next tick
     * launches and lands everything in the skipped window in one step. Forward
     * only: takeoffs/landings are destructive list mutations that cannot be
     * un-done, so a backward jump would leave inconsistent state.
     */
    public synchronized void skipForward(int minutes) {
        if (state == State.STOPPED || minutes <= 0) return;
        simMinutes += minutes;
        Log.info("Engine", "skipped forward to " + TimeUtil.formatClock(simMinutes));
        notifyListener();
    }

    public synchronized void resume() {
        if(State.PAUSED!=state) {
            return;
        }
        lastTickNanos=System.nanoTime();
        state = State.RUNNING;
        startTimer();
        notifyListener();
    }

    public synchronized void reset() {
        stopTimer();
        pending=new ArrayList<>();
        active.clear();
        simMinutes=0;
        totalFlights=0;
        completedFlights=0;
        redirectedLanded=0;
        averageDelayMinutes=0;
        speedFactor = 1.0; // [MOD X8]
        state = State.STOPPED;
        storms.clear(); // [MOD C2] reset means a clean world - weather included
        redirects.clear(); // [MOD W3] ...and all airports reopen
        timetable.clear(); // [MOD X23]
        lastLandingAt.clear(); // [MOD X10] old landing times must not block new runs
        events.clear();        // [MOD X16] every new-state addition ends up here...
        airportStats.clear();  // [MOD X17] ...that is the reset() rule
        reportLines.clear();   // [MOD X19]
        notifyListener();
    }

    // ------------------------------------------------------------- queries

    public synchronized State getState()          { return state; }
    public synchronized double getSimMinutes()    { return simMinutes; }
    public synchronized int getTotalFlights()     { return totalFlights; }
    public synchronized int getCompletedFlights() { return completedFlights; }
    public synchronized int getActiveCount()      { return active.size(); }

    public synchronized List<PlaneView> getPlaneViews() {
        List<PlaneView> planeViews=new ArrayList<>();
        for(ActivePlane activePlane:active) {
            //c1 modification
            ScheduledFlight sf=activePlane.scheduled;
            planeViews.add(new PlaneView(
                    idOf(activePlane),
                    activePlane.x,activePlane.y, sf.flight.getLabel(),sf.flight.getDepartureText(),TimeUtil.formatClock(sf.actualDepartureMinutes),activePlane.redirected,sf.from.getX(),sf.from.getY(),sf.to.getX(),sf.to.getY(),activePlane.progress)
            );//end
        }
        return planeViews;
    }

    // ------------------------------------------------------------ internals

    private void startTimer() {
        timer = new Timer("simulation-engine", true); // daemon: never blocks app exit
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    tick();
                } catch (Exception e) {
                    Log.error("Engine", "unexpected error during simulation tick", e);
                }
            }
        }, 0, TICK_MS);
    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void tick() {
        synchronized (this) {
            if(State.RUNNING!=state) {
                return;
            }
            long now=System.nanoTime();
            double realSeconds=(now-lastTickNanos)/1_000_000_000.0;
            lastTickNanos=now;
            simMinutes+=realSeconds*SIM_MINUTES_PER_REAL_SECOND*speedFactor;// [MOD X8]

            // [MOD X23] rewind mode: the whole state is a PURE FUNCTION of time
            // (active planes derived from the never-destroyed timetable), so the
            // clock may run backward with a negative speedFactor. Incompatible
            // with the destructive path below (redirect/storm/holding carry
            // history), hence the strict either/or.
            if (Mods.X23_REWIND) {
                if (simMinutes < 0) simMinutes = 0;
                rebuildFromTime();
            } else {

            for (Iterator<ScheduledFlight> it = pending.iterator(); it.hasNext(); ) {
                ScheduledFlight sf = it.next();
                if (sf.actualDepartureMinutes > simMinutes) break; // sorted: rest not due yet
                // [MOD W3] origin closed? the flight stays on the ground and
                // tries again every tick until the airport reopens.
                if (redirects.containsKey(sf.from.getCode())) continue;
                it.remove();
                ActivePlane plane = new ActivePlane(sf);
                // [MOD W3] destination closed? redirect immediately at takeoff
                // (resolveReplacement follows chains of closures)
                Airport replacement = Mods.X10_HOLDING
                        ? null : resolveReplacement(sf.to.getCode()); // [MOD X10] fly there and circle
                if (replacement != null) plane.redirect(replacement, simMinutes);
                active.add(plane);
                Log.debug("Engine", "takeoff at " + TimeUtil.formatClock(simMinutes) + ": "
                        + sf.flight.getLabel()
                        + (replacement != null ? " (destination closed, diverted to "
                                + replacement.getCode() + ")" : ""));
                addEvent("takeoff " + sf.flight.getLabel());       // [MOD X16]
                statsFor(sf.from.getCode())[0]++;                  // [MOD X17]
            }
            for(ActivePlane activePlane:active) {
                activePlane.updatePosition(simMinutes);
            }
            // [MOD C2] a plane that enters a storm turns back to its origin
            // (checked AFTER positions move, BEFORE arrivals are removed).
            for(ActivePlane activePlane:active) {
                if(activePlane.redirected)continue;
                for(Storm s:storms){
                    double dx=activePlane.x-s.x;
                    double dy=activePlane.y-s.y;
                    if(dx*dx+dy*dy<s.radius*s.radius) {
                        activePlane.turnBack(simMinutes);
                        Log.debug("Engine", "storm hit: " + activePlane.scheduled.flight.getLabel()
                                + " returning to " + activePlane.scheduled.from.getCode());
                        addEvent("storm: " + activePlane.scheduled.flight.getLabel()
                                + " turns back"); // [MOD X16]
                        break;
                    }
                }
            }
            // [MOD X13] all pairs (O(n^2), n stays small): circles touching?
            if (Mods.X13_AVOIDANCE) {
                double limit = 2 * ActivePlane.COLLISION_RADIUS;
                for (int i = 0; i < active.size(); i++) {
                    for (int j = i + 1; j < active.size(); j++) {
                        ActivePlane a = active.get(i), b = active.get(j);
                        if (a.holding || b.holding) continue; // near-airport circling is fine
                        double dx = a.x - b.x, dy = a.y - b.y;
                        if (dx * dx + dy * dy >= limit * limit) continue;
                        ActivePlane younger = a.scheduled.actualDepartureMinutes
                                >= b.scheduled.actualDepartureMinutes ? a : b;
                        if (!younger.evading) {
                            younger.slowDown(simMinutes);
                            Log.debug("Engine", "collision avoided: "
                                    + younger.scheduled.flight.getLabel() + " slows down");
                        }
                    }
                }
            }
            for(java.util.Iterator<ActivePlane> iterator=active.iterator(); iterator.hasNext();) {
                ActivePlane activePlane=iterator.next();

                // [MOD W3] the REAL landing place - a redirected plane
                // does not land where its flight plan says.
                Airport landedAt = activePlane.redirected
                        ? activePlane.redirectTarget : activePlane.scheduled.to;

                // [MOD X10 + X20] may it land? Each active mod contributes its
                // own veto; the plane circles while ANY reason blocks it.
                if ((Mods.X10_HOLDING || Mods.X20_PARKING)
                        && (activePlane.arrived || activePlane.holding)) {
                    String blockReason = null;
                    if (Mods.X10_HOLDING) {
                        if (redirects.containsKey(landedAt.getCode())) {
                            blockReason = "airport closed";
                        } else if (simMinutes < lastLandingAt
                                .getOrDefault(landedAt.getCode(), -1e9) + 10) {
                            blockReason = "landing slot busy";
                        }
                    }
                    // [MOD X20] landed-and-parked planes never leave in this
                    // model, so the landing count IS the parking occupancy.
                    if (blockReason == null && Mods.X20_PARKING
                            && statsFor(landedAt.getCode())[1] >= PARKING_CAPACITY) {
                        blockReason = "parking full";
                    }
                    if (blockReason == null) {
                        activePlane.arrived = true;   // circling plane may land now
                    } else if (activePlane.arrived) {
                        activePlane.enterHolding(simMinutes);
                        Log.debug("Engine", activePlane.scheduled.flight.getLabel()
                                + " holding above " + landedAt.getCode()
                                + " (" + blockReason + ")");
                    }
                }

                if(activePlane.arrived){
                    lastLandingAt.put(landedAt.getCode(), simMinutes); // [MOD X10]
                    Log.debug("Engine", "landing at " + TimeUtil.formatClock(simMinutes)
                            + ": " + activePlane.scheduled.flight.getLabel()
                            + " landed at " + landedAt.getCode()
                            + (activePlane.redirected ? " (redirected)" : ""));
                    addEvent("landing " + activePlane.scheduled.flight.getLabel()
                            + " at " + landedAt.getCode()); // [MOD X16]
                    statsFor(landedAt.getCode())[1]++;      // [MOD X17]
                    if (Mods.X19_REPORT) {                  // [MOD X19] one CSV row per flight
                        reportLines.add(activePlane.scheduled.flight.getLabel() + ","
                                + activePlane.scheduled.flight.getDepartureText() + ","
                                + TimeUtil.formatClock(activePlane.scheduled.actualDepartureMinutes) + ","
                                + landedAt.getCode() + ","
                                + (activePlane.redirected ? "yes" : "no"));
                    }
                    if (activePlane.redirected) redirectedLanded++; // [MOD X2]
                    iterator.remove();
                    completedFlights++;
                }
            }
            } // [MOD X23] end of the non-rewind (destructive) path
        }
        notifyListener();

    }

    // [MOD X23] Rebuild 'active' and 'completedFlights' purely from simMinutes
    // and the immutable timetable. No destructive state means time is fully
    // reversible: a plane is airborne iff actualDeparture <= now < arrival.
    private void rebuildFromTime() {
        active.clear();
        int done = 0;
        for (ScheduledFlight sf : timetable) {
            int arrival = sf.actualDepartureMinutes + sf.flight.getDurationMinutes();
            if (simMinutes >= arrival) { done++; continue; }
            if (simMinutes >= sf.actualDepartureMinutes) {
                ActivePlane p = new ActivePlane(sf);
                p.updatePosition(simMinutes);
                active.add(p);
            }
        }
        completedFlights = done;
    }

    private void notifyListener() {
        if (listener != null) {
            listener.simulationUpdated();
        }
    }

    /** Stable plane id - single source of truth for snapshots and lookups. */
    private static String idOf(ActivePlane p) {
        return p.scheduled.flight.getLabel() + "@" + p.scheduled.actualDepartureMinutes;
    }

    // ===== [MOD C2] storms: API for MapPanel ================================
    // All synchronized (the tick thread reads storms), views are copies -
    // the same contract as getPlaneViews().

    public synchronized void addStorm(double wx, double wy) {
        storms.add(new Storm(wx, wy, STORM_START_RADIUS));
        Log.debug("Engine", "storm created at " + wx + "," + wy);
    }
    public synchronized void growLatestStorm(double delta) {
        if(!storms.isEmpty())storms.get(storms.size()-1).radius+=delta;
    }
    public synchronized boolean removeStormAt(double x, double y) {
        return storms.removeIf(s->{
            double dx=x-s.x,dy=y-s.y;
            return dx*dx+dy*dy<=s.radius*s.radius;
        });
    }

    public synchronized List<StormView> getStormViews() {
        List<StormView> stormViews=new ArrayList<>(storms.size());
        for(Storm storm:storms) stormViews.add(new StormView(storm.x,storm.y,storm.radius));
        return stormViews;
    }
    // ===== [end MOD C2] =====================================================

        // ===== [MOD W3] rerouting: closed airports ==============================
    // Data stays untouched (flights still SAY they go to the closed airport);
    // the redirect is applied lazily: at takeoff, and to planes already in the
    // air when the airport closes. Reopening is just removing the map entry.
    public synchronized void closeAirport(String closedCode,Airport replacement){
        redirects.put(closedCode, replacement);
        // Planes in the air heading there - whether by PLAN or by earlier
        // REDIRECT - divert to the (chain-resolved) replacement.
        Airport resolved = resolveReplacement(closedCode);
        // [MOD X10] with holding ON, closing does NOT divert planes in the
        // air - they reach the airport and CIRCLE until it reopens.
        if (!Mods.X10_HOLDING) {
            for (ActivePlane p : active) {
                String heading = p.redirected ? p.redirectTarget.getCode()
                        : p.scheduled.to.getCode();
                if (heading.equals(closedCode)) {
                    p.redirect(resolved, simMinutes);
                }
            }
        }
        Log.info("Engine", "airport " + closedCode + " closed, flights redirected to "
                + resolved.getCode());
        addEvent("airport " + closedCode + " closed"); // [MOD X16]
    }

    /**
     * Follows the replacement chain until it reaches an OPEN airport.
     * The map itself is never rewritten (each entry remembers the user's
     * original choice), so reopening an airport in the middle of a chain
     * automatically restores the older routes. The UI never offers a closed
     * airport as a replacement, so the chain cannot contain a cycle; the
     * step limit below is just a belt-and-braces guard.
     */
    private Airport resolveReplacement(String closedCode) {
        Airport r = redirects.get(closedCode);
        for (int steps = 0; r != null && redirects.containsKey(r.getCode())
                && steps < redirects.size(); steps++) {
            r = redirects.get(r.getCode());
        }
        return r;
    }
    public synchronized void reopenAirport(String reopenCode){
        redirects.remove(reopenCode);
        Log.info("Engine", "airport " + reopenCode + " reopened");
        addEvent("airport " + reopenCode + " reopened"); // [MOD X16]
    }
    public synchronized boolean isAirportClosed(String closedCode){
        return redirects.containsKey(closedCode);
    }

    public synchronized Set<String>getClosedAirports(){
        return new HashSet<>(redirects.keySet());
    }
    // ===== [end MOD W3] =====================================================

    // ===== [MOD W4] manual redirect of one plane ============================
    /** @return true if the plane was found and redirected; false = it landed. */
    public synchronized boolean redirectPlane(String planeId,Airport newTarget){
        for(ActivePlane p : active) {
            if(idOf(p).equals(planeId)){
                p.redirect(newTarget, simMinutes);
                Log.info("Engine", "plane " + p.scheduled.flight.getLabel()
                        + " manually redirected to " + newTarget.getCode());
                addEvent("manual redirect " + p.scheduled.flight.getLabel()
                        + " -> " + newTarget.getCode()); // [MOD X16]
                return true;
            }
        }
        return false;
    }
    // ===== [end MOD W4] =====================================================

    // ===== [MOD X2] statistics ==============================================
    public synchronized int getTakeoffCount() {
        return active.size() + completedFlights; // everything that ever left the ground
    }
    public synchronized int getRedirectedCount() {
        int inAir = 0;
        for (ActivePlane p : active) if (p.redirected) inAir++;
        return redirectedLanded + inAir;
    }
    public synchronized double getAverageDelayMinutes() {
        return averageDelayMinutes;
    }
    // ===== [end MOD X2] =====================================================

    // ===== [MOD X4] delay one airport's future departures ===================
    public synchronized void delayAirport(String code,int minutes){
        boolean any=false;
        for(int i=0;i<pending.size();i++){
            ScheduledFlight sf=pending.get(i);
            if(sf.from.getCode().equals(code)){
                pending.set(i,new ScheduledFlight(sf.flight,sf.from,sf.to,sf.actualDepartureMinutes+minutes));
                any=true;
            }
        }
        if(any){
            // the launch loop relies on pending being SORTED by actual departure
            pending.sort(Comparator.comparingInt(sf -> sf.actualDepartureMinutes));
            Log.info("Engine", "airport " + code + " delayed " + minutes
                    + " min for all future departures");
        }
    }
    // ===== [end MOD X4] =====================================================

    // ===== [MOD X8] simulation speed ========================================
    // Changing the factor can NEVER cause a jump: simMinutes is ACCUMULATED
    // from per-tick deltas, so a new factor simply applies from the next
    // tick's delta on. (Had we computed simMinutes = elapsedTotal * rate,
    // every speed change would teleport all planes - say this at the defense.)
    public synchronized void setSpeedFactor(double factor) {
        speedFactor = factor;
        Log.info("Engine", "simulation speed set to " + factor + "x");
    }
    public synchronized double getSpeedFactor() {
        return speedFactor;
    }
    // ===== [end MOD X8] =====================================================

    // [MOD X16] one-line event record with a sim timestamp, capped at 200
    private void addEvent(String text) {
        events.add(TimeUtil.formatClock(simMinutes) + "  " + text);
        if (events.size() > 200) events.remove(0);
    }
    public synchronized List<String> getEvents() {
        return new ArrayList<>(events);
    }
    
    private int[] statsFor(String code) { // [MOD X17]
        return airportStats.computeIfAbsent(code, k -> new int[2]);
    }

    /** [MOD X19] CSV: header + one row per completed flight. */
    public synchronized List<String> getReportLines() {
        List<String> out = new ArrayList<>();
        out.add("FLIGHT,SCHEDULED,ACTUAL_TAKEOFF,LANDED_AT,REDIRECTED");
        out.addAll(reportLines);
        return out;
    }

    /** [MOD X17] {takeoffs, landings, holding-now} for one airport. */
    public synchronized int[] getAirportStats(String code) {
        int[] s = statsFor(code);
        int holdingNow = 0;
        for (ActivePlane p : active) {
            Airport t = p.redirected ? p.redirectTarget : p.scheduled.to;
            if (p.holding && t.getCode().equals(code)) holdingNow++;
        }
        return new int[]{s[0], s[1], holdingNow};
    }

    // ===== [MOD X18] emergency: nearest OPEN airport ========================
    /** @return the airport it was sent to, or null if the plane already landed. */
    public synchronized Airport emergencyLand(String planeId) {
        for (ActivePlane p : active) {
            if (!idOf(p).equals(planeId)) continue;
            Airport nearest = null;
            double best = Double.MAX_VALUE;
            for (Airport a : world.getAirports()) {
                if (redirects.containsKey(a.getCode())) continue; // closed = unusable
                double d = Math.hypot(a.getX() - p.x, a.getY() - p.y);
                if (d < best) { best = d; nearest = a; }
            }
            if (nearest == null) return null; // no open airport exists
            p.redirect(nearest, simMinutes);
            Log.info("Engine", "EMERGENCY: " + p.scheduled.flight.getLabel()
                    + " diverting to nearest open airport " + nearest.getCode());
            addEvent("EMERGENCY " + p.scheduled.flight.getLabel()
                    + " -> " + nearest.getCode()); // [MOD X16]
            return nearest;
        }
        return null;
    }
    // ===== [end MOD X18] ====================================================

    // ===== [MOD X22] add a flight to a RUNNING simulation ===================
    // The sim runs off a snapshot (pending), so adding to the World does not
    // affect the current run. This injects the ONE new flight - it does NOT
    // reschedule everything (that would wipe the planes already in the air).
    public synchronized void addFlightLive(Flight f) {
        if(state==State.STOPPED)return;
        Airport from=world.findAirport(f.getFrom());
        Airport to=world.findAirport(f.getTo());
        if(from==null||to==null) return;
        // Runway rule: not before now, and >= 10 min after the last queued
        // takeoff from the same origin.
        // Desired slot: honor the typed departure, but never before NOW.
        int desired = Math.max(f.getDepartureMinutes(), (int) Math.ceil(simMinutes));
        // Runway rule applies ONLY near that slot: bump past any same-origin
        // takeoff within 10 min of 'desired' (not the whole day's schedule).
        boolean bumped = true;
        while (bumped) {
            bumped = false;
            for (ScheduledFlight sf : pending) {
                if (sf.from.getCode().equals(from.getCode())
                        && Math.abs(sf.actualDepartureMinutes - desired)
                        < DepartureScheduler.RUNWAY_SLOT_MINUTES) {
                    desired = sf.actualDepartureMinutes + DepartureScheduler.RUNWAY_SLOT_MINUTES;
                    bumped = true;
                }
            }
        }
        int actual = desired;
        pending.add(new ScheduledFlight(f,from,to,actual));
        pending.sort(Comparator.comparingInt(sf -> sf.actualDepartureMinutes));
        totalFlights++; // it is part of this run now
        Log.info("Engine", "flight " + f.getLabel() + " added live, takeoff "
                + TimeUtil.formatClock(actual));
        addEvent("added live " + f.getLabel()); // [MOD X16]
        notifyListener();
    }
    // ===== [end MOD X22] ====================================================
}
