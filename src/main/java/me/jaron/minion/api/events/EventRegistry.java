package me.jaron.minion.api.events;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;

/**
 * Registry for managing and firing custom events.
 * This provides a convenient way to fire events from the API.
 */
public class EventRegistry {

    /**
     * Fire a minion event
     * @param event The event to fire
     */
    public void fireEvent(Event event) {
        Bukkit.getPluginManager().callEvent(event);
    }
}
