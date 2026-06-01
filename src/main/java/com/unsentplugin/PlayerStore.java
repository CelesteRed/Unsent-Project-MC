package com.unsentplugin;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Tracks how many messages (note-maps) each player has made, keyed by UUID, in
 * {@code players.yml}. Used to enforce the {@code max-messages-per-player} config limit (players
 * with {@code unsent.unlimited}, which OPs have by default, bypass the limit so their counts
 * aren't checked).
 */
public class PlayerStore {

    private final UnsentPlugin plugin;
    private final File file;
    private final YamlConfiguration cfg;

    public PlayerStore(UnsentPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    // Stored under the "maps" node for backward-compatibility with players.yml written by older
    // versions (one message == one map, so the count is the same either way).
    private static String countPath(UUID uuid) {
        return "players." + uuid + ".maps";
    }

    /** How many messages this player has made so far. */
    public int getMessageCount(UUID uuid) {
        return cfg.getInt(countPath(uuid), 0);
    }

    /** Increments and persists this player's message count, returning the new total. */
    public int incrementMessageCount(UUID uuid) {
        int next = getMessageCount(uuid) + 1;
        cfg.set(countPath(uuid), next);
        save();
        return next;
    }

    private void save() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save players.yml: " + e.getMessage());
        }
    }
}
