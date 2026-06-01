package com.unsentplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

import java.util.List;

public class MapFactory {

    public static ItemStack createMap(World world, String recipientName, String message) {
        // Create a new MapView and attach our custom renderer
        MapView mapView = Bukkit.createMap(world);
        mapView.setScale(MapView.Scale.NORMAL);
        mapView.setTrackingPosition(false);
        mapView.setUnlimitedTracking(false);

        // Remove default renderers (removes the terrain render)
        for (org.bukkit.map.MapRenderer r : new java.util.ArrayList<>(mapView.getRenderers())) {
            mapView.removeRenderer(r);
        }

        // Add our custom renderer
        mapView.addRenderer(new UnsentMapRenderer(recipientName, message));

        // Build the item
        ItemStack item = new ItemStack(Material.FILLED_MAP);

        // Edit meta safely
        item.editMeta(MapMeta.class, meta -> {
            meta.setMapView(mapView);

            meta.displayName(
                Component.text("to " + recipientName)
                         .color(NamedTextColor.DARK_GRAY)
                         .decoration(TextDecoration.ITALIC, true)
            );

            String snippet = message.length() > 30 ? message.substring(0, 27) + "..." : message;
            meta.lore(List.of(
                Component.text("\"" + snippet + "\"")
                         .color(NamedTextColor.GRAY)
                         .decoration(TextDecoration.ITALIC, true)
            ));
        });

        return item;
    }
}
