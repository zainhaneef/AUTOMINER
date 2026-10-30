package com.areaminer.pickaxe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Loads, stores and resolves pickaxe profiles. */
public final class PickaxeProfiles {
    private static final Logger LOG = LoggerFactory.getLogger("AreaMiner");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static List<PickaxeProfile> profiles = new ArrayList<>();

    private PickaxeProfiles() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("areaminer-pickaxes.json");
    }

    public static void load() {
        Path f = file();
        try {
            if (Files.exists(f)) {
                List<PickaxeProfile> read = GSON.fromJson(Files.readString(f), new TypeToken<List<PickaxeProfile>>() { }.getType());
                if (read != null && !read.isEmpty()) {
                    profiles = new ArrayList<>(read);
                }
            }
        } catch (Exception e) {
            LOG.warn("Could not read {} - using defaults", f, e);
        }
        if (profiles.isEmpty()) {
            profiles = defaults();
            save();
        }
        if (byId("normal") == null) {
            profiles.add(0, defaults().get(0));
        }
    }

    public static void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(profiles));
        } catch (IOException e) {
            LOG.warn("Could not write pickaxe profiles", e);
        }
    }

    private static List<PickaxeProfile> defaults() {
        List<PickaxeProfile> list = new ArrayList<>();
        PickaxeProfile normal = new PickaxeProfile();
        normal.id = "normal";
        normal.displayName = "Normal pickaxe";
        normal.squareSize = 1;
        list.add(normal);

        PickaxeProfile amethyst = new PickaxeProfile();
        amethyst.id = "amethyst";
        amethyst.displayName = "Amethyst Pickaxe";
        amethyst.nameContains.add("amethyst");
        amethyst.squareSize = 3;
        amethyst.needsActivation = true;
        amethyst.notes = "3x3 in the layer plane (mine the top face). Verify with /miner calibrate.";
        list.add(amethyst);
        return list;
    }

    public static List<PickaxeProfile> all() {
        return profiles;
    }

    public static void add(PickaxeProfile p) {
        profiles.add(p);
    }

    public static PickaxeProfile byId(String id) {
        for (PickaxeProfile p : profiles) {
            if (p.id.equalsIgnoreCase(id)) {
                return p;
            }
        }
        return null;
    }

    public static PickaxeProfile normal() {
        PickaxeProfile n = byId("normal");
        return n != null ? n : defaults().get(0);
    }

    private static PickaxeProfile matchRule(ItemStack stack) {
        String name = stack.getName().getString();
        List<String> lore = new ArrayList<>();
        LoreComponent lc = stack.get(DataComponentTypes.LORE);
        if (lc != null) {
            for (Text t : lc.lines()) {
                lore.add(t.getString());
            }
        }
        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        for (PickaxeProfile p : profiles) {
            if (p.matches(name, lore, itemId)) {
                return p;
            }
        }
        return null;
    }

    /** True for vanilla pickaxes and for anything a profile rule recognises. */
    public static boolean isMiningTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.isIn(ItemTags.PICKAXES) || matchRule(stack) != null;
    }

    /**
     * @param override "auto" or a profile id chosen by the player
     * @return the profile in effect, or null when "auto" and no mining tool is held
     */
    public static PickaxeProfile resolve(ItemStack stack, String override) {
        if (override != null && !"auto".equalsIgnoreCase(override)) {
            PickaxeProfile forced = byId(override);
            if (forced != null) {
                return forced;
            }
        }
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        PickaxeProfile matched = matchRule(stack);
        if (matched != null) {
            return matched;
        }
        return stack.isIn(ItemTags.PICKAXES) ? normal() : null;
    }
}
