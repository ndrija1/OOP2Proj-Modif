package io;

import java.util.List;

import model.Airport;
import model.Flight;

/**
 * Simple value object returned by the file codecs: the airports and flights
 * read from one data file. The codecs guarantee that every element passed
 * model-level validation; cross-checks (duplicate codes, unknown airports)
 * are done again by {@link model.World#replaceAll} before the data is applied.
 */
public class LoadedData {

    public final List<Airport> airports;
    public final List<Flight> flights;

    public LoadedData(List<Airport> airports, List<Flight> flights) {
        this.airports = airports;
        this.flights = flights;
    }
}
