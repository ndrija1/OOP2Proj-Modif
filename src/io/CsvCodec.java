package io;

import java.util.ArrayList;
import java.util.List;

import model.Airport;
import model.Flight;
import model.ValidationException;
import util.TimeUtil;

/**
 * Reads and writes the CSV format defined by the project specification:
 *
 * <pre>
 * # AIRPORTS
 * CODE,NAME,X,Y
 * LHR,London Heathrow,0,51
 * ...
 *
 * # FLIGHTS
 * FROM,TO,DEPARTURE,DURATION
 * LHR,JFK,08:30,420
 * ...
 * </pre>
 *
 * The parser is deliberately strict about structure (sections and headers
 * must be present) but tolerant about cosmetics (blank lines, extra spaces,
 * upper/lower case in headers). Every error message includes the line number.
 */
public final class CsvCodec {

    private static final String AIRPORTS_HEADER = "CODE,NAME,X,Y";
    private static final String FLIGHTS_HEADER = "FROM,TO,DEPARTURE,DURATION";

    private CsvCodec() {} // static utility, never instantiated

    // -------------------------------------------------------------- loading

    public static LoadedData load(List<String> lines) throws DataFileException {
        List<Airport> airports = new ArrayList<>();
        List<Flight> flights = new ArrayList<>();

        // Which section are we currently inside?
        final int NONE = 0, AIRPORTS = 1, FLIGHTS = 2;
        int section = NONE;
        boolean headerExpected = false;
        boolean sawAirportsSection = false, sawFlightsSection = false;

        for (int i = 0; i < lines.size(); i++) {
            int lineNo = i + 1;
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue; // blank lines are allowed anywhere

            // --- section markers -----------------------------------------
            if (line.startsWith("#")) {
                String marker = line.substring(1).trim().toUpperCase();
                if (marker.contains("AIRPORT")) {
                    section = AIRPORTS;
                    sawAirportsSection = true;
                    headerExpected = true;
                } else if (marker.contains("FLIGHT")) {
                    section = FLIGHTS;
                    sawFlightsSection = true;
                    headerExpected = true;
                } else {
                    throw new DataFileException(
                            "Line " + lineNo + ": unknown section marker \"" + line
                            + "\". Expected \"# AIRPORTS\" or \"# FLIGHTS\".");
                }
                continue;
            }

            if (section == NONE) {
                throw new DataFileException(
                        "Line " + lineNo + ": data found before any section marker. "
                        + "The file must start with a \"# AIRPORTS\" section - "
                        + "check that you selected a valid data file.");
            }

            // --- header line right after a marker -------------------------
            if (headerExpected) {
                String normalized = line.replace(" ", "").toUpperCase();
                String expected = (section == AIRPORTS) ? AIRPORTS_HEADER : FLIGHTS_HEADER;
                if (!normalized.equals(expected)) {
                    throw new DataFileException(
                            "Line " + lineNo + ": the file does not contain the expected columns. "
                            + "Expected the header \"" + expected + "\" but found \"" + line
                            + "\". Check the file format or choose another file.");
                }
                headerExpected = false;
                continue;
            }

            // --- data lines ------------------------------------------------
            if (section == AIRPORTS) {
                airports.add(parseAirportLine(line, lineNo));
            } else {
                flights.add(parseFlightLine(line, lineNo));
            }
        }

        if (!sawAirportsSection) {
            throw new DataFileException(
                    "The file does not contain a \"# AIRPORTS\" section. "
                    + "It does not look like a valid data file for this application - "
                    + "check the format or choose another file.");
        }
        if (!sawFlightsSection) {
            throw new DataFileException(
                    "The file does not contain a \"# FLIGHTS\" section. "
                    + "Add the section (it may be empty) or choose another file.");
        }
        return new LoadedData(airports, flights);
    }

    private static Airport parseAirportLine(String line, int lineNo) throws DataFileException {
        String[] parts = line.split(",", -1);
        if (parts.length != 4) {
            throw new DataFileException(
                    "Line " + lineNo + ": expected 4 comma-separated values (CODE,NAME,X,Y) but found "
                    + parts.length + ". Note: airport names must not contain commas in CSV files - "
                    + "use the JSON format for such names.");
        }
        double x = parseNumber(parts[2], "X coordinate", lineNo);
        double y = parseNumber(parts[3], "Y coordinate", lineNo);
        try {
            return new Airport(parts[1].trim(), parts[0].trim(), x, y);
        } catch (ValidationException e) {
            throw new DataFileException("Line " + lineNo + ": " + e.getMessage());
        }
    }

    private static Flight parseFlightLine(String line, int lineNo) throws DataFileException {
        String[] parts = line.split(",", -1);
        if (parts.length != 4) {
            throw new DataFileException(
                    "Line " + lineNo + ": expected 4 comma-separated values (FROM,TO,DEPARTURE,DURATION) "
                    + "but found " + parts.length + ".");
        }
        try {
            int departure = TimeUtil.parseTimeOfDay(parts[2]);
            int duration = (int) parseNumber(parts[3], "duration", lineNo);
            return new Flight(parts[0].trim(), parts[1].trim(), departure, duration);
        } catch (ValidationException e) {
            throw new DataFileException("Line " + lineNo + ": " + e.getMessage());
        }
    }

    private static double parseNumber(String token, String what, int lineNo) throws DataFileException {
        try {
            return Double.parseDouble(token.trim());
        } catch (NumberFormatException e) {
            throw new DataFileException(
                    "Line " + lineNo + ": " + what + " must be a number, but \""
                    + token.trim() + "\" was found.");
        }
    }

    // -------------------------------------------------------------- saving

    /** Produces the file content in the exact format shown in the specification. */
    public static String save(List<Airport> airports, List<Flight> flights) {
        StringBuilder sb = new StringBuilder();
        sb.append("# AIRPORTS\n");
        sb.append(AIRPORTS_HEADER).append('\n');
        for (Airport a : airports) {
            sb.append(a.getCode()).append(',')
              .append(a.getName()).append(',')
              .append(formatNumber(a.getX())).append(',')
              .append(formatNumber(a.getY())).append('\n');
        }
        sb.append('\n');
        sb.append("# FLIGHTS\n");
        sb.append(FLIGHTS_HEADER).append('\n');
        for (Flight f : flights) {
            sb.append(f.getFrom()).append(',')
              .append(f.getTo()).append(',')
              .append(f.getDepartureText()).append(',')
              .append(f.getDurationMinutes()).append('\n');
        }
        return sb.toString();
    }

    /** Whole coordinates are written without a decimal point ("51", not "51.0"). */
    private static String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
