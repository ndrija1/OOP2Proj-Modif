package app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.CsvCodec;
import io.FileService;
import io.JsonCodec;
import io.LoadedData;
import logic.DepartureScheduler;
import logic.ScheduledFlight;
import model.Airport;
import model.Flight;
import model.ValidationException;
import model.World;
import util.TimeUtil;

/**
 * Headless smoke test - runs WITHOUT the GUI and checks the model, the file
 * codecs and the departure scheduler against the test data from the project
 * specification:
 *
 *     java -cp out app.SelfTest
 *
 * Prints one PASS/FAIL line per check and exits with a non-zero code if
 * anything failed. Handy for quickly verifying the non-GUI layers after a
 * change, and a live demonstration that the layers are truly independent of
 * the GUI.
 */
public final class SelfTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        // ---- file loading -------------------------------------------------
        LoadedData csv = FileService.load(Paths.get("data", "test.csv"));
        check("CSV: 16 airports loaded", csv.airports.size() == 16);
        check("CSV: 27 flights loaded", csv.flights.size() == 27);

        LoadedData json = FileService.load(Paths.get("data", "test.json"));
        check("JSON: 16 airports loaded", json.airports.size() == 16);
        check("JSON: 27 flights loaded", json.flights.size() == 27);

        check("CSV and JSON agree on first airport",
                csv.airports.get(0).getCode().equals(json.airports.get(0).getCode()));

        // ---- round trips ----------------------------------------------------
        Path tmpCsv = Files.createTempFile("selftest", ".csv");
        Files.write(tmpCsv, CsvCodec.save(csv.airports, csv.flights).getBytes("UTF-8"));
        LoadedData csvAgain = FileService.load(tmpCsv);
        check("CSV round trip preserves all data",
                csvAgain.airports.size() == 16 && csvAgain.flights.size() == 27);
        Files.deleteIfExists(tmpCsv);

        Path tmpJson = Files.createTempFile("selftest", ".json");
        Files.write(tmpJson, JsonCodec.save(json.airports, json.flights).getBytes("UTF-8"));
        LoadedData jsonAgain = FileService.load(tmpJson);
        check("JSON round trip preserves all data",
                jsonAgain.airports.size() == 16 && jsonAgain.flights.size() == 27);
        Files.deleteIfExists(tmpJson);

        // ---- validation must reject bad data --------------------------------
        check("rejects lowercase airport code", throwsValidation(() ->
                new Airport("Test", "beg", 0, 0)));
        check("rejects X outside [-180,180]", throwsValidation(() ->
                new Airport("Test", "TST", 181, 0)));
        check("rejects Y outside [-90,90]", throwsValidation(() ->
                new Airport("Test", "TST", 0, -91)));
        check("rejects flight from an airport to itself", throwsValidation(() ->
                new Flight("BEG", "BEG", 0, 60)));
        check("rejects non-positive flight duration", throwsValidation(() ->
                new Flight("BEG", "JFK", 0, 0)));
        check("rejects invalid departure text", throwsValidation(() ->
                TimeUtil.parseTimeOfDay("25:99")));

        // ---- the 10-minute runway rule --------------------------------------
        // The spec's data has three BEG departures at 00:30, 00:30 and 00:35;
        // the runway rule must push them to 00:30, 00:40 and 00:50.
        World world = new World();
        world.replaceAll(csv.airports, csv.flights);
        DepartureScheduler.Result schedule = DepartureScheduler.schedule(world);
        check("scheduler keeps all valid flights", schedule.timetable.size() == 27);
        check("scheduler reports no warnings", schedule.warnings.isEmpty());

        int[] begTakeoffs = schedule.timetable.stream()
                .filter(sf -> sf.flight.getFrom().equals("BEG")
                           && sf.flight.getDepartureMinutes() <= 35)
                .mapToInt(sf -> sf.actualDepartureMinutes)
                .sorted()
                .toArray();
        check("runway rule: BEG 00:30/00:30/00:35 become 00:30/00:40/00:50",
                begTakeoffs.length == 3
                && begTakeoffs[0] == 30 && begTakeoffs[1] == 40 && begTakeoffs[2] == 50);

        for (ScheduledFlight sf : schedule.timetable) {
            if (sf.actualDepartureMinutes != sf.flight.getDepartureMinutes()) {
                System.out.println("       delayed by runway rule: " + sf);
            }
        }

        // ---- summary ---------------------------------------------------------
        System.out.println(failures == 0
                ? "\nAll checks passed."
                : "\n" + failures + " check(s) FAILED.");
        System.exit(failures == 0 ? 0 : 1);
    }

    private interface ThrowingAction {
        void run() throws ValidationException;
    }

    private static boolean throwsValidation(ThrowingAction action) {
        try {
            action.run();
            return false;
        } catch (ValidationException expected) {
            return true;
        }
    }

    private static void check(String description, boolean ok) {
        System.out.println((ok ? "PASS   " : "FAIL   ") + description);
        if (!ok) failures++;
    }
}
