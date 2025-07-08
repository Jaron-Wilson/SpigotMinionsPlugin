package me.jaron.minion.api.events;

import me.jaron.minion.api.minion.IMinionType;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event fired when a minion is about to spawn
 */
public class MinionSpawnEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled;
    private final Player owner;
    private Location location;
    private IMinionType minionType;
    private int tier;

    public MinionSpawnEvent(Player owner, Location location, IMinionType minionType, int tier) {
        this.owner = owner;
        this.location = location;
        this.minionType = minionType;
        this.tier = tier;
        this.cancelled = false;
    }

    /**
     * Get the owner of the minion
     * @return The player who owns the minion
     */
    public Player getOwner() {
        return owner;
    }

    /**
     * Get the location where the minion will spawn
     * @return The spawn location
     */
    public Location getLocation() {
        return location;
    }

    /**
     * Set the location where the minion will spawn
     * @param location The new spawn location
     */
    public void setLocation(Location location) {
        this.location = location;
    }

    /**
     * Get the type of minion being spawned
     * @return The minion type
     */
    public IMinionType getMinionType() {
        return minionType;
    }

    /**
     * Set the type of minion to spawn
     * @param minionType The new minion type
     */
    public void setMinionType(IMinionType minionType) {
        this.minionType = minionType;
    }

    /**
     * Get the tier level of the minion
     * @return The tier level
     */
    public int getTier() {
        return tier;
    }

    /**
     * Set the tier level of the minion
     * @param tier The new tier level
     */
    public void setTier(int tier) {
        this.tier = tier;
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
