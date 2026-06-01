package com.unsentplugin;

import org.bukkit.Rotation;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Keeps an unsent map sealed inside an item frame: once placed it can't be popped back out,
 * rotated, nor can the frame be removed (by a player, mob, projectile, explosion, or by breaking
 * the block it hangs on), so the note stays exactly as it was hung. The frame's rotation is also
 * reset to upright the moment a map is placed, so a pre-rotated frame can't lock the note facing
 * sideways. Players with {@code unsent.admin} bypass the protection for moderation/cleanup.
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
     * Breaking the whole frame would also drop the map. This covers every break cause —
     * {@link HangingBreakByEntityEvent} (player/mob/projectile/explosion) is a subclass and shares
     * this handler list, plus physics (the block it hangs on being removed) and obstruction.
     */
    @EventHandler
    public void onFrameBreak(HangingBreakEvent event) {
        if (event.getEntity() instanceof ItemFrame frame && isUnsentMap(frame.getItem())) {
            // When an entity is responsible, let admins through; environmental causes are always blocked.
            if (event instanceof HangingBreakByEntityEvent byEntity && canBypass(byEntity.getRemover())) return;
            event.setCancelled(true);
        }
    }

    /**
     * Handles right-clicking an item frame:
     * <ul>
     *   <li>If it already holds our map, the click would rotate it — cancel that (admins bypass).</li>
     *   <li>If it's empty and the player is placing our map, reset the frame's rotation to upright
     *       on the next tick (once the item is in), so a pre-rotated frame can't lock the note
     *       facing sideways.</li>
     * </ul>
     */
    @EventHandler
    public void onFrameInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame frame)) return;

        // Frame already holds our map → block the rotate.
        if (isUnsentMap(frame.getItem())) {
            if (!canBypass(event.getPlayer())) event.setCancelled(true);
            return;
        }

        // Empty frame + player placing our map → straighten it once the map lands in the frame.
        ItemStack current = frame.getItem();
        boolean empty = current == null || current.getType().isAir();
        if (empty && isUnsentMap(itemInHand(event.getPlayer(), event.getHand()))) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (frame.isValid() && isUnsentMap(frame.getItem())) {
                    frame.setRotation(Rotation.NONE);
                }
            });
        }
    }

    /** The item the player is interacting with, from whichever hand triggered the event. */
    private ItemStack itemInHand(Player player, EquipmentSlot hand) {
        return hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
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
