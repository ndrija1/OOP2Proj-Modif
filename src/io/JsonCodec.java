package io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import model.Airport;
import model.Flight;
import model.ValidationException;
import util.Json;
import util.TimeUtil;

/**
 * Reads and writes the JSON format defined by the project specification:
 *
 * <pre>
 * {
 *   "airports": [ {"code":"LHR","name":"London Heathrow","x":0,"y":51}, ... ],
 *   "flights":  [ {"from":"LHR","to":"JFK","departure":"08:30","duration":420}, ... ]
 * }
 * </pre>
 *
 * Uses the hand-written {@link util.Json} parser, so no external libraries
 * are needed. Every error message points at the exact element, e.g.
 * "airports[3]: missing required field \"code\"".
 */
public final class JsonCodec {

    private JsonCodec() {} // static utility, never instantiated

    // -------------------------------------------------------------- loading

    public static LoadedData load(String text) throws DataFileException {
        Object root;
        try {
            root = Json.parse(text);
        } catch (Json.JsonException e) {
            throw new DataFileException(
                    e.getMessage() + " The file is not valid JSON - check the format "
                    + "or choose another file.");
        }
        if (!(root instanceof Map)) {
            throw new DataFileException(
                    "The top level of the JSON file must be an object containing the keys "
                    + "\"airports\" and \"flights\". This file does not look like a valid "
                    + "data file for this application.");
        }
        Map<?, ?> rootMap = (Map<?, ?>) root;

        List<?> airportArray = requireArray(rootMap, "airports");
        List<Airport> airports = new ArrayList<>();
        for (int i = 0; i < airportArray.size(); i++) {
            Map<?, ?> obj = requireObject(airportArray, "airports", i);
            String where = "airports[" + i + "]";
            String code = requireString(obj, where, "code");
            String name = requireString(obj, where, "name");
            double x = requireNumber(obj, where, "x");
            double y = requireNumber(obj, where, "y");
            try {
                airports.add(new Airport(name, code, x, y));
            } catch (ValidationException e) {
                throw new DataFileException(where + ": " + e.getMessage());
            }
        }

        List<?> flightArray = requireArray(rootMap, "flights");
        List<Flight> flights = new ArrayList<>();
        for (int i = 0; i < flightArray.size(); i++) {
            Map<?, ?> obj = requireObject(flightArray, "flights", i);
            String where = "flights[" + i + "]";
            String from = requireString(obj, where, "from");
            String to = requireString(obj, where, "to");
            String departureText = requireString(obj, where, "departure");
            double duration = requireNumber(obj, where, "duration");
            try {
                int departure = TimeUtil.parseTimeOfDay(departureText);
                flights.add(new Flight(from, to, departure, (int) duration));
            } catch (ValidationException e) {
                throw new DataFileException(where + ": " + e.getMessage());
            }
        }

        return new LoadedData(airports, flights);
    }

    // ------------------------------------------------- type-checked getters

    private static List<?> requireArray(Map<?, ?> root, String key) throws DataFileException {
        Object value = root.get(key);
        if (value == null) {
            throw new DataFileException(
                    "The JSON file is missing the \"" + key + "\" array. The file must contain "
                    + "both \"airports\" and \"flights\" - check the format or choose another file.");
        }
        if (!(value instanceof List)) {
            throw new DataFileException(
                    "\"" + key + "\" in the JSON file must be an array (e.g. \"" + key + "\": [...]).");
        }
        return (List<?>) value;
    }

    private static Map<?, ?> requireObject(List<?> array, String key, int index)
            throws DataFileException {
        Object value = array.get(index);
        if (!(value instanceof Map)) {
            throw new DataFileException(
                    key + "[" + index + "] must be an object (e.g. {\"code\": ..., ...}).");
        }
        return (Map<?, ?>) value;
    }

    private static String requireString(Map<?, ?> obj, String where, String field)
            throws DataFileException {
        Object value = obj.get(field);
        if (value == null) {
            throw new DataFileException(where + ": missing required field \"" + field + "\".");
        }
        if (!(value instanceof String)) {
            throw new DataFileException(
                    where + ": field \"" + field + "\" must be a string (in quotes), but "
                    + value + " was found.");
        }
        return (String) value;
    }

    private static double requireNumber(Map<?, ?> obj, String where, String field)
            throws DataFileException {
        Object value = obj.get(field);
        if (value == null) {
            throw new DataFileException(where + ": missing required field \"" + field + "\".");
        }
        if (!(value instanceof Number)) {
            throw new DataFileException(
                    where + ": field \"" + field + "\" must be a number (without quotes), but \""
                    + value + "\" was found.");
        }
        return ((Number) value).doubleValue();
    }

    // -------------------------------------------------------------- saving

    public static String save(List<Airport> airports, List<Flight> flights) {
        Map<String, Object> root = new LinkedHashMap<>();

        List<Object> airportList = new ArrayList<>();
        for (Airport a : airports) {
            Map<String, Object> obj = new LinkedHashMap<>();
            obj.put("code", a.getCode());
            obj.put("name", a.getName());
            obj.put("x", a.getX());
            obj.put("y", a.getY());
            airportList.add(obj);
        }
        root.put("airports", airportList);

        List<Object> flightList = new ArrayList<>();
        for (Flight f : flights) {
            Map<String, Object> obj = new LinkedHashMap<>();
            obj.put("from", f.getFrom());
            obj.put("to", f.getTo());
            obj.put("departure", f.getDepartureText());
            obj.put("duration", f.getDurationMinutes());
            flightList.add(obj);
        }
        root.put("flights", flightList);

        return Json.write(root);
    }
}
