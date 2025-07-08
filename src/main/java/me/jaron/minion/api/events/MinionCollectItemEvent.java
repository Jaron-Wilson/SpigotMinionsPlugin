package me.jaron.minion.api.events;

import me.jaron.minion.Minion;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Event fired when a minion collects an item
 */
public class MinionCollectItemEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled;
    private final Minion minion;
    private final ArmorStand armorStand;
    private ItemStack item;

    public MinionCollectItemEvent(Minion minion, ArmorStand armorStand, ItemStack item) {
        this.minion = minion;
        this.armorStand = armorStand;
        this.item = item;
        this.cancelled = false;
    }

    /**
     * Get the minion that collected the item
     * @return The minion instance
     */
    public Minion getMinion() {
        return minion;
    }

    /**
     * Get the armor stand representing the minion
     * @return The armor stand entity
     */
    public ArmorStand getArmorStand() {
        return armorStand;
    }

    /**
     * Get the item being collected
     * @return The ItemStack being collected
     */
    public ItemStack getItem() {
        return item;
    }

    /**
     * Set the item to be collected
     * @param item The new ItemStack to collect
     */
    public void setItem(ItemStack item) {
        this.item = item;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
