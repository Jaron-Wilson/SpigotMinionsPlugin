package me.jaron.minion;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class BundleInteractListener implements Listener {
    private final MinionPlugin plugin;

    public BundleInteractListener(MinionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        if (plugin.getBundleManager().isBundle(item)) {
            event.setCancelled(true);
            event.getPlayer().openInventory(plugin.getBundleManager().getBundleInventory(event.getPlayer()));
        }
    }
}
