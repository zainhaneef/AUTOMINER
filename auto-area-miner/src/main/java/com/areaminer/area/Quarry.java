package com.areaminer.area;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/** An immutable axis-aligned selection. Layer 0 is the top layer (maxY). */
public final class Quarry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public final int minX, minY, minZ, maxX, maxY, maxZ;
    public final String dimension;

    public Quarry(BlockPos a, BlockPos b, String dimension) {
        this(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()), dimension);
    }

    private Quarry(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String dimension) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.dimension = dimension;
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public int layerOfY(int y) {
        return maxY - y;
    }

    public int yOfLayer(int layer) {
        return maxY - layer;
    }

    public int cellIndex(int x, int z) {
        return (x - minX) + (z - minZ) * sizeX();
    }

    public int cellX(int idx) {
        return minX + idx % sizeX();
    }

    public int cellZ(int idx) {
        return minZ + idx / sizeX();
    }

    public String describe() {
        return sizeX() + " x " + sizeY() + " x " + sizeZ() + " (" + String.format("%,d", volume()) + " blocks)";
    }

    // ---------------------------------------------------------------- persistence

    private static final class Saved {
        String dimension;
        int x1, y1, z1, x2, y2, z2;
    }

    private static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("areaminer").resolve("quarries");
    }

    public static boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{1,40}");
    }

    public void save(String name) throws IOException {
        Saved s = new Saved();
        s.dimension = dimension;
        s.x1 = minX;
        s.y1 = minY;
        s.z1 = minZ;
        s.x2 = maxX;
        s.y2 = maxY;
        s.z2 = maxZ;
        Files.createDirectories(dir());
        Files.writeString(dir().resolve(name + ".json"), GSON.toJson(s));
    }

    public static Quarry load(String name) throws IOException {
        Path f = dir().resolve(name + ".json");
        if (!Files.exists(f)) {
            throw new IOException("No saved quarry named '" + name + "'");
        }
        Saved s = GSON.fromJson(Files.readString(f), Saved.class);
        if (s == null || s.dimension == null) {
            throw new IOException("Saved quarry '" + name + "' is corrupt");
        }
        return new Quarry(new BlockPos(s.x1, s.y1, s.z1), new BlockPos(s.x2, s.y2, s.z2), s.dimension);
    }

    public static List<String> listSaved() {
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(dir())) {
            return out;
        }
        try (Stream<Path> st = Files.list(dir())) {
            st.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - 5))
                    .forEach(out::add);
        } catch (IOException ignored) {
            // empty list is fine
        }
        Collections.sort(out);
        return out;
    }
}
