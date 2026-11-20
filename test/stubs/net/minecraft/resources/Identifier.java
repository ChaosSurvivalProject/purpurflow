package net.minecraft.resources;

/** 编译用最小桩。 */
public final class Identifier {
    private final String namespace, path;
    private Identifier(String ns, String p) { namespace = ns; path = p; }
    public static Identifier fromNamespaceAndPath(String ns, String p) { return new Identifier(ns, p); }
    @Override public String toString() { return namespace + ":" + path; }
}
