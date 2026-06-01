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

    // ── Note credits (earn-over-time posting) ───────────────────────────────────

    private static String creditsPath(UUID uuid) { return "players." + uuid + ".credits"; }
    private static String regenPath(UUID uuid)   { return "players." + uuid + ".credit-regen"; }

    private int  startCredits() { return Math.max(0, plugin.getConfig().getInt("note-credits.start", 1)); }
    private int  maxCredits()   { return Math.max(1, plugin.getConfig().getInt("note-credits.max", 5)); }

    private long regenMillis() {
        long parsed = parseDuration(plugin.getConfig().getString("note-credits.regen-period", ""));
        if (parsed > 0) return parsed;
        // Fall back to the older minutes key (default 10080 minutes = 1 week).
        return Math.max(1, plugin.getConfig().getInt("note-credits.regen-minutes", 10080)) * 60_000L;
    }

    /** Parses "15m", "2h", "7d", "1w", or a plain number (minutes). Returns 0 if unparseable. */
    static long parseDuration(String input) {
        if (input == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\s*(\\d+)\\s*([smhdw]?)\\s*").matcher(input.toLowerCase());
        if (!m.matches()) return 0;
        long value = Long.parseLong(m.group(1));
        return value * switch (m.group(2)) {
            case "s" -> 1_000L;
            case "h" -> 3_600_000L;
            case "d" -> 86_400_000L;
            case "w" -> 604_800_000L;
            default  -> 60_000L; // "m" or empty → minutes
        };
    }

    /** Current credit balance, after applying any pending regeneration. */
    public int getCredits(UUID uuid) {
        regenerate(uuid);
        return cfg.getInt(creditsPath(uuid), startCredits());
    }

    /** Spends one credit if available. Returns false if the player has none. */
    public boolean spendCredit(UUID uuid) {
        regenerate(uuid);
        int credits = cfg.getInt(creditsPath(uuid), startCredits());
        if (credits <= 0) return false;
        boolean wasAtCap = credits >= maxCredits();
        cfg.set(creditsPath(uuid), credits - 1);
        // If they were full, start the regen timer fresh from now.
        if (wasAtCap) cfg.set(regenPath(uuid), System.currentTimeMillis());
        save();
        return true;
    }

    /** Milliseconds until the next credit regenerates (0 if already at the cap). */
    public long millisToNextCredit(UUID uuid) {
        regenerate(uuid);
        if (cfg.getInt(creditsPath(uuid), startCredits()) >= maxCredits()) return 0;
        long last = cfg.getLong(regenPath(uuid), System.currentTimeMillis());
        return Math.max(0, last + regenMillis() - System.currentTimeMillis());
    }

    /** Lazily adds any credits earned since the last regen tick. */
    private void regenerate(UUID uuid) {
        long now = System.currentTimeMillis();
        if (!cfg.contains(creditsPath(uuid))) { // first time seen → starting credits
            cfg.set(creditsPath(uuid), startCredits());
            cfg.set(regenPath(uuid), now);
            save();
            return;
        }
        int credits = cfg.getInt(creditsPath(uuid));
        int max = maxCredits();
        if (credits >= max) return; // at cap — no regen (timer restarts on spend)

        long interval = regenMillis();
        long last = cfg.getLong(regenPath(uuid), now);
        long gained = (now - last) / interval;
        if (gained <= 0) return;

        int newCredits = (int) Math.min(max, credits + gained);
        int consumed = newCredits - credits;
        long newLast = (newCredits >= max) ? now : last + (long) consumed * interval;
        cfg.set(creditsPath(uuid), newCredits);
        cfg.set(regenPath(uuid), newLast);
        save();
    }

    private void save() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save players.yml: " + e.getMessage());
        }
    }
}
