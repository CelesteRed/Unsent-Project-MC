package com.unsentplugin;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class MessageStore {

    private final UnsentPlugin plugin;
    private final File messagesDir;

    public MessageStore(UnsentPlugin plugin) {
        this.plugin = plugin;
        this.messagesDir = new File(plugin.getDataFolder(), "messages");
        if (!messagesDir.exists()) messagesDir.mkdirs();
    }

    /** Saves a new message for the given name. Returns false if the cap is reached. */
    public boolean save(String name, String message) {
        File file = fileFor(name);
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        List<String> messages = cfg.getStringList("messages");
        int cap = plugin.getConfig().getInt("max-messages-per-name", 100);
        if (messages.size() >= cap) return false;

        // Store message with timestamp prefix separated by "|"
        messages.add(Instant.now().toEpochMilli() + "|" + message);
        cfg.set("messages", messages);

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save message for " + name + ": " + e.getMessage());
        }
        return true;
    }

    /** Returns all stored UnsentMessage objects for a name (oldest first). */
    public List<UnsentMessage> load(String name) {
        File file = fileFor(name);
        if (!file.exists()) return new ArrayList<>();

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        List<String> raw = cfg.getStringList("messages");
        List<UnsentMessage> result = new ArrayList<>();

        for (String entry : raw) {
            int sep = entry.indexOf('|');
            if (sep < 0) {
                result.add(new UnsentMessage(0, entry));
            } else {
                long ts = Long.parseLong(entry.substring(0, sep));
                String msg = entry.substring(sep + 1);
                result.add(new UnsentMessage(ts, msg));
            }
        }
        return result;
    }

    /** Deletes all messages for a name. */
    public void clear(String name) {
        fileFor(name).delete();
    }

    /** Returns all names that have messages. */
    public List<String> allNames() {
        List<String> names = new ArrayList<>();
        File[] files = messagesDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) {
            for (File f : files) names.add(f.getName().replace(".yml", ""));
        }
        return names;
    }

    private File fileFor(String name) {
        return new File(messagesDir, name.toLowerCase() + ".yml");
    }

    // ── Inner record ──────────────────────────────────────────────────────────

    public static class UnsentMessage {
        public final long timestamp;
        public final String text;

        public UnsentMessage(long timestamp, String text) {
            this.timestamp = timestamp;
            this.text      = text;
        }
    }
}
