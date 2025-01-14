/*
 * $Id$
 *
 * Copyright (c) 2000-2003 by Rodney Kinney
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
package VASL.build.module.map.boardPicker;

import VASL.LOS.Map.Map;
import VASL.build.module.map.boardArchive.BoardColor;
import VASL.build.module.map.boardArchive.ColorSSRule;
import VASSAL.build.module.map.boardPicker.board.MapGrid;
import VASSAL.build.module.map.boardPicker.board.MapGrid.BadCoords;
import VASSAL.tools.DataArchive;
import VASSAL.tools.SequenceEncoder;
import VASSAL.tools.image.ImageUtils;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.StringTokenizer;

/**
 * Overlays of all types and sizes
 */
public class Overlay implements Cloneable {
    private String version = "0";
    protected String name = "";
    protected Image image;
    protected File overlayFile;
    public String hex1 = "", hex2 = "";
    private String origins;
    private boolean preserveelevation= false;
    java.util.Map<Integer, Integer> mappings;
    protected Rectangle boundaries = new Rectangle();
    // boundaries are in local coordinates of the parent board
    // i.e., not accounting for cropping and reversal
    protected ASLBoard board;
    protected DataArchive archive;

    public Overlay() {
    }

    public String getVersion() {
        return version;
    }

    public Overlay(String ovr, ASLBoard board, File overlayDir) throws IOException, BoardException {
        StringTokenizer st = new StringTokenizer(ovr, "\t");
        if (st.countTokens() >= 3) {
            name = st.nextToken();
            hex1 = st.nextToken();
            hex2 = st.nextToken();
            if (st.hasMoreTokens()) {
                preserveelevation= Boolean.parseBoolean(st.nextToken());
            }
        }
        this.board = board;
        overlayFile = new File(overlayDir, archiveName());

        archive = new DataArchive(overlayFile.getPath(), "");

        readData();
        transform(preserveelevation);
        try {
            setBounds();
        } catch (BadCoords e) {
            throw new BoardException(e.getMessage());
        }
        check();
    }
    public Overlay(String ovr, File overlayDir) throws IOException, BoardException {
        StringTokenizer st = new StringTokenizer(ovr, "\t");
        if (st.countTokens() >= 3) {
            name = st.nextToken();
            hex1 = st.nextToken();
            hex2 = st.nextToken();
            if (st.hasMoreTokens()) {
                preserveelevation=Boolean.parseBoolean(st.nextToken());
            }
        }

        overlayFile = new File(overlayDir, archiveName());

        archive = new DataArchive(overlayFile.getPath(), "");
        readData();
        transform(preserveelevation);
    }
    public SSRFilter getTerrain() {
        SSRFilter terrain = board.getTerrain();
        if (terrain == null) {
            return null;
        }

        boolean hasOwnParameters = false;
        try {
            hasOwnParameters = archive.contains("colors");
        } catch (IOException ignore) {
        }

        try {
            hasOwnParameters = archive.contains("colorSSR");
        } catch (IOException ignore) {
        }

        try {
            hasOwnParameters = archive.contains("BoardMetadata.xml");
        } catch (IOException ignore) {
        }

        if (hasOwnParameters) {
            try {
                terrain = new SSRFilter(terrain.toString(), overlayFile, board);
            } catch (BoardException e) {
                e.printStackTrace();
                terrain = null;
            }
        }
        return terrain;
    }

    public Image getImage() {
        if (image == null) {
            image = loadImage();
        }
        return image;
    }
    public boolean getPreserveElevation(){
        return preserveelevation;
    }
    protected Image loadImage() {
        Image im = null;
        char c = getOrientation();
        if (isSingleHex()) {
            c = 'a';
        }

        try (InputStream in = archive.getInputStream(fileName(name + c))){
            im = ImageIO.read(new MemoryCacheImageInputStream(in));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return im;
    }
    public void setImage(BufferedImage bi){
        image = (Image) bi;
    }

    public String getName() {
        return name;
    }

    /**
     * The boundaries of this overlay's image, in local coordinates on the parent board
     */
    public Rectangle bounds() {
        return boundaries;
    }

    private void readData() throws IOException {
        // DR - these values are immediately overwritten below so I assume they are created on a "just-in-case" the try-catch fails
        //ToDo - first tests indicate this routine is not necessary; do more testing and if possible remove
        origins = getDefaultOriginList(name);

        try (InputStream in = archive.getInputStream("data")) {
            BufferedReader file = new BufferedReader(new InputStreamReader(in));
            String s;
            while ((s = file.readLine()) != null) {
                StringTokenizer st = new StringTokenizer(s);
                if (st.countTokens() < 2) {
                    continue;
                }
                String s1 = st.nextToken();
                if (s1.equalsIgnoreCase(name)) {
                    origins = s.substring(name.length()).trim();
                } else if (s1.equalsIgnoreCase("version")) {
                    version = st.nextToken();
                } else if (s1.equalsIgnoreCase("placeAt")) {
                    hex1 = st.nextToken();
                    hex2 = st.nextToken();
                }
            }
        }
    }

    public File getFile() {
        return overlayFile;
    }

    protected void setBounds() throws IOException, BoardException, MapGrid.BadCoords {
        char c = getOrientation();
        boundaries.setLocation(board.getGrid().getLocation(hex1));
        boundaries.translate(-offset(c, board).x, -offset(c, board).y);
        final String fn = fileName(name + c);
        try (InputStream in = archive.getInputStream(fn)) {
            boundaries.setSize(ImageUtils.getImageSize(fn, in));
        }
    }

    private String fileName(String name) {
        return "ovr" + equivalence(name) + ".gif";
    }

    public String archiveName() {
        return archiveName(name + 'a');
    }

    public static String archiveName(String name) {
        return "ovr" + trimName(equivalence(name)).toUpperCase();
    }

    private static String equivalence(String file) {
        if (file.startsWith("be2") || file.startsWith("be3"))
            return "be1" + file.substring(3);
        else if (file.startsWith("be5") || file.startsWith("be6"))
            return "be4" + file.substring(3);
        else if (file.startsWith("oc2"))
            return "oc1" + file.substring(3);
        else if (file.startsWith("oc4"))
            return "oc3" + file.substring(3);
        else
            return file;
    }

    private static String trimName(String sin) {
        if (sin.charAt(0) <= '9' && sin.charAt(0) >= '0')
            return sin.substring(0, sin.length() - 1);
        String s = sin.substring(0, sin.length() - 1);
        int n = s.length() - 1;
        while (s.charAt(n) <= '9' && s.charAt(n) >= '0' && n >= 0)
            --n;
        if (n >= 0)
            s = s.substring(0, n + 1);
        return s;
    }

    protected char getOrientation() {
        if (hex1.equals(hex2))
            return 'a';
        int hex1X, hex1Y, hex2X, hex2Y;
        try {
            Point p = board.getGrid().getLocation(hex1);
            hex1X = p.x;
            hex1Y = p.y;
            p = board.getGrid().getLocation(hex2);
            hex2X = p.x;
            hex2Y = p.y;
        } catch (MapGrid.BadCoords bEx) {
            return 'a';
        }
        char c;
        if (hex1X == hex2X)
            c = hex1Y < hex2Y ? 'd' : 'a';
        else if (hex1X > hex2X)
            c = hex1Y > hex2Y ? 'f' : 'e';
        else
            c = hex1Y > hex2Y ? 'b' : 'c';
        return c;
    }

    private void check() throws BoardException {
        if (isSingleHex()) {
            if (!hex2.equals(hex1) && hex1.length() > 0 && hex2.length() > 0)
                throw new BoardException("Specify a single hex");
        } else if (hex1.length() == 0 || hex2.length() == 0 || hex1.equals(hex2)) {
            throw new BoardException("Specify two hexes");
        }
        try {
            Point p1 = board.getGrid().getLocation(hex1);
            Point p2 = board.getGrid().getLocation(hex2);
            int dx = board.getGrid().getLocation("B1").x - board.getGrid().getLocation("A1").x;
            int dy = board.getGrid().getLocation("B1").y - board.getGrid().getLocation("B0").y;
            if (Math.abs(p2.x - p1.x) > 1.1 * dx || Math.abs(p2.y - p1.y) > 1.1 * dy)
                throw new BoardException("Illegal coordinates");
            char c = getOrientation();
            offset(c, board);
            try {
                if (!archive.contains(fileName(name + c))) {
                    throw new IOException();
                }
            } catch (IOException ex) {
                throw new BoardException("Overlay not found in " + overlayFile.getPath());
            }
        } catch (MapGrid.BadCoords err) {
            throw new BoardException(err.getMessage());
        }
    }

    Point offset(char orient, ASLBoard board) throws BoardException {
        try {
            SequenceEncoder.Decoder st = new SequenceEncoder.Decoder(origins, ';');
            int n = orient - 'a' + 1;
            String origin = "";
            while (n-- > 0) {
                origin = st.nextToken();
            }
            // hack-o-potomous for boards 1b - 9b (with some room to grow) which use a different hex grid
            String[] boards = {"1b", "2b", "3b", "4b", "5b", "6b", "7b", "8b", "9b", "10b", "11b", "12b", "13b", "14b", "15b", "16b", "17b", "18b", "19b", "20b", "21", "22b"};
            if (Arrays.asList(boards).contains(board.getName())) {

                char[] temp = origin.toCharArray();
                temp[0] += 16;
                origin = String.valueOf(temp);
            }
            // ...and again for BFP boards
            String[] moreBoards = {"BFPDW1b", "BFPDW2b", "BFPDW3b", "BFPDW4b", "BFPDW5b", "BFPDW6b", "BFPDW7b"};
            if (Arrays.asList(moreBoards).contains(board.getName())) {

                try {
                    int row = Integer.parseInt(origin.substring(1));
                    origin = origin.substring(0,1) + String.valueOf(row + 10);
                } catch (Exception e) {
                    // bury
                }
            }


            return board.getGrid().getLocation(origin);
        } catch (Exception e) {
            throw new BoardException("Illegal orientation \'" + orient + "\' for overlay " + name);
        }
    /*
     * Orientations are:
     *
     * 2 1 : a
     *
     * 2 1 : b
     *
     * 1 2 : c
     *
     * 1 2 : d
     *
     * 1 2 : e
     *
     * 2 1 : f
     *
     */
    }

    /*
    * DR - See comments in readdata()
    * this method has not been updated since 2009 and certainly does not cover all current overlays
    * if further testing indicates that it can be removed, then I would be in favour of doing so
    *
    * DR - it appears that the purpose of the values contained in these sequences is to permit the calculation of the x,y difference between the top left pixel
    * of the overlay image (including the transparent part) and the centre of the hex1 value entered in the boardpicker Add Overlay dialog. The values in the
    * sequence represent the hex whose center dot is the same distance (in x,y)  from 0,0 on the mapboard as the top left pixel of the overlay is from the centre
    * dot of the "1" hex of the overlay. This is then used to determine the placement point for the overlay on the mapboard.
     */
    private String getDefaultOriginList(String ovr) {
        String o;
        if ("st1".equals(ovr))
            o = "b7;g4;g3;d5;h3;i4";
        else if ("st2".equals(ovr))
            o = "d6;c5;g2;g4;g6;h4";
        else if ("st3".equals(ovr))
            o = "e8;c6;g1;i3;i7;j4";
        else if ("1".equals(ovr))
            o = "b2; ; ;l8; ; ";
        else if ("2".equals(ovr))
            o = "b2; ; ;l8; ; ";
        else if ("3".equals(ovr))
            o = "b2; ; ;l8; ; ";
        else if ("4".equals(ovr))
            o = "b0; ; ;j10; ; ";
        else if ("5".equals(ovr))
            o = "c2; ; ;g9; ; ";
        else if ("d1".equals(ovr))
            o = "b3;c2;e4;f4;e6;c4";
        else if ("d2".equals(ovr))
            o = "b3;b2;d1;f3;f4;d5";
        else if ("d3".equals(ovr))
            o = "b4;b2;b1;b1;e1;e3";
        else if ("d4".equals(ovr))
            o = "b2;b1;c1;e2;e3;c4";
        else if ("d5".equals(ovr))
            o = "b3;d1;h2;h5;h7;d6";
        else if ("d6".equals(ovr))
            o = "b3;d2;f3;h3;f4;d3";
        else if ("e1".equals(ovr))
            o = "a1; ; ;gg10; ; ";
        else if ("h1".equals(ovr))
            o = "b4;f1;c7;j6;h9;i4";
        else if ("h2".equals(ovr))
            o = "c5;d3;f3;e5;f5;f1";
        else if ("h3".equals(ovr))
            o = "b3;b1;e1;f1;f3;c4";
        else if ("h4".equals(ovr))
            o = "d6;h3;h5;d6;h4;f5";
        else if ("h5".equals(ovr))
            o = "c6;e3;f3;e6;i5;f5";
        else if ("h6".equals(ovr))
            o = "b4;d2;e2;f4;h3;e5";
        else if ("s1".equals(ovr))
            o = "b3;d1;e2;d3;f2;c4";
        else if ("s2".equals(ovr))
            o = "b2;b1;c1;d1;d2;c3";
        else if ("s3".equals(ovr))
            o = "c3;e2;e3;e4;e3;c4";
        else if ("s4".equals(ovr))
            o = "b3;c2;d1;d2;e2;d2";
        else if ("s5".equals(ovr))
            o = "c5;e5;e2;g4;e4;g3";
        else if ("s6".equals(ovr))
            o = "b3;b2;b2;d1;d2;d2";
        else if ("s7".equals(ovr))
            o = "b3;b1;b1;d1;f1;d3";
        else if ("s8".equals(ovr))
            o = "b3;b2;d5;f3;f4;d1";
        else if ("sd1".equals(ovr))
            o = "b4;d2;e3;d3;f4;e4";
        else if ("sd2".equals(ovr))
            o = "c2;f1;e4;g5;f4;c5";
        else if ("sd3".equals(ovr))
            o = "b3;b2;d1;f2;f3;d4";
        else if ("sd4".equals(ovr))
            o = "b3;b1;e2;f2;f4;c4";
        else if ("sd5".equals(ovr))
            o = "c6;f2;f4;e5;h4;f4";
        else if ("sd6".equals(ovr))
            o = "c6;b3;d1;g2;h4;f4";
        else if ("sd7".equals(ovr))
            o = "b2;b1;e2;f2;f3;c3";
        else if ("sd8".equals(ovr))
            o = "b4;c3;g1;f4;g5;e4";
        else if ("w1".equals(ovr))
            o = "e6;c6;h1;g4;e6;h3";
        else if ("w2".equals(ovr))
            o = "h5;g6;d6;f4;i1;f5";
        else if ("w3".equals(ovr))
            o = "b7;c4;h1;f4;i6;h4";
        else if ("w4".equals(ovr))
            o = "b6;d2;h2;h5;j6;f6";
        else if ("ef1".equals(ovr))
            o = "e3;e3;b4;e3;e3;c4";
        else if ("ef2".equals(ovr))
            o = "b3;e2;f2;d4;e4;d3";
        else if ("ef3".equals(ovr))
            o = "c5;d3;e2;e4;g3;f5";
        else if ("be1".equals(ovr) || "be2".equals(ovr) || "be3".equals(ovr) || "be7".equals(ovr))
            o = "g11;e10;b5;b1;k1;n5";
        else if ("be4".equals(ovr) || "be5".equals(ovr) || "be6".equals(ovr))
            o = "m7;i12;b9;b1;g1;n3";
        else if ("oc1".equals(ovr) || "oc2".equals(ovr))
            o = "m13;h15;d9;b1;o1;s7";
        else if ("oc3".equals(ovr) || "oc4".equals(ovr))
            o = "m14;h15;c9;b1;o1;t7";
        else if ("x20".equals(ovr) || "x21".equals(ovr))
            o = "c2;f2;f4;c5;c5;c2";
        else if ("x23".equals(ovr) || "x24".equals(ovr))
            o = "b2;d1;c2;e2;d3;d4";
        else if ("rr1".equals(ovr) || "rr2".equals(ovr) || "rr8".equals(ovr))
            o = "b5;h4;h4;b7;f5;f3";
        else if ("rr3".equals(ovr) || "rr11".equals(ovr))
            o = "c5;d4;d2;c2;g3;g4";
        else if ("rr4".equals(ovr) || "rr12".equals(ovr))
            o = "c4;d4;d2;c3;e3;e4";
        else if ("rr7".equals(ovr))
            o = "b7;f4;f3;b5;h3;h4";
        else if ("rr13".equals(ovr))
            o = "c6;d2;g2;g5;h6;e7";
        else if ("rr14".equals(ovr))
            o = "b6;f3;g3;c5;h3;g4";
        else if ("re1".equals(ovr))
            o = "c3;b1;k1;l5;d9;d7";
        else if ("re2".equals(ovr))
            o = "c5;b1;k2;l7;f9;e9";
        else if ("re3".equals(ovr))
            o = "h5;h6;c7;d6;e3;d3";
        else if ("re4".equals(ovr))
            o = "b4;b1;c1;e1;f3;d4";
        else if ("re5".equals(ovr))
            o = "b4;b1;c1;e1;f3;d4";
        else if ("hd5".equals(ovr) || "hd6".equals(ovr) || "hd7".equals(ovr))
            o = "b4;b2;b1;c1;d1;d2";
        else if ("hd8".equals(ovr))
            o = "b3;c2;d1;d2;d2;d2;";
        else if ("hd9".equals(ovr) || "hd10".equals(ovr))
            o = "b4;b3;b1;c1;f1;f3";
        else if ("hd11".equals(ovr))
            o = "d6;b5;b1;c1;g2;h4";
        else if ("ow1".equals(ovr))
            o = "c3;c3;d2;e3;d3;d3";
        else if (ovr.startsWith("wd") || ovr.startsWith("g") || ovr.startsWith("m") || ovr.startsWith("p") || ovr.startsWith("x") || ovr.startsWith("b")
                || ovr.startsWith("rp") || ovr.startsWith("rr") || ovr.startsWith("hd") || ovr.startsWith("o"))
            o = "b2;b1;c1;d1;d2;c3";
        else
            o = null;
        return o;
    }

    private boolean isSingleHex() {
        try {
            return archive.contains(fileName(name + 'a')) &&
                    !archive.contains(fileName(name + 'd'));
        } catch (IOException e) {
            return false;
        }
    }

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public String toString() {
        return "OVR\t" + name + "\t" + hex1 + "\t" + hex2;
    }

    public ASLBoard getBoard() {
        return board;
    }

    public DataArchive getDataArchive() {
        return archive;
    }
    public void transform(boolean preserveelevation){
        if(!preserveelevation){return;}
        Map losmap;
        Integer hex1elevation;
        try {
            losmap= board.getVASLBoardArchive().getLOSData("Normal", false);
            if (losmap==null){return;}
            hex1elevation = losmap.getHex(hex1).getBaseLevelofHex();
        } catch (Exception e) {
            return;
        }

        if (hex1elevation != 0){
            Image i = getImage();
            // get the image as a buffered image
            BufferedImage bi = new BufferedImage(i.getWidth(null), i.getHeight(null), BufferedImage.TYPE_INT_ARGB);
            Rectangle ovrRec = bounds();
            Graphics2D bGr = bi.createGraphics();
            bGr.drawImage(i, 0, 0, null);
            bGr.dispose();
            String addnewelev = (hex1elevation == -1 ? "_1" : Integer.toString(hex1elevation));
            String elevationtransform = "Level0ToLevel"+addnewelev;
            dotransform(elevationtransform, bi);
            setImage(bi);
        }

    }
    private int colorToInt(Color color) {
        return (color.getRed() << 16) + (color.getGreen() << 8) + color.getBlue();
    }
    public void dotransform(String elevationtransform, BufferedImage bi){
        // load the color mappings
        java.util.Map<String, Integer> colorValues = new HashMap<String, Integer>();
        mappings = new HashMap<Integer, Integer>();
        for (BoardColor boardColor : board.getVASLBoardArchive().getBoardColors().values()) {
            colorValues.put(boardColor.getVASLColorName(), colorToInt(boardColor.getColor()));
        }


        for (java.util.Map.Entry<String, ColorSSRule> entry : board.getVASLBoardArchive().getColorSSRules().entrySet()) {

            String ruleName = entry.getKey();

            if (elevationtransform.equals(ruleName)) {

                ColorSSRule colorSSRule = board.getVASLBoardArchive().getColorSSRules().get(ruleName);

                for (java.util.Map.Entry<String, String> entry1 : colorSSRule.getColorMaps().entrySet()) {

                    int fromColor = 0;
                    int toColor = 0;
                    try {
                        fromColor = colorValues.get(entry1.getKey());
                        toColor = colorValues.get(entry1.getValue());

                        if (fromColor >= 0 && toColor >= 0 && fromColor != toColor) {

                            if (!mappings.containsKey(fromColor)) {
                                mappings.put(fromColor, toColor);
                            }

                            // Also apply this mapping to previous mappings
                            if (mappings.containsValue(fromColor)) {

                                for (java.util.Map.Entry<Integer, Integer> e : mappings.entrySet()) {
                                    if (e.getValue() == fromColor)
                                        e.setValue(toColor);
                                }
                            }
                        }
                    } catch (Exception e) {
                        if (colorValues.get(entry1.getKey()) == null){
                            //logger.warn("Board " + board.getName() + " missing color entry in color SSR mapping: " + entry1.getKey());
                        }
                        if (colorValues.get(entry1.getValue()) == null){
                            //logger.warn("Board " + board.getName() + " missing color entry in color SSR mapping: " + entry1.getValue());
                        }
                    }
                }
            }
        }

        if (!mappings.isEmpty()) {
            final int h = bi.getHeight();
            final int[] row = new int[bi.getWidth()];
            for (int y = 0; y < h; ++y) {
                bi.getRGB(0, y, row.length, 1, row, 0, row.length);
                for (int x = 0; x < row.length; ++x) {
                    row[x] = filterRGB(x, y, row[x]);
                }
                bi.setRGB(0, y, row.length, 1, row, 0, row.length);
            }
        }
    }
    public int filterRGB(int x, int y, int rgb) {
        return ((0xff000000 & rgb) | mapColor(rgb & 0xffffff));
    }
    private int mapColor(int rgb) {
        int rval = rgb;
        Integer mappedValue = mappings.get(rgb);
        if (mappedValue != null) {
            rval = mappedValue;
        }
        return rval;
    }
}
