package com.unsentplugin;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class UnsentPlugin extends JavaPlugin {

    private MessageStore messageStore;
    private WordFilter wordFilter;
    private MapStore mapStore;
    private PlayerStore playerStore;
    private AiModerator aiModerator;
    private BlockWhitelist blockWhitelist;
    private UsernameValidator usernameValidator;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ConfigUpdater.update(this); // merge in new options if the bundled config is newer

        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();

        messageStore   = new MessageStore(this);
        wordFilter     = new WordFilter(this);
        mapStore       = new MapStore(this);
        playerStore    = new PlayerStore(this);
        aiModerator    = new AiModerator(this);
        blockWhitelist = new BlockWhitelist(this);
        usernameValidator = new UsernameValidator(this);

        if (getConfig().getBoolean("ai-moderation.enabled", false) && !aiModerator.isEnabled()) {
            getLogger().warning("ai-moderation is enabled but no api-key is set — AI checks are inactive "
                    + "until you add a key in config.yml. The blocked-words filter still applies.");
        }

        UnsentCommand  unsentCmd  = new UnsentCommand(this);
        ReadCommand    readCmd    = new ReadCommand(this);
        RecoverCommand recoverCmd = new RecoverCommand(this);
        AdminCommand   adminCmd   = new AdminCommand(this);
        ReloadCommand  reloadCmd  = new ReloadCommand(this);

        getCommand("unsent").setExecutor(unsentCmd);
        getCommand("unsent").setTabCompleter(unsentCmd);
        getCommand("unsentread").setExecutor(readCmd);
        getCommand("unsentread").setTabCompleter(readCmd);
        getCommand("unsentrecover").setExecutor(recoverCmd);
        getCommand("unsentrecover").setTabCompleter(recoverCmd);
        getCommand("unsentadmin").setExecutor(adminCmd);
        getCommand("unsentadmin").setTabCompleter(adminCmd);
        getCommand("unsentreload").setExecutor(reloadCmd);

        getServer().getPluginManager().registerEvents(new ItemFrameListener(this), this);
        getServer().getPluginManager().registerEvents(new ColorPromptListener(this, unsentCmd), this);
        getServer().getPluginManager().registerEvents(new NoteVoucherListener(this), this);

        // Regenerate note credits and refresh each online player's voucher once a minute.
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (!getConfig().getBoolean("note-credits.enabled", true)) return;
            for (org.bukkit.entity.Player online : getServer().getOnlinePlayers()) {
                NoteVoucher.sync(this, online);
            }
        }, 20L * 60, 20L * 60);

        // Runtime map renderers aren't saved with the world, so existing unsent maps would render
        // blank after a restart. Re-attach them once the server is fully up (next tick).
        getServer().getScheduler().runTask(this, () -> {
            int restored = mapStore.rehydrateAll();
            if (restored > 0) getLogger().info("Restored renderers for " + restored + " unsent map(s).");
        });

        getLogger().info("UnsentPlugin enabled. Messages will be saved to " + dataFolder.getPath());
    }

    @Override
    public void onDisable() {
        getLogger().info("UnsentPlugin disabled.");
    }

    public MessageStore  getMessageStore()  { return messageStore;  }
    public WordFilter    getWordFilter()    { return wordFilter;    }
    public MapStore      getMapStore()      { return mapStore;      }
    public PlayerStore   getPlayerStore()   { return playerStore;   }
    public AiModerator   getAiModerator()   { return aiModerator;   }
    public BlockWhitelist getBlockWhitelist() { return blockWhitelist; }
    public UsernameValidator getUsernameValidator() { return usernameValidator; }
}
