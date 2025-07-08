package me.jaron.minion.api.events;

import me.jaron.minion.Minion;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event fired after a minion has been successfully spawned
 */
public class MinionSpawnedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Minion minion;
    private final ArmorStand armorStand;
    private final Player owner;

    public MinionSpawnedEvent(Minion minion, ArmorStand armorStand, Player owner) {
        this.minion = minion;
        this.armorStand = armorStand;
        this.owner = owner;
    }

    /**
     * Get the minion that was spawned
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
     * Get the owner of the minion
     * @return The player who owns the minion
     */
    public Player getOwner() {
        return owner;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
