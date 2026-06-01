package com.unsentplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UnsentCommand implements CommandExecutor, TabCompleter {

    private final UnsentPlugin plugin;

    /** Players with an AI moderation check currently in flight — blocks spamming the API. */
    private final Set<UUID> pendingChecks = ConcurrentHashMap.newKeySet();

    /** Epoch-millis of each player's last successful map creation, for the cooldown. */
    private final Map<UUID, Long> lastCreation = new ConcurrentHashMap<>();

    /** Players who passed all checks and are being asked for a background colour (unsent.color). */
    private final Map<UUID, PendingNote> pendingColors = new ConcurrentHashMap<>();

    /** A note awaiting a background-colour choice. */
    private record PendingNote(String name, String message) {}

    public UnsentCommand(UnsentPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Block namespaced calls like /unsentplugin:unsent
        if (label.contains(":")) {
            sender.sendMessage(Component.text("Unknown command.").color(NamedTextColor.RED));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can send unsent messages.");
            return true;
        }

        if (pendingColors.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("Choose a colour for your previous note first (type a hex code, 'default', or 'cancel').").color(NamedTextColor.YELLOW));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /unsent <name> <message>").color(NamedTextColor.RED));
            return true;
        }

        String name = args[0];
        WordFilter filter = plugin.getWordFilter();

        if (!filter.isValidName(name)) {
            player.sendMessage(Component.text("That name isn't valid. Use letters, numbers, or underscores (max 16 chars).").color(NamedTextColor.RED));
            return true;
        }

        if (filter.isBlocked(name)) {
            player.sendMessage(Component.text("That name contains blocked content.").color(NamedTextColor.RED));
            return true;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        int maxLen = plugin.getConfig().getInt("max-message-length", 80);
        if (message.length() > maxLen) {
            player.sendMessage(Component.text("Your message is too long (max " + maxLen + " characters).").color(NamedTextColor.RED));
            return true;
        }

        if (filter.isBlocked(message)) {
            player.sendMessage(Component.text("Your message contains content that isn't allowed. Please keep it kind.").color(NamedTextColor.RED));
            return true;
        }

        // Notes must be unique — no duplicate of the same message to the same name.
        if (plugin.getMessageStore().isDuplicate(name, message)) {
            notifyDuplicate(player, name);
            return true;
        }

        // Volume control. unsent.unlimited (OPs by default) bypasses it. When note-credits is on it
        // gates by available credits; otherwise it falls back to the flat per-player message limit.
        boolean unlimited = player.hasPermission("unsent.unlimited");
        boolean creditsEnabled = plugin.getConfig().getBoolean("note-credits.enabled", true);
        if (!unlimited) {
            if (creditsEnabled) {
                if (plugin.getPlayerStore().getCredits(player.getUniqueId()) <= 0) {
                    long ms = plugin.getPlayerStore().millisToNextCredit(player.getUniqueId());
                    player.sendMessage(Component.text("You're out of notes — the next one is ready in "
                            + formatDuration(ms) + ".").color(NamedTextColor.YELLOW));
                    return true;
                }
            } else {
                // Read the new key, falling back to the old `max-maps-per-player` name, then default 10.
                int messageLimit = plugin.getConfig().getInt("max-messages-per-player",
                        plugin.getConfig().getInt("max-maps-per-player", 10));
                if (messageLimit > 0 && plugin.getPlayerStore().getMessageCount(player.getUniqueId()) >= messageLimit) {
                    player.sendMessage(Component.text("You've reached your limit of " + messageLimit + " messages.").color(NamedTextColor.YELLOW));
                    return true;
                }
            }
        }

        // Rate limit: enforce a cooldown between creations (unlimited players are exempt).
        if (!unlimited) {
            int cooldownSeconds = plugin.getConfig().getInt("creation-cooldown-seconds", 30);
            if (cooldownSeconds > 0) {
                long last = lastCreation.getOrDefault(player.getUniqueId(), 0L);
                long remainingMs = cooldownSeconds * 1000L - (System.currentTimeMillis() - last);
                if (remainingMs > 0) {
                    long remainingSec = (remainingMs + 999) / 1000; // round up
                    player.sendMessage(Component.text("Please wait " + remainingSec
                            + "s before making another map.").color(NamedTextColor.YELLOW));
                    return true;
                }
            }
        }

        // Remote checks — real-username validation and AI moderation — are blocking HTTP calls, so
        // run them off the main thread in sequence, then finish back on the main thread.
        UsernameValidator validator = plugin.getUsernameValidator();
        AiModerator ai = plugin.getAiModerator();
        if (validator.isEnabled() || ai.isEnabled()) {
            UUID uuid = player.getUniqueId();
            if (!pendingChecks.add(uuid)) {
                player.sendMessage(Component.text("Hold on — your last message is still being checked.").color(NamedTextColor.YELLOW));
                return true;
            }
            player.sendMessage(Component.text("Checking your message…").color(NamedTextColor.GRAY));

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                String rejection = runRemoteChecks(validator, ai, name, message);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    pendingChecks.remove(uuid);
                    if (!player.isOnline()) return;
                    if (rejection != null) {
                        player.sendMessage(Component.text(rejection).color(NamedTextColor.RED));
                    } else {
                        finishUnsent(player, name, message);
                    }
                });
            });
            return true;
        }

        // No remote checks enabled — commit immediately.
        finishUnsent(player, name, message);
        return true;
    }

    /**
     * Runs the enabled remote checks (username validation, then AI moderation) on the calling
     * thread. Returns a player-facing rejection message, or {@code null} if the message may proceed.
     */
    private String runRemoteChecks(UsernameValidator validator, AiModerator ai, String name, String message) {
        if (validator.isEnabled()) {
            UsernameValidator.Result result = validator.validate(name);
            if (result == UsernameValidator.Result.NOT_FOUND) {
                return "There's no Minecraft player named \"" + name + "\".";
            }
            if (result == UsernameValidator.Result.ERROR && validator.isFailClosed()) {
                return "Couldn't verify that username right now — please try again in a moment.";
            }
        }
        if (ai.isEnabled()) {
            AiModerator.Result result = ai.check(message);
            if (result == AiModerator.Result.UNSAFE) {
                return "Your message looks unsafe for this server. Please keep it kind.";
            }
            if (result == AiModerator.Result.ERROR && ai.isFailClosed()) {
                return "Couldn't verify your message right now — please try again in a moment.";
            }
        }
        return null;
    }

    /**
     * Checks passed. Players with {@code unsent.color} are asked for a background colour first;
     * everyone else gets a white note immediately.
     */
    private void finishUnsent(Player player, String name, String message) {
        if (player.hasPermission("unsent.color")) {
            startColorPrompt(player, name, message);
        } else {
            createNote(player, name, message, Color.WHITE);
        }
    }

    /** Stores a pending note and asks the player to type a background colour in chat. */
    private void startColorPrompt(Player player, String name, String message) {
        UUID uuid = player.getUniqueId();
        pendingColors.put(uuid, new PendingNote(name, message));

        player.sendMessage(Component.text("Color: ").color(NamedTextColor.GRAY)
                .append(Component.text("White").color(NamedTextColor.WHITE))
                .append(Component.text(" [default]").color(NamedTextColor.DARK_GRAY)));
        player.sendMessage(Component.text("Type a hex code (e.g. #FFAA00) for the background, "
                + "or 'default' to keep white. 'cancel' to abort.").color(NamedTextColor.GRAY));

        // Expire after 60s — fall back to a white note so a passed message isn't lost.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingNote stale = pendingColors.remove(uuid);
            if (stale != null && player.isOnline()) {
                player.sendMessage(Component.text("No colour chosen — using white.").color(NamedTextColor.GRAY));
                createNote(player, stale.name(), stale.message(), Color.WHITE);
            }
        }, 20L * 60);
    }

    /** True if the player is mid-prompt for a background colour. Called by {@link ColorPromptListener}. */
    public boolean hasPendingColor(UUID uuid) {
        return pendingColors.containsKey(uuid);
    }

    /** Handles a player's typed colour input (run on the main thread by {@link ColorPromptListener}). */
    public void submitColor(Player player, String input) {
        PendingNote pending = pendingColors.get(player.getUniqueId());
        if (pending == null) return; // expired or already handled

        String text = input.trim();
        if (text.equalsIgnoreCase("cancel")) {
            pendingColors.remove(player.getUniqueId());
            player.sendMessage(Component.text("Cancelled — note discarded.").color(NamedTextColor.YELLOW));
            return;
        }

        Color color = parseColor(text);
        if (color == null) {
            player.sendMessage(Component.text("That isn't a valid hex colour. Try #RRGGBB, 'default', or 'cancel'.").color(NamedTextColor.RED));
            return; // keep the prompt open for another try
        }

        pendingColors.remove(player.getUniqueId());
        createNote(player, pending.name(), pending.message(), color);
    }

    /** Parses "#RRGGBB"/"RRGGBB", or "default"/"white"/empty → white. Returns null if invalid. */
    private Color parseColor(String input) {
        if (input.isEmpty() || input.equalsIgnoreCase("default") || input.equalsIgnoreCase("white")) {
            return Color.WHITE;
        }
        String hex = input.startsWith("#") ? input.substring(1) : input;
        if (hex.matches("[0-9A-Fa-f]{6}")) {
            return new Color(Integer.parseInt(hex, 16));
        }
        return null;
    }

    /** Saves the message, builds the map with the chosen background, gives it, and counts it. */
    private void createNote(Player player, String name, String message, Color background) {
        // Re-check uniqueness here too — this is the authoritative point, after any prompt/async gap.
        if (plugin.getMessageStore().isDuplicate(name, message)) {
            notifyDuplicate(player, name);
            return;
        }

        // Stamp once so the stored value and the value rendered on the map match exactly.
        long now = System.currentTimeMillis();

        boolean saved = plugin.getMessageStore().save(name, message, now);
        if (!saved) {
            player.sendMessage(Component.text("There are already too many messages for that name.").color(NamedTextColor.YELLOW));
            return;
        }

        ItemStack map = MapFactory.createMap(plugin, player.getWorld(), name, message, now, background);

        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), map);
            player.sendMessage(Component.text("Your inventory was full — your map dropped at your feet.").color(NamedTextColor.YELLOW));
        } else {
            player.getInventory().addItem(map);
        }

        // Account for the note: spend a credit (and refresh the voucher) or count it toward the
        // flat limit. Unlimited players are exempt.
        boolean creditsEnabled = plugin.getConfig().getBoolean("note-credits.enabled", true);
        boolean unlimited = player.hasPermission("unsent.unlimited");
        if (!unlimited) {
            if (creditsEnabled) {
                plugin.getPlayerStore().spendCredit(player.getUniqueId());
                NoteVoucher.sync(plugin, player);
            } else {
                plugin.getPlayerStore().incrementMessageCount(player.getUniqueId());
            }
        }

        // Start the creation cooldown from this successful map.
        lastCreation.put(player.getUniqueId(), System.currentTimeMillis());

        player.sendMessage(
            Component.text("Your message to ")
                     .color(NamedTextColor.GRAY)
                .append(Component.text(name).color(NamedTextColor.WHITE))
                .append(Component.text(" has been sealed into a map.").color(NamedTextColor.GRAY))
        );

        if (creditsEnabled && !unlimited) {
            int left = plugin.getPlayerStore().getCredits(player.getUniqueId());
            player.sendMessage(Component.text("Notes left: ").color(NamedTextColor.DARK_GRAY)
                    .append(Component.text(left).color(NamedTextColor.GRAY)));
        }
    }

    /** Clears any pending colour prompt for a player (called when they quit). */
    public void clearPendingColor(UUID uuid) {
        pendingColors.remove(uuid);
    }

    /** Formats a millisecond duration as "Xm Ys" (or "Ys" under a minute). */
    private static String formatDuration(long ms) {
        long totalSeconds = (ms + 999) / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "s";
    }

    private void notifyDuplicate(Player player, String name) {
        player.sendMessage(Component.text("That exact note already exists for ").color(NamedTextColor.YELLOW)
                .append(Component.text(name).color(NamedTextColor.WHITE))
                .append(Component.text(" — make it unique!").color(NamedTextColor.YELLOW)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (label.contains(":")) return Collections.emptyList();
        if (args.length == 1) {
            String typed = args[0].toLowerCase();
            // Suggest online + previously-joined players (like /tp, but offline players too),
            // plus any names that already have messages. Case-insensitive, de-duplicated.
            TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
                String playerName = offline.getName();
                if (playerName != null && playerName.toLowerCase().startsWith(typed)) {
                    names.add(playerName);
                }
            }
            for (String storedName : plugin.getMessageStore().allNames()) {
                if (storedName.toLowerCase().startsWith(typed)) names.add(storedName);
            }
            return names.stream().limit(100).toList();
        }
        return Collections.emptyList();
    }
}
