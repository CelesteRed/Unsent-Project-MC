package com.unsentplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * The "Unsent Note" paper item that shows a player's available note credits as a stack in their
 * inventory (stack size = credits). It's a display only — the authoritative balance lives in
 * {@link PlayerStore}; {@link #sync} reconciles the inventory item to match, so dropping/crafting
 * it can't change a player's real credit count.
 */
public final class NoteVoucher {

    private NoteVoucher() {}

    private static NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(plugin, "note_voucher");
    }

    /** Builds a voucher paper stack of the given size. */
    public static ItemStack create(Plugin plugin, int amount) {
        ItemStack item = new ItemStack(Material.PAPER, Math.max(1, Math.min(amount, 64)));
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
            meta.displayName(Component.text("Unsent Note").color(NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                Component.text("One note you can send with /unsent.").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("You earn more over time.").color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
            ));
        });
        return item;
    }

    private static boolean is(Plugin plugin, ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
    }

    private static int count(Plugin plugin, Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (is(plugin, item)) total += item.getAmount();
        }
        return total;
    }

    private static void removeAll(Plugin plugin, Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            if (is(plugin, contents[i])) {
                contents[i] = null;
                changed = true;
            }
        }
        if (changed) player.getInventory().setContents(contents);
    }

    /** Reconciles the player's voucher item to their current credit balance. */
    public static void sync(UnsentPlugin plugin, Player player) {
        boolean enabled = plugin.getConfig().getBoolean("note-credits.enabled", true);
        boolean unlimited = player.hasPermission("unsent.unlimited");
        if (!enabled || unlimited) {
            removeAll(plugin, player); // no credits in play → no voucher
            return;
        }

        int credits = plugin.getPlayerStore().getCredits(player.getUniqueId());
        if (count(plugin, player) == credits) return; // already in sync

        removeAll(plugin, player);
        if (credits > 0) {
            player.getInventory().addItem(create(plugin, credits));
        }
    }
}
