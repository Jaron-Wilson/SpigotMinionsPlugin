package me.jaron.minion.api.minion;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Interface representing a type of minion.
 * Implement this interface to create custom minion types.
 */
public interface IMinionType {
    /**
     * Get the unique identifier for this minion type
     * @return The minion type ID
     */
    String getId();

    /**
     * Get the display name for this minion type at a specific tier
     * @param tier The tier level
     * @return The formatted display name
     */
    String getDisplayName(int tier);

    /**
     * Get the maximum tier level for this minion type
     * @return The maximum tier level
     */
    int getMaxTier();

    /**
     * Get the base delay between actions for this minion type (in ticks)
     * @param tier The tier level
     * @return The delay in ticks
     */
    long getBaseDelay(int tier);

    /**
     * Get the efficiency multiplier for this minion type
     * @param tier The tier level
     * @return The efficiency multiplier
     */
    double getEfficiency(int tier);

    /**
     * Get the icon representing this minion type
     * @return The ItemStack icon
     */
    ItemStack getIcon();

    /**
     * Get the material this minion type primarily works with
     * @return The primary material
     */
    Material getPrimaryMaterial();

    /**
     * Execute the minion's task
     * @param armorStand The armor stand representing the minion
     * @param location The location of the minion
     * @return True if the action was successful, false otherwise
     */
    boolean executeTask(ArmorStand armorStand, Location location);

    /**
     * Get a random message for a specific action
     * @param action The action type (e.g., "mining", "harvesting")
     * @param tier The tier level
     * @return A formatted message string
     */
    String getMessage(String action, int tier);

    /**
     * Check if this minion type can work with the given material
     * @param material The material to check
     * @return True if compatible, false otherwise
     */
    boolean isCompatibleWith(Material material);

    /**
     * Create a spawn egg for this minion type
     * @param tier The tier level
     * @return The spawn egg ItemStack
     */
    ItemStack createSpawnEgg(int tier);
}
