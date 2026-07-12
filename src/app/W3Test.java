package app;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import io.FileService;
import io.LoadedData;
import logic.SimulationEngine;
import model.Airport;
import model.World;

/**
 * Headless W3 (rerouting) test - runs the REAL engine in real time for
 * ~100 seconds (about 16h40m of simulated time), closes five airports in a
 * deliberately chained order, and checks two invariants on every plane:
 *
 *   1. no plane LANDS at an airport that was closed at the time of landing,
 *   2. no plane TAKES OFF from a closed airport.
 *
 * The observer only uses the public snapshot API (getPlaneViews), exactly
 * like the GUI would: a plane's landing place is the airport nearest to its
 * last seen position. Run with:  java -cp out app.W3Test
 */
public final class W3Test {

    public static void main(String[] args) throws Exception {
        LoadedData data = FileService.load(Paths.get("data", "test.csv"));
        World world = new World();
        world.replaceAll(data.airports, data.flights);
        SimulationEngine engine = new SimulationEngine(world, () -> {});
        engine.start();

        // Closing plan: chained on purpose (JFK's shelter YYZ closes, YYZ's
        // shelter LHR closes too...), so chain resolution gets exercised.
        String[][] plan = {{"JFK","YYZ"}, {"YYZ","LHR"}, {"LHR","CDG"}, {"IST","AMS"}, {"FRA","AMS"}};
        long[] planAtMillis = {2000, 4000, 6000, 8000, 10000};
        int planIdx = 0;

        Map<String, double[]> lastPos = new HashMap<>(); // plane id -> last {x, y}
        Map<String, Boolean> wasRedirected = new HashMap<>();
        Set<String> seen = new HashSet<>();              // ids that already took off
        Map<String, Long> closedAt = new HashMap<>();    // code -> wall time of closing
        int landings = 0, redirectedLandings = 0, violations = 0;

        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 100_000) {
            long t = System.currentTimeMillis() - start;

            if (planIdx < plan.length && t >= planAtMillis[planIdx]) {
                Airport replacement = world.findAirport(plan[planIdx][1]);
                engine.closeAirport(plan[planIdx][0], replacement);
                closedAt.put(plan[planIdx][0], System.currentTimeMillis());
                System.out.println("TEST   closed " + plan[planIdx][0]
                        + " -> " + replacement.getCode());
                planIdx++;
            }

            Set<String> nowIds = new HashSet<>();
            for (SimulationEngine.PlaneView p : engine.getPlaneViews()) {
                nowIds.add(p.id);
                lastPos.put(p.id, new double[]{p.x, p.y});
                wasRedirected.put(p.id, p.redirected);
                if (seen.add(p.id)) {
                    // first sighting = takeoff; origin must not be closed
                    String origin = p.label.split("-")[0];
                    if (isLongClosed(closedAt, origin)) {
                        System.out.println("FAIL   " + p.label
                                + " took off from CLOSED " + origin);
                        violations++;
                    }
                }
            }

            // ids seen before but gone now = landed planes
            for (Iterator<Map.Entry<String, double[]>> it = lastPos.entrySet().iterator();
                 it.hasNext(); ) {
                Map.Entry<String, double[]> e = it.next();
                if (nowIds.contains(e.getKey())) continue;
                Airport nearest = nearestAirport(world, e.getValue());
                landings++;
                if (Boolean.TRUE.equals(wasRedirected.get(e.getKey()))) redirectedLandings++;
                if (nearest != null && isLongClosed(closedAt, nearest.getCode())) {
                    System.out.println("FAIL   " + e.getKey()
                            + " LANDED at CLOSED " + nearest.getCode());
                    violations++;
                }
                it.remove();
            }

            Thread.sleep(100);
        }

        int held = engine.getTotalFlights() - engine.getCompletedFlights()
                - engine.getActiveCount();
        System.out.println();
        System.out.println("landings observed: " + landings
                + " (redirected: " + redirectedLandings + ")");
        System.out.println("still pending/held on the ground: " + held
                + ", still in the air: " + engine.getActiveCount());
        System.out.println(violations == 0
                ? "PASS   no plane landed at or took off from a closed airport"
                : "FAIL   " + violations + " violation(s)");
        System.exit(violations == 0 ? 0 : 1);
    }

    /** Closed for more than a second - filters out poll-timing races. */
    private static boolean isLongClosed(Map<String, Long> closedAt, String code) {
        Long at = closedAt.get(code);
        return at != null && System.currentTimeMillis() - at > 1000;
    }

    private static Airport nearestAirport(World world, double[] pos) {
        Airport best = null;
        double bestDist = Double.MAX_VALUE;
        for (Airport a : world.getAirports()) {
            double d = Math.hypot(a.getX() - pos[0], a.getY() - pos[1]);
            if (d < bestDist) { bestDist = d; best = a; }
        }
        return best;
    }
}
