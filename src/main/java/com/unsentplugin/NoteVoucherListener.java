package com.unsentplugin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Keeps a player's "Unsent Note" voucher in sync when they join. Ongoing regeneration is handled
 * by a repeating task in {@link UnsentPlugin}.
 */
public class NoteVoucherListener implements Listener {

    private final UnsentPlugin plugin;

    public NoteVoucherListener(UnsentPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        NoteVoucher.sync(plugin, event.getPlayer());
    }
}
