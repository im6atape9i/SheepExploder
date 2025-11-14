package com.example.sheepexplode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

public class Recipes {
    public static void registerAll(SheepExplodePlugin plugin) {
        plugin.getServer().addRecipe(createChargeStickRecipe(plugin));
    }

    private static ShapedRecipe createChargeStickRecipe(JavaPlugin plugin) {
        FileConfiguration cfg = plugin.getConfig();

        final String chargeItemType = cfg.getString("charge-item", "TNT");
        final String chargeItemName = cfg.getString("charge-item-name", "TNT");
        final String chargeItemLore = cfg.getString("charge-item-lore", "???");


        ItemStack item = new ItemStack(Material.valueOf(chargeItemType));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(chargeItemName);
            meta.setLore(Arrays.asList(chargeItemLore));
        item.setItemMeta(meta);

        NamespacedKey key = new NamespacedKey(plugin, "charge_stick");
        ShapedRecipe recipe = new ShapedRecipe(key, item);
        recipe.shape("WWW","WTW","WWW");
        recipe.setIngredient('W', Material.WHEAT);
        recipe.setIngredient('T', Material.TNT);

        return recipe;
    }
}
