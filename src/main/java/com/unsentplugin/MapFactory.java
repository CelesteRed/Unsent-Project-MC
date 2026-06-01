package com.unsentplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

public class MapFactory {

    /**
     * Marker key written into the map's PersistentDataContainer so listeners can recognise an
     * unsent map (e.g. to keep it locked inside an item frame). Built from the plugin instance,
     * so its namespace is always the plugin name.
     */
    public static NamespacedKey unsentMapKey(Plugin plugin) {
        return new NamespacedKey(plugin, "unsent_map");
    }

    /**
     * Configures a MapView to display an unsent note: disables position tracking, strips the
     * default (terrain) renderers, and attaches a fresh {@link UnsentMapRenderer}. Used both when
     * a map is first created and when restoring renderers on startup (see {@link MapStore}).
     */
    public static void applyUnsentRenderer(MapView mapView, String recipientName,
                                           String message, long timestamp) {
        mapView.setScale(MapView.Scale.NORMAL);
        mapView.setTrackingPosition(false);
        mapView.setUnlimitedTracking(false);

        // Remove existing renderers (the default terrain render, or a stale one on reload).
        for (org.bukkit.map.MapRenderer r : new java.util.ArrayList<>(mapView.getRenderers())) {
            mapView.removeRenderer(r);
        }

        mapView.addRenderer(new UnsentMapRenderer(recipientName, message, timestamp));
    }

    public static ItemStack createMap(UnsentPlugin plugin, World world, String recipientName,
                                      String message, long timestamp) {
        // Create a new MapView and attach our custom renderer
        MapView mapView = Bukkit.createMap(world);
        applyUnsentRenderer(mapView, recipientName, message, timestamp);

        // Remember this map so its renderer can be restored after a server restart
        // (runtime renderers are not persisted with the world — see MapStore).
        plugin.getMapStore().record(mapView.getId(), recipientName, message, timestamp);

        // Build the item
        ItemStack item = new ItemStack(Material.FILLED_MAP);

        // Edit meta safely
        item.editMeta(MapMeta.class, meta -> {
            meta.setMapView(mapView);
            decorate(meta, plugin, message);
        });

        return item;
    }

    /**
     * Recovery tool: rebinds an existing (e.g. blanked-after-restart) map item to a note. Attaches
     * a fresh renderer to the item's MapView, registers it for future restarts, and re-applies the
     * tag/name/lore — so even maps created before the registry existed can be brought back.
     */
    public static void restoreMap(UnsentPlugin plugin, ItemStack item, MapView mapView,
                                  String recipientName, String message, long timestamp) {
        applyUnsentRenderer(mapView, recipientName, message, timestamp);
        plugin.getMapStore().record(mapView.getId(), recipientName, message, timestamp);

        item.editMeta(MapMeta.class, meta -> {
            meta.setMapView(mapView);
            decorate(meta, plugin, message);
        });
    }

    /** Applies the shared tag and lore snippet to an unsent map's meta. */
    private static void decorate(MapMeta meta, Plugin plugin, String message) {
        // Tag it so the item-frame listener can identify our maps.
        meta.getPersistentDataContainer().set(unsentMapKey(plugin), PersistentDataType.BYTE, (byte) 1);

        // No custom display name on purpose: a renamed item shows floating text when placed in an
        // item frame, and the recipient ("to X") is already drawn on the map face. Clear any
        // existing name explicitly so recovered/old maps lose their floating name too.
        meta.displayName(null);

        String snippet = message.length() > 30 ? message.substring(0, 27) + "..." : message;
        meta.lore(List.of(
            Component.text("\"" + snippet + "\"")
                     .color(NamedTextColor.GRAY)
                     .decoration(TextDecoration.ITALIC, true)
        ));
    }
}
