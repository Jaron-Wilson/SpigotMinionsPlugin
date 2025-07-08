package me.jaron.minion.api.minion.types;

import me.jaron.minion.api.minion.IMinionType;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public class BlockMinerMinionType implements IMinionType {

    private static final List<Material> COMPATIBLE_MATERIALS = Arrays.asList(
        Material.STONE, Material.COBBLESTONE, Material.COAL_ORE, Material.IRON_ORE,
        Material.GOLD_ORE, Material.DIAMOND_ORE, Material.EMERALD_ORE,
        Material.DEEPSLATE, Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_IRON_ORE,
        Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE_EMERALD_ORE
    );

    @Override
    public String getId() {
        return "block_miner";
    }

    @Override
    public String getDisplayName(int tier) {
        return ChatColor.GOLD + "Block Miner Minion " + ChatColor.GREEN + "(Tier " + tier + ")";
    }

    @Override
    public int getMaxTier() {
        return 5;
    }

    @Override
    public long getBaseDelay(int tier) {
        return 20 * 30 / tier; // 30 seconds base, reduced by tier
    }

    @Override
    public double getEfficiency(int tier) {
        return 1.0 + (tier * 0.2); // 20% increase per tier
    }

    @Override
    public ItemStack getIcon() {
        ItemStack icon = new ItemStack(Material.IRON_PICKAXE);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Block Miner Minion");
            icon.setItemMeta(meta);
        }
        return icon;
    }

    @Override
    public Material getPrimaryMaterial() {
        return Material.COBBLESTONE;
    }

    @Override
    public boolean executeTask(ArmorStand armorStand, Location location) {
        Block targetBlock = location.clone().add(0, -1, 0).getBlock();
        if (!isCompatibleWith(targetBlock.getType())) {
            return false;
        }

        // Replace the block with air and drop the appropriate item
        Material blockType = targetBlock.getType();
        ItemStack drop = new ItemStack(blockType);
        targetBlock.setType(Material.AIR);
        location.getWorld().dropItemNaturally(location, drop);

        return true;
    }

    @Override
    public String getMessage(String action, int tier) {
        if ("mining".equals(action)) {
            return ChatColor.GRAY + "Mining...";
        }
        return ChatColor.GRAY + "Working...";
    }

    @Override
    public boolean isCompatibleWith(Material material) {
        return COMPATIBLE_MATERIALS.contains(material);
    }

    @Override
    public ItemStack createSpawnEgg(int tier) {
        ItemStack egg = new ItemStack(Material.VILLAGER_SPAWN_EGG);
        ItemMeta meta = egg.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(getDisplayName(tier));
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Place this egg to spawn a",
                ChatColor.GRAY + "Block Miner minion (Tier " + tier + ")"
            ));
            egg.setItemMeta(meta);
        }
        return egg;
    }
}
