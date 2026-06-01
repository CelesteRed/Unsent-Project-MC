package com.unsentplugin;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class UnsentPlugin extends JavaPlugin {

    private MessageStore messageStore;
    private WordFilter wordFilter;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();

        messageStore = new MessageStore(this);
        wordFilter   = new WordFilter(this);

        UnsentCommand unsentCmd  = new UnsentCommand(this);
        ReadCommand   readCmd    = new ReadCommand(this);

        getCommand("unsent").setExecutor(unsentCmd);
        getCommand("unsent").setTabCompleter(unsentCmd);
        getCommand("unsentread").setExecutor(readCmd);
        getCommand("unsentread").setTabCompleter(readCmd);

        getLogger().info("UnsentPlugin enabled. Messages will be saved to " + dataFolder.getPath());
    }

    @Override
    public void onDisable() {
        getLogger().info("UnsentPlugin disabled.");
    }

    public MessageStore getMessageStore() { return messageStore; }
    public WordFilter   getWordFilter()   { return wordFilter;   }
}
