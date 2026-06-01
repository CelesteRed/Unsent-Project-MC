package com.unsentplugin;

import java.util.List;

public class WordFilter {

    private final UnsentPlugin plugin;

    public WordFilter(UnsentPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Returns true if the message contains a blocked word/phrase.
     */
    public boolean isBlocked(String message) {
        String lower = message.toLowerCase();
        List<String> blocked = plugin.getConfig().getStringList("blocked-words");
        for (String word : blocked) {
            if (lower.contains(word.toLowerCase())) return true;
        }
        return false;
    }

    /**
     * Returns true if the name looks valid (letters, digits, underscores, 1-16 chars).
     */
    public boolean isValidName(String name) {
        return name.matches("[a-zA-Z0-9_]{1,16}");
    }
}
