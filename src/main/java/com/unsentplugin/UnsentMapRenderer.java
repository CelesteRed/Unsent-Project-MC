package com.unsentplugin;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class UnsentMapRenderer extends MapRenderer {

    // The map canvas is always 128×128 pixels.
    private static final int MAP_SIZE = 128;

    // Date stamp drawn on the map, e.g. 06/01/26.
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MM/dd/yy").withZone(ZoneId.systemDefault());

    // Subtle watermark shown in the bottom-left corner of every note.
    private static final String WATERMARK = "[Mc Project Unsent]";

    private final String recipientName;
    private final String message;
    private final long timestamp;
    private final Color background;
    private final int bodyFontSize;
    private boolean rendered = false;

    public UnsentMapRenderer(String recipientName, String message, long timestamp, Color background, int bodyFontSize) {
        this.recipientName = recipientName;
        this.message       = message;
        this.timestamp     = timestamp;
        this.background     = background != null ? background : Color.WHITE;
        this.bodyFontSize  = bodyFontSize;
    }

    /** True if the colour is dark enough that text on it should be light. */
    private static boolean isDark(Color c) {
        double luminance = 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
        return luminance < 128;
    }

    @Override
    public void render(MapView view, MapCanvas canvas, Player player) {
        if (rendered) return;
        rendered = true;

        // ── Build the image in Java2D ──────────────────────────────────────
        BufferedImage img = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        // Background (player-chosen, default white). Text colours adapt for contrast.
        boolean darkBg = isDark(background);
        Color fg    = darkBg ? Color.WHITE : Color.BLACK;
        Color faint = darkBg ? new Color(255, 255, 255, 140) : new Color(0, 0, 0, 140);

        g.setColor(background);
        g.fillRect(0, 0, MAP_SIZE, MAP_SIZE);

        // Thin border
        g.setColor(faint);
        g.drawRect(0, 0, MAP_SIZE - 1, MAP_SIZE - 1);

        // Enable antialiasing for nicer text
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // ── "to [Name]" header (always size 10; only the body scales) ──────
        Font headerFont = MapFont.derive(Font.ITALIC, 10f);
        g.setFont(headerFont);
        g.setColor(fg);
        String header = "to " + recipientName + ",";
        g.drawString(header, 6, 14);

        // Separator line
        g.setColor(faint);
        g.drawLine(6, 17, MAP_SIZE - 6, 17);

        // ── Message body (centred, kept below the header) ──────────────────
        Font bodyFont = MapFont.derive(Font.PLAIN, bodyFontSize);
        g.setFont(bodyFont);
        g.setColor(fg);
        FontMetrics bodyFm = g.getFontMetrics();

        List<String> lines = wrapText(message, bodyFm, MAP_SIZE - 12);

        int lineHeight = bodyFm.getHeight();
        int areaBottom = MAP_SIZE - 18;   // leave room for the date/watermark footer
        // Start the first line just below the separator. Because the baseline is measured from the
        // font's ascent, the TOP of the text is always at areaTop (21) no matter how big the font
        // is — so the body can never reach the header. Left-aligned, flowing top-down.
        int areaTop = 21;
        int baseline = areaTop + bodyFm.getAscent();

        for (String line : lines) {
            if (baseline > areaBottom) break; // don't overrun the footer
            g.drawString(line, 6, baseline);
            baseline += lineHeight;
        }

        // ── Date footer (mm/dd/yy) ─────────────────────────────────────────
        if (timestamp > 0) {
            g.setFont(MapFont.derive(Font.PLAIN, 7f));
            g.setColor(faint);
            String date = DATE_FMT.format(Instant.ofEpochMilli(timestamp));
            FontMetrics dateFm = g.getFontMetrics();
            int dateX = MAP_SIZE - 6 - dateFm.stringWidth(date);
            g.drawString(date, dateX, MAP_SIZE - 5);
        }

        // ── Watermark (bottom-left) ────────────────────────────────────────
        g.setFont(MapFont.derive(Font.PLAIN, 6f));
        g.setColor(faint);
        g.drawString(WATERMARK, 4, MAP_SIZE - 4);

        g.dispose();

        // ── Paint to Minecraft map canvas ─────────────────────────────────
        canvas.drawImage(0, 0, img);
    }

    /** Wraps text to fit within maxWidth pixels using the given FontMetrics. */
    private List<String> wrapText(String text, FontMetrics fm, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            String test = current.length() == 0 ? word : current + " " + word;
            if (fm.stringWidth(test) <= maxWidth) {
                if (current.length() > 0) current.append(" ");
                current.append(word);
            } else {
                if (current.length() > 0) lines.add(current.toString());
                current = new StringBuilder(word);
            }
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }
}
