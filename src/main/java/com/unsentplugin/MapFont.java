package com.unsentplugin;

import java.awt.Font;
import java.io.File;

/**
 * Holds the base font used to draw notes. Loaded once on enable (and on {@code /unsentreload}) from,
 * in order of preference:
 * <ol>
 *   <li>a TrueType file at {@code plugins/UnsentPlugin/font.ttf} (drop a Minecraft font here),</li>
 *   <li>the system font family named by {@code map.font} in config,</li>
 *   <li>SansSerif (the built-in fallback).</li>
 * </ol>
 * The cached base font is style/size-derived per render — cheap, and avoids re-reading the file.
 */
public final class MapFont {

    private static volatile Font base = new Font("SansSerif", Font.PLAIN, 10);

    private MapFont() {}

    public static void load(UnsentPlugin plugin) {
        // Look for the font file inside the plugin's OWN folder (plugins/UnsentPlugin/), not plugins/.
        File ttf = new File(plugin.getDataFolder(), "font.ttf");
        File otf = new File(plugin.getDataFolder(), "font.otf");
        File fontFile = ttf.exists() ? ttf : (otf.exists() ? otf : null);
        if (fontFile != null) {
            try {
                base = Font.createFont(Font.TRUETYPE_FONT, fontFile); // handles both .ttf and .otf
                plugin.getLogger().info("Map font loaded from " + fontFile.getName()
                        + " (family: " + base.getFamily() + ").");
                return;
            } catch (Exception e) {
                plugin.getLogger().warning("Could not load " + fontFile.getName() + " (" + e.getMessage() + ") — falling back.");
            }
        }

        String name = plugin.getConfig().getString("map.font", "SansSerif");
        Font named = new Font(name, Font.PLAIN, 10);
        // new Font() silently falls back to "Dialog" for an unknown family — detect that.
        if (name.equalsIgnoreCase("SansSerif") || named.getFamily().equalsIgnoreCase(name)) {
            base = named;
            return;
        }

        plugin.getLogger().info("Map font \"" + name + "\" isn't installed and no font file was found — using SansSerif. "
                + "For a custom (e.g. Minecraft) font, put it exactly here: " + ttf.getAbsolutePath());
        base = new Font("SansSerif", Font.PLAIN, 10);
    }

    /** A style/size variant of the loaded base font. */
    public static Font derive(int style, float size) {
        return base.deriveFont(style, size);
    }
}
