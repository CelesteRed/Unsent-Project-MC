package com.unsentplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;

/**
 * Item-level protection for unsent notes: they can't be dropped on the floor (only placed on a
 * wall or kept). Dropping one is cancelled with a cheeky reminder.
 */
public class NoteItemListener implements Listener {

    private final UnsentPlugin plugin;

    public NoteItemListener(UnsentPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (MapFactory.isUnsentMap(plugin, event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("The floor isn't a trash can!").color(NamedTextColor.RED));
        }
    }
}
