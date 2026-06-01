package com.unsentplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * Admin recovery tool: <code>/unsentrecover &lt;name&gt; [number]</code>.
 *
 * <p>Rebuilds the renderer on a blank map (e.g. one created before the restart-fix, whose note was
 * wiped when the server restarted). The player holds the blank map, picks which stored message to
 * bind by its number from {@code /unsentread <name>}, and the map is re-rendered and registered so
 * it survives future restarts.
 */
public class RecoverCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MM/dd/yy").withZone(ZoneId.systemDefault());

    private final UnsentPlugin plugin;

    public RecoverCommand(UnsentPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.contains(":")) {
            sender.sendMessage(Component.text("Unknown command.").color(NamedTextColor.RED));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can recover maps (you need to hold the map).");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /unsentrecover <name> [number]").color(NamedTextColor.RED));
            player.sendMessage(Component.text("Hold the blank map and use the number from /unsentread <name>.").color(NamedTextColor.GRAY));
            return true;
        }

        String name = args[0];
        List<MessageStore.UnsentMessage> messages = plugin.getMessageStore().load(name);
        if (messages.isEmpty()) {
            player.sendMessage(Component.text("No stored messages found for ").color(NamedTextColor.GRAY)
                    .append(Component.text(name).color(NamedTextColor.WHITE))
                    .append(Component.text(".").color(NamedTextColor.GRAY)));
            return true;
        }

        // Pick which message to bind. Default to the only one if there's just a single message.
        int index;
        if (args.length >= 2) {
            try {
                index = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("\"" + args[1] + "\" isn't a valid number.").color(NamedTextColor.RED));
                return true;
            }
        } else if (messages.size() == 1) {
            index = 1;
        } else {
            player.sendMessage(Component.text(name + " has " + messages.size()
                    + " messages — pick one with /unsentrecover " + name + " <number> "
                    + "(see /unsentread " + name + ").").color(NamedTextColor.YELLOW));
            return true;
        }

        if (index < 1 || index > messages.size()) {
            player.sendMessage(Component.text("Pick a number between 1 and " + messages.size() + ".").color(NamedTextColor.RED));
            return true;
        }

        // The map must be held in the main hand.
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() != Material.FILLED_MAP) {
            player.sendMessage(Component.text("Hold the blank map in your main hand first.").color(NamedTextColor.RED));
            return true;
        }
        if (!(held.getItemMeta() instanceof MapMeta mapMeta) || !mapMeta.hasMapView()) {
            player.sendMessage(Component.text("That map has no map data to rebuild.").color(NamedTextColor.RED));
            return true;
        }
        MapView view = mapMeta.getMapView();
        if (view == null) {
            player.sendMessage(Component.text("Couldn't read that map's view.").color(NamedTextColor.RED));
            return true;
        }

        MessageStore.UnsentMessage msg = messages.get(index - 1);
        MapFactory.restoreMap(plugin, held, view, name, msg.text, msg.timestamp);

        String date = msg.timestamp > 0 ? DATE_FMT.format(Instant.ofEpochMilli(msg.timestamp)) : "unknown date";
        player.sendMessage(
            Component.text("Restored the note to ").color(NamedTextColor.GRAY)
                .append(Component.text(name).color(NamedTextColor.WHITE))
                .append(Component.text(" (" + date + "). It will now survive restarts.").color(NamedTextColor.GRAY))
        );
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (label.contains(":")) return Collections.emptyList();
        if (args.length == 1) {
            String typed = args[0].toLowerCase();
            return plugin.getMessageStore().allNames().stream()
                    .filter(n -> n.startsWith(typed))
                    .toList();
        }
        return Collections.emptyList();
    }
}
