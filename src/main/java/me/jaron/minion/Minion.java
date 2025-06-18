package me.jaron.minion;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class Minion {

    private final MinionPlugin plugin;
    private final ArmorStand minionArmorStand;
    private BukkitTask miningTask;

    private static final int SELECTOR_SLOT = 1;

    public Minion(MinionPlugin plugin, ArmorStand minionArmorStand) {
        this.plugin = plugin;
        this.minionArmorStand = minionArmorStand;
    }

    public Inventory getActionInventory() {
        // build UI: Mine Once, Target selector, Plant Once, Storage
        Inventory inv = Bukkit.createInventory(new MinionInventoryHolder(minionArmorStand.getUniqueId()), 9, "Minion Control Panel");
        // mode button
        byte mode = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.modeKey, PersistentDataType.BYTE, (byte)0);
        String modeName = (mode==0 ? "Mine" : "Plant");
        ItemStack modeItem = createItem(Material.REDSTONE_TORCH, ChatColor.YELLOW + "Mode: " + modeName);
        ItemStack mineItem = createItem(Material.DIAMOND_PICKAXE, ChatColor.GOLD + "Mine Once");
        ItemStack selector = createItem(Material.valueOf(minionArmorStand.getPersistentDataContainer()
            .getOrDefault(plugin.targetKey, PersistentDataType.STRING, Material.COBBLESTONE.name())),
            ChatColor.AQUA + "Target: " + minionArmorStand.getPersistentDataContainer()
            .getOrDefault(plugin.targetKey, PersistentDataType.STRING, Material.COBBLESTONE.name()));
        ItemStack plantItem = createItem(Material.WHEAT_SEEDS, ChatColor.GREEN + "Plant Once");
        ItemStack storage = createItem(Material.CHEST, ChatColor.BLUE + "Open Minion Storage");
        inv.setItem(0, mineItem);
        inv.setItem(SELECTOR_SLOT, selector);
        inv.setItem(2, plantItem);
        inv.setItem(4, storage);
        inv.setItem(6, modeItem);
        return inv;
    }

    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name); // Use setDisplayName for legacy strings
            item.setItemMeta(meta);
        }
        return item;
    }

    // Mine single cell
    public void mineBlock() {
        processCell(true);
    }
    // Plant single cell
    public void plantBlock() {
        processCell(false);
    }
    private void processCell(boolean isMine) {
        if (minionArmorStand.isDead()) return;
        World world = minionArmorStand.getWorld();
        var pdc = minionArmorStand.getPersistentDataContainer();
        String target = pdc.getOrDefault(plugin.targetKey, PersistentDataType.STRING, Material.COBBLESTONE.name());
        Material mat = Material.valueOf(target);
        int idx = pdc.getOrDefault(plugin.indexKey, PersistentDataType.INTEGER, 0);
        Location loc = minionArmorStand.getLocation();
        Block b = world.getBlockAt(loc.getBlockX() + (idx%3)-1,
            loc.getBlockY()-1, loc.getBlockZ() + (idx/3)-1);

        // Skip if this is the block directly below minion (center block)
        if (idx == 4) {
            pdc.set(plugin.indexKey, PersistentDataType.INTEGER, (idx+1)%9);
            return;
        }

        Player owner = Bukkit.getPlayer(pdc.get(plugin.ownerKey, PersistentDataType.STRING));
        Inventory storage = plugin.getMinionStorage(minionArmorStand.getUniqueId());

        // find adjacent chest in cross pattern (N,S,E,W only)
        Inventory chestInv = null;
        int[][] crossOffsets = {{0,1}, {0,-1}, {1,0}, {-1,0}};  // x,z offsets for N,S,E,W
        for (int[] offset : crossOffsets) {
            Block adj;
            // check cardinal directions
            adj = world.getBlockAt(loc.getBlockX() + offset[0], loc.getBlockY(), loc.getBlockZ() + offset[1]);
            if (adj.getState() instanceof Chest chest) {
                chestInv = chest.getInventory();
                break;
            }
        }

        // attempt chosen action, fallback to the other
        boolean did = false;
        if (isMine) {
            if (b.getType() == mat) {
                // mine
                Inventory finalChestInv = chestInv;
                b.getDrops().forEach(d -> {
                    Map<Integer, ItemStack> left;
                    if (finalChestInv != null) {
                        left = finalChestInv.addItem(d);
                    } else {
                        left = storage.addItem(d);
                    }
                    left.values().forEach(o -> world.dropItemNaturally(b.getLocation(), o));
                });
                b.setType(Material.AIR);
                did = true;
            } else if (b.getType() == Material.AIR) {
                // fallback plant
                b.setType(mat);
                did = true;
            }
        } else {
            if (b.getType() == Material.AIR) {
                // plant
                b.setType(mat);
                did = true;
            } else if (b.getType() == mat) {
                // fallback mine
                Inventory finalChestInv1 = chestInv;
                b.getDrops().forEach(d -> {
                    Map<Integer, ItemStack> left;
                    if (finalChestInv1 != null) {
                        left = finalChestInv1.addItem(d);
                    } else {
                        left = storage.addItem(d);
                    }
                    left.values().forEach(o -> world.dropItemNaturally(b.getLocation(), o));
                });
                b.setType(Material.AIR);
                did = true;
            }
        }
        // update name based on result
        if (did) {
            minionArmorStand.setCustomName(ChatColor.YELLOW + "CobbleMinion");
        } else {
            // neither action possible
            if (isMine) {
                minionArmorStand.setCustomName(ChatColor.RED + "Need " + mat.name());
            } else {
                minionArmorStand.setCustomName(ChatColor.RED + "Occupied: " + b.getType());
            }
        }
        minionArmorStand.setCustomNameVisible(true);
        // advance pointer
        pdc.set(plugin.indexKey, PersistentDataType.INTEGER, (idx+1)%9);
    }

    /** Toggle between Mine (0) and Plant (1) modes */
    public void toggleMode() {
        var pdc = minionArmorStand.getPersistentDataContainer();
        byte curr = pdc.getOrDefault(plugin.modeKey, PersistentDataType.BYTE, (byte)0);
        pdc.set(plugin.modeKey, PersistentDataType.BYTE, (byte)(curr==0 ? 1 : 0));
    }

    // for compatibility: destroyBlock now mines one cell
    public void destroyBlock() { mineBlock(); }
    // start scheduled automation running processCell(mode)
    public void startAutomation() {
        if (miningTask != null && !miningTask.isCancelled()) return;
        miningTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // random mine or plant
            boolean mine = ThreadLocalRandom.current().nextBoolean();
            processCell(mine);
         }, 0L, 60L);
    }
    // stop automation
    public void stopAutomation() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }

    public static class MinionInventoryHolder implements InventoryHolder {
        private final UUID minionUUID;

        public MinionInventoryHolder(UUID minionUUID) {
            this.minionUUID = minionUUID;
        }

        public UUID getMinionUUID() {
            return minionUUID;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
