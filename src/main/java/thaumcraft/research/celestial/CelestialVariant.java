package thaumcraft.research.celestial;

/** BETA26 ItemCelestialNotes metadata and texture suffixes. */
public enum CelestialVariant {
    SUN("sun"), STARS_1("stars_1"), STARS_2("stars_2"), STARS_3("stars_3"), STARS_4("stars_4"),
    MOON_1("moon_1"), MOON_2("moon_2"), MOON_3("moon_3"), MOON_4("moon_4"),
    MOON_5("moon_5"), MOON_6("moon_6"), MOON_7("moon_7"), MOON_8("moon_8");

    private final String suffix;
    CelestialVariant(String suffix) { this.suffix = suffix; }
    public int metadata() { return ordinal(); }
    public String suffix() { return suffix; }
    public String itemId() { return "celestial_notes_" + suffix; }
    public static CelestialVariant byMetadata(int metadata) {
        return metadata >= 0 && metadata < values().length ? values()[metadata] : null;
    }
}
