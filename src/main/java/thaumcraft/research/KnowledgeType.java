package thaumcraft.research;

/** Raw units per complete knowledge point in TC6 BETA26. */
public enum KnowledgeType {
    OBSERVATION(16), THEORY(32);

    private final int units;

    KnowledgeType(int units) { this.units = units; }

    public int units() { return units; }
}
