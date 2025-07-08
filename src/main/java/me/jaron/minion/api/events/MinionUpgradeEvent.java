package me.jaron.minion.api.events;

import me.jaron.minion.Minion;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class MinionUpgradeEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled;
    private final Minion minion;
    private final ArmorStand armorStand;
    private int oldTier;
    private int newTier;

    public MinionUpgradeEvent(Minion minion, ArmorStand armorStand, int oldTier, int newTier) {
        this.minion = minion;
        this.armorStand = armorStand;
        this.oldTier = oldTier;
        this.newTier = newTier;
        this.cancelled = false;
    }

    public Minion getMinion() {
        return minion;
    }

    public ArmorStand getArmorStand() {
        return armorStand;
    }

    public int getOldTier() {
        return oldTier;
    }

    public int getNewTier() {
        return newTier;
    }

    public void setNewTier(int newTier) {
        this.newTier = newTier;
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
