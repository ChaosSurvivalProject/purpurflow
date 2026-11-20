package org.bukkit.configuration.file;

/** 编译用最小桩：仅覆盖仓库实际调用的方法。 */
public class FileConfiguration {
    public boolean getBoolean(String k, boolean d) { return d; }
    public int getInt(String k, int d) { return d; }
    public long getLong(String k, long d) { return d; }
    public java.util.List<Integer> getIntegerList(String k) { return java.util.List.of(); }
}
