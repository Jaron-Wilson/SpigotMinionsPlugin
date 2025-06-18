package me.jaron.minion;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class GiveMinionEggCommand implements CommandExecutor {

    private final MinionPlugin plugin;

    public GiveMinionEggCommand(MinionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        player.getInventory().addItem(createMinionSpawnEgg());
        player.sendMessage(ChatColor.GREEN + "You have received a Minion Spawn Egg!");
        return true;
    }

    private ItemStack createMinionSpawnEgg() {
        ItemStack spawnEgg = new ItemStack(Material.PIG_SPAWN_EGG);
        ItemMeta meta = spawnEgg.getItemMeta();

        // Use legacy ChatColor for display name and lore
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Minion Spawn Egg");
        meta.setLore(List.of(
                ChatColor.GRAY + "Place this egg on a block",
                ChatColor.GRAY + "to spawn a CobbleMinion!"
        ));

        // Add a persistent data tag to identify this item as our special egg.
        meta.getPersistentDataContainer().set(plugin.minionEggKey, PersistentDataType.BYTE, (byte) 1);

        spawnEgg.setItemMeta(meta);
        return spawnEgg;
    }
}