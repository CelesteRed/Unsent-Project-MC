package com.unsentplugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Keeps {@code config.yml} in sync with the template bundled in the jar.
 *
 * <p>On startup it compares the {@code config-version} stamp at the bottom of the server's config
 * against the bundled one. If they differ, it rebuilds the file from the bundled template — so new
 * options (with their comments) appear and obsolete ones are dropped — while copying over every
 * value the user had customised, then re-stamps the new version. Existing settings are preserved;
 * the only thing lost is any hand-written comments the user added.
 */
public final class ConfigUpdater {

    private ConfigUpdater() {}

    public static void update(UnsentPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) return; // saveDefaultConfig() handles a fresh install

        // Load the template bundled in the jar.
        YamlConfiguration bundled;
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) return;
            bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read bundled config.yml: " + e.getMessage());
            return;
        }

        FileConfiguration user = plugin.getConfig();
        String bundledVersion = bundled.getString("config-version", "");
        String userVersion = user.getString("config-version", "");
        if (bundledVersion.equals(userVersion)) return; // already current

        // Overlay the user's values onto the fresh template: keeps their customisations, adds new
        // keys (with default values + comments), and drops keys that no longer exist.
        for (String key : user.getKeys(true)) {
            if (key.equals("config-version")) continue;
            if (user.isConfigurationSection(key)) continue; // copy leaf values only
            if (bundled.contains(key)) {
                bundled.set(key, user.get(key));
            }
        }

        // Carry over renamed keys so their values aren't reset to defaults.
        if (user.contains("max-maps-per-player") && !user.contains("max-messages-per-player")) {
            bundled.set("max-messages-per-player", user.get("max-maps-per-player"));
        }

        bundled.set("config-version", bundledVersion);

        try {
            bundled.save(file);
            plugin.reloadConfig();
            plugin.getLogger().info("Updated config.yml from "
                    + (userVersion.isEmpty() ? "(unversioned)" : "v" + userVersion)
                    + " to v" + bundledVersion + " — your settings were kept.");
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save updated config.yml: " + e.getMessage());
        }
    }
}
