package io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import model.Airport;
import model.Flight;
import util.Log;

/**
 * The single entry point the GUI uses for all file work. Chooses the codec
 * by file extension and translates low-level I/O failures into user friendly
 * {@link DataFileException}s.
 */
public final class FileService {

    private FileService() {} // static utility, never instantiated

    /** Loads airports and flights from a .csv or .json file. */
    public static LoadedData load(Path file) throws DataFileException {
        String name = file.getFileName().toString().toLowerCase();
        if (!Files.exists(file)) {
            throw new DataFileException(
                    "The file \"" + file + "\" does not exist. "
                    + "Check the path or choose another file.");
        }
        try {
            LoadedData data;
            if (name.endsWith(".csv")) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                data = CsvCodec.load(lines);
            } else if (name.endsWith(".json")) {
                String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                data = JsonCodec.load(text);
            } else {
                throw new DataFileException(
                        "Unsupported file type: \"" + file.getFileName() + "\". "
                        + "Only .csv and .json files can be loaded.");
            }
            Log.info("FileService", "loaded " + data.airports.size() + " airports and "
                    + data.flights.size() + " flights from " + file);
            return data;
        } catch (IOException e) {
            throw new DataFileException(
                    "The file \"" + file + "\" could not be read (" + e.getMessage() + "). "
                    + "Check that the file is accessible and not locked by another program.", e);
        }
    }

    /** Saves airports and flights to a .csv or .json file (chosen by extension). */
    public static void save(Path file, List<Airport> airports, List<Flight> flights)
            throws DataFileException {
        String name = file.getFileName().toString().toLowerCase();
        String content;
        if (name.endsWith(".csv")) {
            content = CsvCodec.save(airports, flights);
        } else if (name.endsWith(".json")) {
            content = JsonCodec.save(airports, flights);
        } else {
            throw new DataFileException(
                    "Unsupported file type: \"" + file.getFileName() + "\". "
                    + "Save the data as a .csv or .json file.");
        }
        try {
            Files.write(file, content.getBytes(StandardCharsets.UTF_8));
            Log.info("FileService", "saved " + airports.size() + " airports and "
                    + flights.size() + " flights to " + file);
        } catch (IOException e) {
            throw new DataFileException(
                    "The file \"" + file + "\" could not be written (" + e.getMessage() + "). "
                    + "Check that the location is writable and the file is not open elsewhere.", e);
        }
    }
}
