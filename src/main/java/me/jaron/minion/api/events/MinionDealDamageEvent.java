package me.jaron.minion.api.events;

import me.jaron.minion.Minion;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class MinionDealDamageEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled;
    private final Minion minion;
    private final ArmorStand armorStand;
    private Entity target;
    private double damage;

    public MinionDealDamageEvent(Minion minion, ArmorStand armorStand, Entity target, double damage) {
        this.minion = minion;
        this.armorStand = armorStand;
        this.target = target;
        this.damage = damage;
        this.cancelled = false;
    }

    public Minion getMinion() {
        return minion;
    }

    public ArmorStand getArmorStand() {
        return armorStand;
    }

    public Entity getTarget() {
        return target;
    }

    public void setTarget(Entity target) {
        this.target = target;
    }

    public double getDamage() {
        return damage;
    }

    public void setDamage(double damage) {
        this.damage = damage;
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
