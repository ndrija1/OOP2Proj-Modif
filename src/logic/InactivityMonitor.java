package logic;

import java.util.LinkedHashSet;
import java.util.Set;

import util.Log;

/**
 * Phase-A auto-shutdown: counts down 60 s of inactivity on a swing timer
 * (EDT - no synchronization needed), warns the listener during the last 5 s.
 * The countdown pauses while ANY named reason is active ("airport-selected",
 * "simulation", ...), so independent pause sources cannot cancel each other.
 */
public class InactivityMonitor {

    public static final int TIMEOUT_SECONDS = 60;
    public static final int WARNING_SECONDS = 5;

    public interface Listener {
        void warning(int secondsRemaining);
        void timedOut();
    }

    private final Listener listener;
    private final javax.swing.Timer ticker;
    private int remaining = TIMEOUT_SECONDS;
    private final Set<String> pauseReasons = new LinkedHashSet<>();

    public InactivityMonitor(Listener listener) {
        this.listener = listener;
        this.ticker = new javax.swing.Timer(1000, e -> tick());
        this.ticker.setRepeats(true);
    }

    public void start() {
        remaining = TIMEOUT_SECONDS;
        ticker.start();

    }

    public void reset() {
        remaining = TIMEOUT_SECONDS;
    }

    public void setPaused(String reason, boolean paused) {
        boolean changed=paused?pauseReasons.add(reason):pauseReasons.remove(reason);
        if (changed) {
            Log.debug("Inactivity", (paused ? "paused" : "unpaused") + " (" + reason
                    + "), active pauses: " + pauseReasons);
        }
    }

    public int getRemainingSeconds() {
        return remaining;
    }

    public void stop() {
        ticker.stop();
    }

    private void tick() {
        if(!pauseReasons.isEmpty()) {
            Log.debug("Inactivity", "tick skipped, paused by " + pauseReasons);
            return; // frozen: an airport is selected and/or the simulation is running
        }
        remaining--;
        if(remaining>0 &&remaining<=WARNING_SECONDS) {
            listener.warning(remaining);
        }else if(remaining<=0) {
            ticker.stop();
            listener.timedOut();
        }
    }
}
