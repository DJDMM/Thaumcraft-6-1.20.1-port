package thaumcraft.api.aspects;

/** Brace blocks airborne access while the transport interface remains available. */
public interface IAspectSource extends IAspectContainer {
    boolean isBlocked();
}
