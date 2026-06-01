package com.unsentplugin;

import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Keeps an unsent map sealed inside an item frame: once placed, it can't be popped back out
 * (nor can the frame be knocked down by an entity), so the note stays where it was hung.
 * Players with {@code unsent.admin} bypass the protection for moderation/cleanup.
 */
public class ItemFrameListener implements Listener {

    private final UnsentPlugin plugin;

    public ItemFrameListener(UnsentPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * The first hit on a filled item frame normally pops the item out (without breaking the
     * frame). Cancel that when the frame holds one of our tagged maps.
     */
    @EventHandler
    public void onFrameDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof ItemFrame frame && isUnsentMap(frame.getItem())) {
            if (canBypass(event.getDamager())) return;
            event.setCancelled(true);
        }
    }

    /**
     * Breaking the whole frame (e.g. by a mob, projectile, or another entity) would also drop
     * the map on the ground. Protect the frame too while it holds an unsent map.
     */
    @EventHandler
    public void onFrameBreak(HangingBreakByEntityEvent event) {
        if (event.getEntity() instanceof ItemFrame frame && isUnsentMap(frame.getItem())) {
            if (canBypass(event.getRemover())) return;
            event.setCancelled(true);
        }
    }

    /** Admins may still remove protected maps/frames. */
    private boolean canBypass(Entity entity) {
        return entity instanceof Player player && player.hasPermission("unsent.admin");
    }

    /** True if the item carries our PersistentDataContainer marker from {@link MapFactory}. */
    private boolean isUnsentMap(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(MapFactory.unsentMapKey(plugin), PersistentDataType.BYTE);
    }
}
