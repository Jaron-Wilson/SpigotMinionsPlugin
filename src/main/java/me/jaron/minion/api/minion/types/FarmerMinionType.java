package me.jaron.minion.api.minion.types;

import me.jaron.minion.FarmerState;
import me.jaron.minion.api.minion.IMinionType;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FarmerMinionType implements IMinionType {

    private static final List<Material> COMPATIBLE_CROPS = Arrays.asList(
        Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
        Material.MELON, Material.PUMPKIN, Material.SUGAR_CANE, Material.CACTUS
    );

    private static final Map<Material, Material> CROP_TO_SEED_MAP = new HashMap<>();

    static {
        CROP_TO_SEED_MAP.put(Material.WHEAT, Material.WHEAT_SEEDS);
        CROP_TO_SEED_MAP.put(Material.CARROTS, Material.CARROT);
        CROP_TO_SEED_MAP.put(Material.POTATOES, Material.POTATO);
        CROP_TO_SEED_MAP.put(Material.BEETROOTS, Material.BEETROOT_SEEDS);
        CROP_TO_SEED_MAP.put(Material.MELON, Material.MELON_SEEDS);
        CROP_TO_SEED_MAP.put(Material.PUMPKIN, Material.PUMPKIN_SEEDS);
        // Sugar cane and cactus use themselves as seeds
        CROP_TO_SEED_MAP.put(Material.SUGAR_CANE, Material.SUGAR_CANE);
        CROP_TO_SEED_MAP.put(Material.CACTUS, Material.CACTUS);
    }

    @Override
    public String getId() {
        return "farmer";
    }

    @Override
    public String getDisplayName(int tier) {
        return ChatColor.GREEN + "Farmer Minion " + ChatColor.YELLOW + "(Tier " + tier + ")";
    }

    @Override
    public int getMaxTier() {
        return 5;
    }

    @Override
    public long getBaseDelay(int tier) {
        return 20 * 60 / tier; // 60 seconds base, reduced by tier
    }

    @Override
    public double getEfficiency(int tier) {
        return 1.0 + (tier * 0.15); // 15% increase per tier
    }

    @Override
    public ItemStack getIcon() {
        ItemStack icon = new ItemStack(Material.IRON_HOE);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "Farmer Minion");
            icon.setItemMeta(meta);
        }
        return icon;
    }

    @Override
    public Material getPrimaryMaterial() {
        return Material.WHEAT;
    }

    @Override
    public boolean executeTask(ArmorStand armorStand, Location location) {
        // Look for fully grown crops in a 3x3 area
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block block = location.clone().add(x, -1, z).getBlock();

                if (block.getBlockData() instanceof Ageable cropData) {
                    if (COMPATIBLE_CROPS.contains(block.getType()) &&
                        cropData.getAge() == cropData.getMaximumAge()) {

                        // Harvest the crop
                        Material cropType = block.getType();
                        Material seedType = CROP_TO_SEED_MAP.getOrDefault(cropType, Material.WHEAT_SEEDS);

                        // Drop the appropriate items based on crop type
                        if (cropType == Material.WHEAT) {
                            location.getWorld().dropItemNaturally(location, new ItemStack(Material.WHEAT));
                            location.getWorld().dropItemNaturally(location, new ItemStack(seedType));
                        } else if (cropType == Material.BEETROOTS) {
                            location.getWorld().dropItemNaturally(location, new ItemStack(Material.BEETROOT));
                            location.getWorld().dropItemNaturally(location, new ItemStack(seedType));
                        } else {
                            location.getWorld().dropItemNaturally(location, new ItemStack(cropType));
                        }

                        // Reset the crop age to 0
                        cropData.setAge(0);
                        block.setBlockData(cropData);

                        return true;
                    }
                } else if (block.getType() == Material.MELON || block.getType() == Material.PUMPKIN) {
                    // Handle melon and pumpkin blocks
                    Material blockType = block.getType();
                    Material dropType = blockType;

                    block.setType(Material.AIR);
                    location.getWorld().dropItemNaturally(location, new ItemStack(dropType));

                    return true;
                } else if (block.getType() == Material.SUGAR_CANE || block.getType() == Material.CACTUS) {
                    // Check if there's a block of the same type below
                    Block blockBelow = block.getLocation().clone().add(0, -1, 0).getBlock();
                    if (blockBelow.getType() == block.getType()) {
                        // Only harvest if there's at least 2 blocks tall
                        block.setType(Material.AIR);
                        location.getWorld().dropItemNaturally(location, new ItemStack(block.getType()));
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Override
    public String getMessage(String action, int tier) {
        if ("harvesting".equals(action)) {
            return ChatColor.GREEN + "Harvesting...";
        } else if ("planting".equals(action)) {
            return ChatColor.GREEN + "Planting...";
        }
        return ChatColor.GREEN + "Farming...";
    }

    @Override
    public boolean isCompatibleWith(Material material) {
        return COMPATIBLE_CROPS.contains(material) ||
               material == Material.FARMLAND ||
               material == Material.DIRT ||
               material == Material.GRASS_BLOCK ||
               material == Material.SAND;
    }

    @Override
    public ItemStack createSpawnEgg(int tier) {
        ItemStack egg = new ItemStack(Material.VILLAGER_SPAWN_EGG);
        ItemMeta meta = egg.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(getDisplayName(tier));
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Place this egg to spawn a",
                ChatColor.GRAY + "Farmer minion (Tier " + tier + ")"
            ));
            egg.setItemMeta(meta);
        }
        return egg;
    }
}
