package com.unsentplugin;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.map.MapView;

import java.io.File;
import java.io.IOException;

/**
 * Persists the note content of every generated map, keyed by its numeric map id, so the custom
 * renderer can be restored after a server restart.
 *
 * <p><b>Why this exists:</b> Bukkit {@link org.bukkit.map.MapRenderer}s added at runtime are not
 * saved with the world. After a restart the {@link MapView} reloads from disk but our
 * {@link UnsentMapRenderer} is gone, and CraftBukkit re-attaches the default (terrain) renderer —
 * which redraws the map blank, so the note vanishes. On enable we read this registry and re-apply
 * the renderer to every stored map id. Because a {@code MapView} is a single global object shared
 * by every item that references its id, re-attaching here restores the note everywhere it appears
 * (inventory, item frame, chest, even chunks that aren't loaded yet).
 */
public class MapStore {

    private final UnsentPlugin plugin;
    private final File file;
    private final YamlConfiguration cfg;

    public MapStore(UnsentPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "maps.yml");
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    /** Records (or overwrites) the note data for a map id so its renderer can be rebuilt later. */
    public void record(int mapId, String recipient, String message, long timestamp) {
        String base = "maps." + mapId;
        cfg.set(base + ".recipient", recipient);
        cfg.set(base + ".message", message);
        cfg.set(base + ".timestamp", timestamp);
        save();
    }

    /**
     * Re-attaches the {@link UnsentMapRenderer} to every stored map. Call once, ideally on the
     * first server tick so all worlds/map data are ready. Ids whose {@link MapView} no longer
     * exists are skipped (and pruned). Returns the number of maps restored.
     */
    public int rehydrateAll() {
        ConfigurationSection maps = cfg.getConfigurationSection("maps");
        if (maps == null) return 0;

        int restored = 0;
        boolean pruned = false;
        for (String key : maps.getKeys(false)) {
            int mapId;
            try {
                mapId = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                continue;
            }

            MapView view = Bukkit.getMap(mapId);
            if (view == null) {
                // Underlying map data is gone — drop the stale registry entry.
                cfg.set("maps." + key, null);
                pruned = true;
                continue;
            }

            String recipient = maps.getString(key + ".recipient", "");
            String message   = maps.getString(key + ".message", "");
            long timestamp   = maps.getLong(key + ".timestamp", 0L);
            MapFactory.applyUnsentRenderer(view, recipient, message, timestamp);
            restored++;
        }

        if (pruned) save();
        return restored;
    }

    private void save() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save maps.yml: " + e.getMessage());
        }
    }
}
