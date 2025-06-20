package me.jaron.minion;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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

    @EventHandler
    public void onMinionDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof ArmorStand armorStand) {
            if (armorStand.getPersistentDataContainer().has(plugin.isMinionKey, PersistentDataType.BYTE)) {
                if (event.getDamager() instanceof Player) {
                    event.setCancelled(true);
                } else if (event.getDamager() instanceof Projectile projectile) {
                    if (projectile.getShooter() instanceof Player) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        for (Entity entity : block.getWorld().getNearbyEntities(block.getLocation().add(0.5, 1, 0.5), 0.1, 0.1, 0.1)) {
            if (entity instanceof ArmorStand armorStand) {
                if (armorStand.getPersistentDataContainer().has(plugin.isMinionKey, PersistentDataType.BYTE)) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(ChatColor.RED + "You cannot break the block under a minion!");
                }
            }
        }
    }
}