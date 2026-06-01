package com.unsentplugin;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Loads {@code whitelist.yml} — the allow-list of block materials an unsent map may be placed on
 * (in a wall-mounted item frame). Only blocks in this set are permitted; see {@link ItemFrameListener}
 * for the placement enforcement. The file is user-curated and is not auto-merged on update.
 */
public class BlockWhitelist {

    private final UnsentPlugin plugin;
    private final File file;
    private final Set<Material> allowed = EnumSet.noneOf(Material.class);

    public BlockWhitelist(UnsentPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "whitelist.yml");
        reload();
    }

    /** (Re)reads whitelist.yml, writing the bundled default first if it doesn't exist yet. */
    public void reload() {
        if (!file.exists()) plugin.saveResource("whitelist.yml", false);

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        allowed.clear();

        List<String> names = cfg.getStringList("allowed-blocks");
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material != null && material.isBlock()) {
                allowed.add(material);
            } else {
                plugin.getLogger().warning("whitelist.yml: '" + name + "' is not a known block — skipping.");
            }
        }

        if (allowed.isEmpty()) {
            plugin.getLogger().warning("whitelist.yml has no valid blocks — no surface will accept an unsent map.");
        } else {
            plugin.getLogger().info("Loaded " + allowed.size() + " whitelisted block(s) for map placement.");
        }
    }

    /** True if a map may be placed on a block of this material. */
    public boolean isAllowed(Material material) {
        return material != null && allowed.contains(material);
    }

    /** Adds a block and persists. Returns false if it was already whitelisted. */
    public boolean add(Material material) {
        if (!allowed.add(material)) return false;
        save();
        return true;
    }

    /** Removes a block and persists. Returns false if it wasn't whitelisted. */
    public boolean remove(Material material) {
        if (!allowed.remove(material)) return false;
        save();
        return true;
    }

    /** A sorted, read-only snapshot of the whitelisted blocks. */
    public List<Material> getAllowed() {
        return allowed.stream().sorted(Comparator.comparing(Material::name)).toList();
    }

    /** Writes the current set back to whitelist.yml (preserving the file's header comments). */
    private void save() {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        cfg.set("allowed-blocks", allowed.stream().map(Material::name).sorted().toList());
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save whitelist.yml: " + e.getMessage());
        }
    }
}
