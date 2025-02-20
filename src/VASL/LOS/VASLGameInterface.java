/*
 * Copyright (c) 2000-2003 by David Sullivan
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License (LGPL) as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, copies are available
 * at http://www.opensource.org.
 */
package VASL.LOS;

import VASL.LOS.Map.Bridge;
import VASL.LOS.Map.Hex;
import VASL.LOS.Map.Location;
import VASL.LOS.counters.*;
import VASL.build.module.ASLMap;
import VASL.counters.ASLProperties;
import VASSAL.build.GameModule;
import VASSAL.counters.GamePiece;
import VASSAL.counters.PieceIterator;
import VASSAL.counters.Properties;
import VASSAL.counters.Stack;

import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;

import static VASSAL.build.GameModule.getGameModule;

/**
 * This class provides access to VASL game information. E.g. the location and status of vehicles, counter terrain, etc.
 */
public class VASLGameInterface {

    public ASLMap gameMap;
    VASL.LOS.Map.Map LOSMap;

    // the LOS counter rules from the shared metadata file
    LinkedHashMap<String, CounterMetadata> counterMetadata;

    /* the DBCounterType marker
    unit = counter is a unit and can spot
    global = global counters such as fire markers, wrecks, terrain, etc.
             because global counters are always visible they do not need an owner
    common = All other counters

    Type    Can spot?   Visible to opponent?
    ----    ---------   --------------------
    unit    Yes         If in LOS
    global  No          Always
    common  No          If in LOS
    none    No          treated as common
    */
    public final static String DB_COUNTER_TYPE_MARKER_KEY = "DBCounterType";
    public final static String DB_UNIT_TYPE = "unit";
    public final static String COUNTER_TYPE_MARKER_KEY = "CounterType";
    public final static String UNIT_TYPE = "unit";
    public final static String DB_GLOBAL_TYPE = "global";
    public final static String DB_COMMON_TYPE = "common";

    // the counter lists
    protected HashMap<Hex, CounterMetadata> terrainList;
    protected HashMap<Hex, HashSet<Smoke>> smokeList;
    protected HashMap<Hex, HashSet<OBA>> OBAList;
    protected HashMap<Hex, HashSet<Wreck>> wreckList;
    protected HashMap<Hex, HashSet<Vehicle>> vehicleList;
    protected HashMap<Hex, CounterMetadata> hexsideList;

    // this is a list of all location counters - not those currently on the board
    protected static HashMap<String, LocationCounter> locationCounterList = null;

    // empty lists are used a lot so create them once
    protected final static HashSet<Vehicle> emptyVehicleList = new HashSet<Vehicle>(0);
    protected final static HashSet<OBA> emptyOBAList = new HashSet<OBA>(0);
    protected final static HashSet<Smoke> emptySmokeList = new HashSet<Smoke>(0);
    protected final static HashSet<Wreck> emptyWreckList = new HashSet<Wreck>(0);

	public VASLGameInterface(ASLMap GameMap, VASL.LOS.Map.Map LOSMap) {

        this.gameMap = GameMap;
        this.LOSMap = LOSMap;
        // change for losgui
        if (GameMap == null && LOSMap == null){return;}
        // get the counter metadata and location counters
        CounterMetadataFile counterMetadataFile = new CounterMetadataFile();
        counterMetadata =  counterMetadataFile.getMetadataElements();
        if (counterMetadata != null) {
            createLocationCounters();
        }
        else {
            final GameModule mod = getGameModule();
            mod.warn("CounterMetadata.xml information did not load properly. LOS results may be incorrect. If problem persits, likely a network error occurred. Please check your network connectivity and try again.");
        }
	}

    /**
     * Updates the game pieces, creating a snapshot of the game LOS counters
     */
    public void updatePieces() {

        printCounterLocations();

        // reset the counter lists
        smokeList = new HashMap<Hex, HashSet<Smoke>>();
        terrainList = new HashMap<Hex, CounterMetadata>();
        hexsideList = new HashMap<Hex, CounterMetadata>();
        OBAList = new HashMap<Hex, HashSet<OBA>>();
        wreckList = new HashMap<Hex, HashSet<Wreck>>();
        vehicleList = new HashMap<Hex, HashSet<Vehicle>>();

        // get all of the game pieces
        GamePiece[] p = gameMap.getPieces();

        // add each of the pieces
        for (GamePiece aP : p) {
            if (aP instanceof Stack) {
                for (PieceIterator pi = new PieceIterator(((Stack) aP).getPiecesIterator()); pi.hasMoreElements(); ) {
                    updatePiece(pi.nextPiece());
                }
            } else {
                updatePiece(aP);
            }
        }
    }

    /**
     * Updates a single piece creating a counter object if necessary
     * @param piece the piece
     */
    private void updatePiece(GamePiece piece) {

        // determine what hex and location the piece is in
        Point p = piece.getPosition();
        p.translate(-gameMap.getEdgeBuffer().width, -gameMap.getEdgeBuffer().height);

        if (!LOSMap.onMap(p.x, p.y)) return;
        Hex h = LOSMap.gridToHex(p.x, p.y);

        String name = piece.getName().trim();
        // need to take out any label info for next test
        name = (parsepiecename(name)).trim();
        // ignore any piece whose name is prefixed by an ignore-type counter
        for(CounterMetadata counter: counterMetadata.values()){

            if(counter.getType() == CounterMetadata.CounterType.IGNORE && name.startsWith(counter.getName())){
                return;
            }
        }

        // add the piece
        if (!Boolean.TRUE.equals(piece.getProperty(Properties.INVISIBLE_TO_ME))) {

            CounterMetadata counter = counterMetadata.get(name);
            if (counter == null) {
                if (name.contains("Hedge Overlay")) {
                    name = "Hedge Overlay";
                } else if (name.contains("Wall Overlay")) {
                    name = "Wall Overlay";
                } else if (name.contains("Bocage Overlay")) {
                    name = "Bocage Overlay";
                } else if (name.contains("Road")) {
                    name = "Road";    // Dirt/Paved has no los impact
                } else if (name.contains("Foot") || name.contains("Pontoon")) { // Foot/Pontoon have no los impact
                    name = "";
                } else if (name.contains("Bridge")) { // all Bridges are same for LOS
                    name = "Bridge";
                }
                counter = counterMetadata.get(name);
            }
            if(counter != null) {

                // add counter object to the appropriate list
                switch (counter.getType()) {
                    case OBA:
                        // ToDo Refactor after beta test
                        OBA oba = new OBA(counter.getName(), h, counter.getRotation(), counter.getIsBarrage());
                        // need to handle Smoke and WP FFE/Barrage by creating smoke "counter" in each hex
                        Hex smokehex = null; int hindrance = 0; int height = 0;
                        if(counter.getName().contains("Har")){
                            break;
                        }
                        if(counter.getName().contains("Sm ") || counter.getName().contains("WP")) {
                            if (oba.getisBarrage()) { //Barrage
                                // get four hexes from center hex on each side depending on rotation
                                String[] barragehexes = LOSMap.getBarrageHexes(oba);
                                for (String shex : barragehexes) {
                                    smokehex = LOSMap.getHex(shex);
                                    if (smokehex != null) {
                                        if (counter.getName().contains("+3")) {
                                            hindrance = 3;
                                            height = 2;
                                        } else if (counter.getName().contains("+1")) {
                                            hindrance = 1;
                                            height = 4;
                                        } else {
                                            hindrance = 2;
                                            height = (counter.getName().contains("Sm") ? 2 : 4);
                                        }
                                        Smoke smoke = new Smoke(counter.getName(), smokehex.getCenterLocation(), height, hindrance);
                                        addCounter(smokeList, smoke, smokehex);
                                    }
                                }
                            } else {  // Smoke
                                // get 7 hex blast area
                                String[] ffehexes = LOSMap.getAllAdjacentHexes(oba.getHex());
                                for (String shex : ffehexes) {
                                    smokehex = LOSMap.getHex(shex);
                                    if (smokehex != null) {
                                        if (counter.getName().contains("+3")) {
                                            hindrance = 3;
                                            height = 2;
                                        } else if (counter.getName().contains("+1")) {
                                            hindrance = 1;
                                            height = 4;
                                        } else {
                                            hindrance = 2;
                                            height = (counter.getName().contains("Sm") ? 2 : 4);
                                        }
                                        Smoke smoke = new Smoke(counter.getName(), smokehex.getCenterLocation(), height, hindrance);
                                        addCounter(smokeList, smoke, smokehex);
                                    }
                                }
                                if(counter.getName().contains("Naval")) {
                                    // add two extra hexes if NOBA
                                    Hex[] nobahexes = getExtraNobaHexes(counter.getRotation(), ffehexes);
                                    for (int x = 0; x < 2; x++) {
                                        if (nobahexes[x] != null) {
                                            Smoke smoke = new Smoke(counter.getName(), nobahexes[x].getCenterLocation(), height, hindrance);
                                            addCounter(smokeList, smoke, nobahexes[x]);
                                        }
                                    }
                                }
                            }
                        }
                        // Handle NOBA Bombardment and FFE
                        if (counter.getName().contains("Bombardment") && (counter.getName().contains("Smoke") || counter.getName().contains("Dispersed"))) {
                            height = 2;
                            int nobaradius = (counter.getName().contains("Small") ? 2 : 3);
                            String [] nobaradisuhexes = LOSMap.getAllHexesInRadiusOf(oba.getHex(), nobaradius);
                            for (String nobahex : nobaradisuhexes) {
                                        smokehex = LOSMap.getHex(nobahex);
                                        if (smokehex != null) {
                                            if (counter.getName().contains("Dispersed")) {
                                                hindrance = 2;
                                            }
                                            else {
                                                hindrance = 3;
                                            }
                                            Smoke smoke = new Smoke(counter.getName(), smokehex.getCenterLocation(), height, hindrance);
                                            addCounter(smokeList, smoke, smokehex);
                                        }
                            }
                        }

                        else {

                            addCounter(OBAList, oba, h);
                        }
                        break;
                    case TERRAIN:
                        // we assume there is only one terrain-type counter in a hex
                        terrainList.put(h, counter);
                        break;
                    case HEXSIDE:
                        hexsideList.put(h, counter);

                        break;
                    case ENTRENCHMENT:
                        // we assume there is only one terrain-type counter in a hex
                        terrainList.put(h, counter);
                        // now create a "location" in the hex
                        createLocationinHexForEntrenchments(h);
                        break;
                    case BRIDGE:
                        // we assume there is only one terrain-type counter in a hex
                        terrainList.put(h, counter);
                        // now create a "location" in the hex
                        createLocationinHexForBridges(h);
                        break;
                    case CREST:
                        // we assume there is only one terrain-type counter in a hex
                        terrainList.put(h, counter);
                        // now create a "location" in the hex
                        createLocationinHexForCrest(h);
                        break;
                    case SMOKE:
                        Smoke smoke = new Smoke(counter.getName(), h.getNearestLocation(p.x, p.y), counter.getHeight(), counter.getHindrance());
                        addCounter(smokeList, smoke, h);
                        break;

                    case WRECK:
                        // treat wrecks as vehicles for now
                        Vehicle vehicle = new Vehicle(name, h.getCenterLocation());
                        addCounter(vehicleList, vehicle, h);
                        break;
                }
            }

            // add vehicles
            //TODO: assuming all hindrance counters that have no rule are vehicles - not good
            else if(piece.getProperty(ASLProperties.HINDRANCE) != null && !Boolean.TRUE.equals(piece.getProperty(Properties.MOVED))){

                Vehicle v = new Vehicle(name, h.getNearestLocation(p.x, p.y));
                addCounter(vehicleList, v, h);
            }
        }
    }
    private String parsepiecename(String piecename) {
        // test for label info that must be removed
        int userStart = 0;
        int userEnd = 0;
        do {
            userStart = piecename.indexOf("(");
            userEnd = piecename.lastIndexOf(")");

            if (userStart != -1 && userEnd != -1) {
                if (userStart <= userEnd+1) {  //error trapping
                    final String deletestring = piecename.substring(userStart, userEnd + 1);
                    piecename = piecename.replace(deletestring, "");
                    userStart = 0;
                    userEnd = 0;
                } else {
                    userStart = -1; userEnd = -1;  //jump out without changing name
                }
            }
        } while (userStart != -1 && userEnd != -1);
        return piecename;
    }

    public Hex[] getExtraNobaHexes(int rotation, String[] ffehexes) {
        Hex[] nobahexes = new Hex[2]; Hex basehex = null;
        switch (rotation){
            case 1:
                basehex = LOSMap.getHex(ffehexes[1]);
                if (LOSMap.getAdjacentHex(basehex, 1) != null) {
                    nobahexes[0] = LOSMap.getAdjacentHex(basehex, 1);
                }
                basehex = LOSMap.getHex(ffehexes[4]);
                if (LOSMap.getAdjacentHex(basehex, 4) != null) {
                    nobahexes[1] = LOSMap.getAdjacentHex(basehex, 4);
                }
                break;
            case 2:
                basehex = LOSMap.getHex(ffehexes[2]);
                if (LOSMap.getAdjacentHex(basehex, 1) != null) {
                    nobahexes[0] = LOSMap.getAdjacentHex(basehex, 1);
                }
                basehex = LOSMap.getHex(ffehexes[5]);
                if (LOSMap.getAdjacentHex(basehex, 4) != null) {
                    nobahexes[1] = LOSMap.getAdjacentHex(basehex, 4);
                }
                break;
            case 3:
                basehex = LOSMap.getHex(ffehexes[2]);
                if (LOSMap.getAdjacentHex(basehex, 2) != null) {
                    nobahexes[0] = LOSMap.getAdjacentHex(basehex, 2);
                }
                basehex = LOSMap.getHex(ffehexes[5]);
                if (LOSMap.getAdjacentHex(basehex, 5) != null) {
                    nobahexes[1] = LOSMap.getAdjacentHex(basehex, 5);
                }
                break;
            case 4:
                basehex = LOSMap.getHex(ffehexes[3]);
                if (LOSMap.getAdjacentHex(basehex, 2) != null) {
                    nobahexes[0] = LOSMap.getAdjacentHex(basehex, 2);
                }
                basehex = LOSMap.getHex(ffehexes[6]);
                if (LOSMap.getAdjacentHex(basehex, 5) != null) {
                    nobahexes[1] = LOSMap.getAdjacentHex(basehex, 5);
                }
                break;
            case 5:
                basehex = LOSMap.getHex(ffehexes[3]);
                if (LOSMap.getAdjacentHex(basehex, 3) != null) {
                    nobahexes[0] = LOSMap.getAdjacentHex(basehex, 3);
                }
                basehex = LOSMap.getHex(ffehexes[6]);
                if (LOSMap.getAdjacentHex(basehex, 0) != null) {
                    nobahexes[1] = LOSMap.getAdjacentHex(basehex, 0);
                }
                break;
            case 6:
                basehex = LOSMap.getHex(ffehexes[4]);
                if (LOSMap.getAdjacentHex(basehex, 3) != null) {
                    nobahexes[0] = LOSMap.getAdjacentHex(basehex, 3);
                }
                basehex = LOSMap.getHex(ffehexes[1]);
                if (LOSMap.getAdjacentHex(basehex, 0) != null) {
                    nobahexes[1] = LOSMap.getAdjacentHex(basehex, 0);
                }
                break;
            default:
        }
        return nobahexes;
    }

    /**
     * Create a location counters for all location counters in the metadata
     */
    private void createLocationCounters(){

        // only do this once
        if(locationCounterList == null){

            locationCounterList = new HashMap<String, LocationCounter>();

            for(CounterMetadata c: counterMetadata.values()) {

                LocationCounter lc;
                switch (c.getType()) {
                    case BUILDING_LEVEL:
                        lc = new LocationCounter(c, LocationCounter.LocationCounterType.BUILDING_LEVEL);
                        locationCounterList.put(lc.getName(), lc);
                        break;

                    case ROOF:
                        lc = new LocationCounter(c, LocationCounter.LocationCounterType.ROOF);
                        locationCounterList.put(lc.getName(), lc);
                        break;

                    case ENTRENCHMENT:
                        lc = new LocationCounter(c, LocationCounter.LocationCounterType.ENTRENCHMENT);
                        locationCounterList.put(lc.getName(), lc);
                        break;

                    case CLIMB:
                        lc = new LocationCounter(c, LocationCounter.LocationCounterType.CLIMB);
                        locationCounterList.put(lc.getName(), lc);
                        break;

                    case CREST:
                        lc = new LocationCounter(c, LocationCounter.LocationCounterType.CREST);
                        locationCounterList.put(lc.getName(), lc);
                        break;

                    case BRIDGE:
                        lc = new LocationCounter(c, LocationCounter.LocationCounterType.BRIDGE);
                        locationCounterList.put(lc.getName(), lc);
                        break;

                }
            }
        }
    }

    /**
     * Adds a counter object to the counter list
     * @param hashMap the counter list
     * @param counter the object to add
     * @param h the hex location
     */
    private void addCounter(HashMap hashMap, Counter counter, Hex h) {

        @SuppressWarnings("unchecked")
        HashMap<Hex, HashSet<Counter>> hm = (HashMap<Hex, HashSet<Counter>>) hashMap;

        HashSet<Counter> list = hm.get(h);
        if(list == null) {

            list = new HashSet<Counter>();
            list.add(counter);
            hm.put(h, list);
        }
        else {
            list.add(counter);
        }
    }

    /**
     * Get the set of vehicle counters in the given hex
     * @param h the hex
     * @return the set of vehicle counters - if none an empty list is returned
     */
    public HashSet<Vehicle> getVehicles(Hex h){

		if(vehicleList != null && vehicleList.get(h) != null){
            return vehicleList.get(h);
        }
        else {
            return emptyVehicleList;
        }
	}

    /**
     * Gets smoke counters in the given hex
     * @param hex the hex
     * @return the set of smoke counters - if none an empty list is returned
     */
    public HashSet<Smoke> getSmoke(Hex hex) {

        if(smokeList != null && smokeList.get(hex) != null){
            return  smokeList.get(hex);
        }
        else {
            return emptySmokeList;
        }
    }

    /**
     * Get the terrain counter in the given hex
     * @param hex the hex
     * @return the terrain
     */
    public String getTerrain(Hex hex) {

        if(terrainList != null && terrainList.get(hex) != null) {
            return (terrainList.get(hex)).getTerrain();
        }
        return null;
    }
    public int getLevel(Hex hex) {
        // check there is a counter in the hex
        if(terrainList != null && terrainList.get(hex) != null) {
            // check the countermetadata has a level value
            if(terrainList.get(hex).getName().contains("Bridge")) {
                 return hex.getBridgeLocationAbsoluteLevel();
            }
            if (terrainList.get(hex).getLevel() != -99) {
                return (terrainList.get(hex)).getLevel();
            }
        }
        return -99;
    }

    public CounterMetadata getHexside(Hex hex) {

        if(hexsideList != null) {
            return hexsideList.get(hex);
        }
        return null;
    }
    /**
     * Get the OBA in the given hex
     * @param hex the hex
     * @return the set of OBA counters in the hex - if none an empty list is returned
     */
    public HashSet<OBA> getOBA(Hex hex){

        if(OBAList != null && OBAList.get(hex) != null){
            return OBAList.get(hex);
        }
        else {
            return emptyOBAList;
        }
    }

    /**
     * @return all OBA counters - if none an empty list is returned
     */
    public HashSet<OBA> getOBA(){
        if(OBAList != null && OBAList.size() > 0) {

            HashSet<OBA> OBACounters = new HashSet<OBA>();
            for(Hex hex : OBAList.keySet()) {
                OBACounters.addAll(getOBA(hex));
            }
            return OBACounters;
        }
        return emptyOBAList;
    }


    /**
     * Get the wrecks in the given hex
     * @param hex the hex
     * @return the set of wreck counters - if none an empty list is returned
     */
    public HashSet<Wreck> getWreck(Hex hex){

        if(wreckList != null && wreckList.get(hex) != null){
            return wreckList.get(hex);
        }
        else {
            return emptyWreckList;
        }
    }

    /**
     * Finds the location for the given piece
     * @param piece the piece
     * @return the location - null if error or none
     */
    public Location getLocation(GamePiece piece) {
        // this works with double blind viewer only as it requires a unit GamePiece to exist and assumes that hex locations have not been created
        // determine what hex and location the piece is in
        Point p = piece.getPosition();
        p.translate(-gameMap.getEdgeBuffer().width, -gameMap.getEdgeBuffer().height);

        if (gameMap == null || gameMap.getVASLMap() == null || !gameMap.getVASLMap().onMap(p.x, p.y)) {
            return null;
        }

        Hex h = LOSMap.gridToHex(p.x, p.y);
        Location location = h.getNearestLocation(p.x, p.y);
        LocationCounter locationCounter = getLocationCounterForPiece(piece);

        // if no location return the nearest location
        if (locationCounter == null) {
            return location;
        }

        // otherwise create a location appropriately
        else {

            switch (locationCounter.getType()) {
                case BUILDING_LEVEL:

                    // try to find the building location
                    Location l = location;
                    for(int x = 0; x < locationCounter.getLevel(); x++) {
                        if(l.getUpLocation() != null) {
                            l = l.getUpLocation();
                        }
                    }
                    if(l.getLevelInHex() == locationCounter.getLevel()) {
                        return l;
                    }

                    // otherwise create a new location
                    else {
                        Location newLocation = new Location(location);
                        newLocation.setLevelInHex(locationCounter.getLevel());
                        newLocation.setDownLocation(location);
                        return newLocation;
                    }

                case CLIMB:
                    break;

                case ENTRENCHMENT:
                    Location newLocation = new Location(location);
                    newLocation.setUpLocation(location);
                    newLocation.setTerrain(LOSMap.getTerrain("Foxholes")); // use foxholes as all fortifications have the same LOS rules
                    return newLocation;

                case ROOF:
                    break;

                case CREST:
                    break;
            }



            return location;
        }
    }


    /**
     * @param piece a game piece
     * @return true if piece has the unit marker set
     */
    public boolean isDBUnitCounter(GamePiece piece) {

        return isPropertySet(piece, DB_COUNTER_TYPE_MARKER_KEY, DB_UNIT_TYPE);
    }
    public boolean isUnitCounter(GamePiece piece) {

        return isPropertySet(piece, COUNTER_TYPE_MARKER_KEY, UNIT_TYPE);
    }

    /**
     * @param piece a game piece
     * @return true if piece has the fire marker set
     */
    public boolean isDBGlobalCounter(GamePiece piece) {

        return isPropertySet(piece, DB_COUNTER_TYPE_MARKER_KEY, DB_GLOBAL_TYPE);
    }

    /**
     * Checks if a given property is set to a given value
     * @param piece the game piece
     * @param key the property key
     * @param value the value
     * @return true if the property is set and equals the value, otherwise false
     */
    public boolean isPropertySet(GamePiece piece, String key, String value) {

        return piece.getProperty(key) != null && piece.getProperty(key).equals(value);
    }

    private void printCounterLocations(){

        //if (locationCounterList == null){return;}
        GamePiece[] p = gameMap.getPieces();
        for (GamePiece aP : p) {
            if (aP instanceof Stack) {
                for (PieceIterator pi = new PieceIterator(((Stack) aP).getPiecesIterator()); pi.hasMoreElements(); ) {
                    GamePiece p2 = pi.nextPiece();
                    LocationCounter location = getLocationCounterForPiece(p2);
/*
                    if(location == null) {
                        System.out.println(p2.getName() + " has no location counter piece");
                    }
                    else {
                        System.out.println(p2.getName()  + " is in location " + location.getName());
                    }
*/
                }
            } else {
                LocationCounter location = getLocationCounterForPiece(aP);
/*
                if(location == null) {
                    System.out.println(aP.getName() + " has no location counter piece");
                }
                else {
                    System.out.println(aP.getName()  + " is in location " + location.getName());
                }
*/
            }
        }
    }

    /**
     * Find the appropriate location counter for a piece
     * Location counters beneath a unit have preference over those above
     * @param piece the piece
     * @return the location counter for the given piece
     */
    private LocationCounter getLocationCounterForPiece (GamePiece piece) {

        Stack stack = piece.getParent();

        // single counter?
        if(stack == null || stack.getPieceCount() == 1) {
            return locationCounterList.get(piece.getName());
        }

        // search downward first - those location counters have precedence
        GamePiece somePiece = piece;
        LocationCounter locationCounter;
        while (somePiece != null) {

            locationCounter = locationCounterList.get(somePiece.getName());
            if(locationCounter != null && locationCounter.isAbovePiece()) {
                return locationCounter;
            }
            somePiece = stack.getPieceBeneath(somePiece);
        }

        // search the stack upward
        somePiece = piece;
        while (somePiece != null) {

            locationCounter = locationCounterList.get(somePiece.getName());
            if(locationCounter != null && locationCounter.isBelowPiece()) {
                return locationCounter;
            }
            somePiece = stack.getPieceAbove(somePiece);
        }
        return null;
    }
    /**
     * Create the appropriate location in the Hex for an entrenchment counter - don't set as different level
     * Entrenchment Locations are used by Map.checkHexsideTerrainRule to check LOS to entrenchments across hedge/wall
     * @param h the Hex
     * create the location and set relationship with centre location of Hex
     */
    private void createLocationinHexForEntrenchments(Hex h) {
        Location location = h.getCenterLocation();
        // create new location
        Location newLocation = new Location(location);
        // location "above" entrenchment is the center location
        newLocation.setUpLocation(location);
        // set terrain of new location
        newLocation.setTerrain(LOSMap.getTerrain("Foxholes")); // use foxholes as all fortifications have the same LOS rules
        // location 'below' center location is the entrenchment
        location.setDownLocation((newLocation));
    }
    private void createLocationinHexForBridges(Hex h) {
        Location location = null; Location newLocation = null;
        // first test if already exists
        if(h.hasBridge()){return;}
        location = h.getCenterLocation();
        // create new location
        newLocation = new Location(location);

        //ToDo clean this up and test all possibilities
        int bridgeLevelInHex = h.getBridgeLocationAbsoluteLevel() - newLocation.getHex().getBaseLevelofHex();
        newLocation.setLevelInHex(bridgeLevelInHex);
        // location "below" bridge is the center location
        newLocation.setDownLocation(location);
        // set terrain of new location
        newLocation.setTerrain(LOSMap.getTerrain("Stone Bridge")); // use foxholes as all fortifications have the same LOS rules
        // location 'above' center location is the bridge
        location.setUpLocation((newLocation));
        // add Bridge object to hex
        h.setBridge(new Bridge(newLocation.getTerrain(), newLocation.getLevelInHex(), newLocation,
                true, h.getHexCenter()));
    }
    private void createLocationinHexForCrest(Hex h) {
        Location location = h.getCenterLocation();
        // create new location
        Location newLocation = new Location(location);
        int crestLevelInHex = h.getBridgeLocationAbsoluteLevel() - newLocation.getHex().getBaseLevelofHex();
        newLocation.setLevelInHex(crestLevelInHex);
        // location "above" entrenchment is the center location
        newLocation.setDownLocation(location);
        // set terrain of new location
        newLocation.setTerrain(LOSMap.getTerrain("Crest")); // use foxholes as all fortifications have the same LOS rules
        // location 'below' center location is the entrenchment
        location.setUpLocation((newLocation));
    }
}
