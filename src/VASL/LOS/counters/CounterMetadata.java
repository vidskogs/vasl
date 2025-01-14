/*
 * $Id: LOSCounterRule 2/4/14 davidsullivan1 $
 *
 * Copyright (c) 2013 by David Sullivan
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
package VASL.LOS.counters;

/**
 * This class contains the metadata for a single VASL game piece
 */
public class CounterMetadata {

    /*
        name = game piece name (i.e. piece.getName())
        hindrance = # of hindrances/hex
        height = height of LOS hindrance
        terrain = map terrain type
        level - denotes location level
        position - for location denotes if pieces above/below the counter are in the location.
        coverArch - covered arch of location
        rotation - used only for Barrage, rotation of Barrage counter
        isBarrange - used for OBA/Barrage

     */
    private String name;
    private String terrain;
    private int height;
    private int hindrance;
    private CounterType type;
    private int level;
    private String position;
    private int coverArch;
    private int rotation;
    private boolean isBarrage;

    public static enum CounterType {SMOKE, WRECK, OBA, TERRAIN, IGNORE, BUILDING_LEVEL, CREST, ROOF, ENTRENCHMENT, CLIMB, BRIDGE, HEXSIDE}

    public CounterMetadata(String name, CounterType type) {
        this.name = name;
        this.type = type;
    }

    /**
     * @return the counter name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the terrain name (for terrain-type counters)
     */
    public String getTerrain() {
        return terrain;
    }

    /**
     * @return the height (for smoke-type counters)
     */
    public int getHeight() {
        return height;
    }

    /**
     * @return the hindrance amount (for smoke counters)
     */
    public int getHindrance() {
        return hindrance;
    }

    /**
     * @return the counter type
     */
    public CounterType getType() {
        return type;
    }

    /**
     * Set the terrain (for terrain-type counters)
     * @param terrain the terrain name
     */
    public void setTerrain(String terrain) {
        this.terrain = terrain;
    }

    /**
     * Set the smoke height (for smoke-type counter)
     * @param height the smoke height
     */
    public void setHeight(int height) {
        this.height = height;
    }

    /**
     * Set the smoke hindrance (for smoke-type counters)
     * @param hindrance the smoke hindrance level
     */
    public void setHindrance(int hindrance) {
        this.hindrance = hindrance;
    }

    /**
     * @return the location level
     */
    public int getLevel() {
        return level;
    }

    /**
     * Set the location level
     * @param level the level
     */
    public void setLevel(int level) {
        this.level = level;
    }

    /**
     * @return the piece covered arch
     */
    public int getCoverArch() {
        return coverArch -1;
    }  //counters use 1-6, code uses 0-5

    /**
     * Set the piece covered arch
     * @param coverArch the covered arch
     */
    public void setCoverArch(int coverArch) {
        this.coverArch = coverArch;
    }

    /**
     * @return the location position
     */
    public String getPosition() {
        return position;
    }

    /**
     * Set the location position
     * @param position the covered arch
     */
    public void setPosition(String position) {
        this.position = position;
    }

    /**
     * @return the rotation for Barrage/OBA
     */
    public int getRotation() {
        return rotation;
    }

    /**
     * Set the rotation for Barrage/OBA
     */
    public void setRotation(int rotation) {
        this.rotation = rotation;
    }

    /**
     * @return is Barrage
     */
    public boolean getIsBarrage() {
        return isBarrage;
    }

    /**
     * Set is Barrage
     */
    public void setIsBarrage(boolean isBarrage) {
        this.isBarrage = isBarrage;
    }

}
