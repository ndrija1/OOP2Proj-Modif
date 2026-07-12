package model;

import java.util.*;

import util.Log;

/**
 * The central data store: airports + flights + the rules that connect them
 * (unique codes, flights reference existing airports). All GUI parts observe
 * it through {@link Listener} instead of talking to each other. Mutations
 * happen on the EDT only, so no synchronization is needed here; the engine
 * snapshots the data when a simulation starts.
 */
public class World {

    /** Implemented by anyone who wants to know when the data changes. */
    public interface Listener {
        void worldChanged();
    }

    private final List<Airport> airports = new ArrayList<>();
    private final List<Flight> flights = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();

    // ------------------------------------------------------------------ read

    public List<Airport> getAirports() {
        return Collections.unmodifiableList(airports);
    }

    public List<Flight> getFlights() {
        return Collections.unmodifiableList(flights);
    }

    public Airport findAirport(String code) {
        for(Airport a:airports){
            if(a.getCode().equals(code)){
                return a;
            }
        }
        return null;
    }

    public int countFlightsUsing(String airportCode) {
        int n=0;
        for(Flight f:flights){
            if(f.getFrom().equals(airportCode)||f.getTo().equals(airportCode)){
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- modify

    public void addAirport(Airport airport) throws ValidationException {
        Airport existing=findAirport(airport.getCode());
        if(existing!=null){
            throw new ValidationException("Airport already exists");
        }
        airports.add(airport);
        fireChanged();
    }

    public int removeAirport(int index) {
        Airport removed=airports.remove(index);
        int before=flights.size();
        flights.removeIf(flight -> flight.getFrom().equals(removed.getCode())||flight.getTo().equals(removed.getCode()));
        priorityFlights.retainAll(flights); // [MOD X7]
        int removedFlights = before - flights.size();
        Log.debug("World", "removed airport " + removed.getCode()
                + " and " + removedFlights + " flight(s) using it");
        fireChanged();
        return removedFlights;
    }

    public void addFlight(Flight flight) throws ValidationException {
        requireAirportExists(flight.getFrom(),"origin");
        requireAirportExists(flight.getTo(),"destination");
        flights.add(flight);
        Log.debug("World", "added flight " + flight);
        fireChanged();
    }

    public void removeFlight(int index) {
        priorityFlights.remove(flights.get(index));
        flights.remove(index);
        fireChanged();
    }

    public void replaceAll(List<Airport> newAirports, List<Flight> newFlights)
            throws ValidationException {
        List<String>seen=new ArrayList<>();
        for(Airport a:newAirports){
            if(seen.contains(a.getCode())){
                throw new ValidationException("Airport already exists");
            }
            seen.add(a.getCode());
        }
        for(Flight f:newFlights){
            if(!seen.contains(f.getFrom())||!seen.contains(f.getTo())){
                throw new ValidationException("Flight " + f.getLabel()
                        + " references an airport that is not defined in the loaded data.");            }
        }
        airports.clear();
        airports.addAll(newAirports);
        flights.clear();
        flights.addAll(newFlights);
        priorityFlights.clear(); // [MOD X7] a fresh file starts with no priorities
        fireChanged();
    }

    // -------------------------------------------------------------- listeners

    public void addListener(Listener l) {
        listeners.add(l);
    }

    private void fireChanged() {
        for (Listener l : new ArrayList<>(listeners)) {
            l.worldChanged();
        }
    }
    private void requireAirportExists(String code, String to) throws ValidationException {
        if(findAirport(code)==null){
            throw new ValidationException("Airport does not exist");
        }
    }

    // ===== [MOD X7] priority flights ========================================
    // Runtime-only state (never persisted to files): a set of references to
    // flights currently in the world. No Mods gate here - the model just
    // stores facts; the GUI and the scheduler decide whether to use them.
    private final Set<Flight> priorityFlights = new HashSet<>();

    public void togglePriority(Flight f) {
        if (!priorityFlights.remove(f)) priorityFlights.add(f);
        Log.debug("World", "priority toggled for " + f);
        fireChanged();
    }

    public boolean isPriority(Flight f) {
        return priorityFlights.contains(f);
    }
    // ===== [end MOD X7] =====================================================
}
