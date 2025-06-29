package me.jaron.minion;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;

public class MinionSpawnListener implements Listener {

    private final MinionPlugin plugin;

    public MinionSpawnListener(MinionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack itemInHand = event.getItem();
        Player player = event.getPlayer();

        if (itemInHand != null && itemInHand.hasItemMeta()) {
            if (itemInHand.getItemMeta().getPersistentDataContainer().has(plugin.minionEggKey, PersistentDataType.BYTE)) {
                event.setCancelled(true);

                Location spawnLocation = Objects.requireNonNull(event.getClickedBlock()).getLocation().add(0.5, 1.0, 0.5);
                spawnLocation.setYaw(player.getLocation().getYaw());

                plugin.spawnMinion(player, spawnLocation);

                if (player.getGameMode() != GameMode.CREATIVE) {
                    itemInHand.setAmount(itemInHand.getAmount() - 1);
                }
            }
        }
    }


}