package com.example.sheepexplode;

import org.bukkit.plugin.java.JavaPlugin;

public class SheepExplodePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();

        Recipes.registerAll(this);

        getCommand("sheepexplode").setExecutor((sender, command, label, args) -> {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                sender.sendMessage("§a[SheepExplode] Конфиг перезагружен!");
                return true;
            }
            sender.sendMessage("§eИспользуй: /sheepexplode reload");
            return true;
        });

        getServer().getPluginManager().registerEvents(new SheepInteractListener(this), this);
    }

    @Override
    public void onDisable() {
        getLogger().info("SheepExplode disabled");
    }
}
