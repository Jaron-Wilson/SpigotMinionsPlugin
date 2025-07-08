package me.jaron.minion.api.events;

import me.jaron.minion.Minion;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class MinionWorkEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled;
    private final Minion minion;
    private final ArmorStand armorStand;
    private double efficiency;

    public MinionWorkEvent(Minion minion, ArmorStand armorStand, double efficiency) {
        this.minion = minion;
        this.armorStand = armorStand;
        this.efficiency = efficiency;
        this.cancelled = false;
    }

    public Minion getMinion() {
        return minion;
    }

    public ArmorStand getArmorStand() {
        return armorStand;
    }

    public double getEfficiency() {
        return efficiency;
    }

    public void setEfficiency(double efficiency) {
        this.efficiency = efficiency;
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
