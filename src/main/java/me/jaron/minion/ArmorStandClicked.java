package me.jaron.minion;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataType;

public class ArmorStandClicked implements Listener {
    private final MinionPlugin plugin;

    public ArmorStandClicked(MinionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onArmorStandClicked(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand armorStand)) {
            return;
        }

        if (armorStand.getPersistentDataContainer().has(plugin.isMinionKey, PersistentDataType.BYTE)) {
            Player player = event.getPlayer();
            event.setCancelled(true);

            Minion minion = new Minion(plugin, armorStand);
            Inventory minionInventory = minion.getActionInventory();
            player.openInventory(minionInventory);
        }
    }
}