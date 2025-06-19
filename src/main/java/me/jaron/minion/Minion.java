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

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class Minion {

    private final MinionPlugin plugin;
    private final ArmorStand minionArmorStand;
    private BukkitTask miningTask;

    private static final int SELECTOR_SLOT = 8;

    public Minion(MinionPlugin plugin, ArmorStand minionArmorStand) {
        this.plugin = plugin;
        this.minionArmorStand = minionArmorStand;
    }

    public static ItemStack createBackButton() {
        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta meta = barrier.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Back");
            barrier.setItemMeta(meta);
        }
        return barrier;
    }

    public static ItemStack createCollectButton(String text) {
        ItemStack hopper = new ItemStack(Material.HOPPER);
        ItemMeta meta = hopper.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + text);
            hopper.setItemMeta(meta);
        }
        return hopper;
    }

    public Inventory getActionInventory() {
        Inventory inv = Bukkit.createInventory(new MinionInventoryHolder(minionArmorStand.getUniqueId()), 9, "Minion Control Panel");
        ItemStack mineItem = createItem(Material.DIAMOND_PICKAXE, ChatColor.GOLD + "Mine Once");
        ItemStack selector = createItem(Material.valueOf(minionArmorStand.getPersistentDataContainer()
            .getOrDefault(plugin.targetKey, PersistentDataType.STRING, Material.COBBLESTONE.name())),
            ChatColor.AQUA + "Target: " + minionArmorStand.getPersistentDataContainer()
            .getOrDefault(plugin.targetKey, PersistentDataType.STRING, Material.COBBLESTONE.name()));
        ItemStack storage = createItem(Material.CHEST, ChatColor.BLUE + "Open Minion Storage");

        inv.setItem(0, mineItem);
        inv.setItem(4, storage);
        inv.setItem(SELECTOR_SLOT - 1, selector);
        inv.setItem(SELECTOR_SLOT, createBackButton());
        return inv;
    }

    public Inventory getMinionStorage() {
        Inventory storage = plugin.getMinionStorage(minionArmorStand.getUniqueId());
        storage.setItem(storage.getSize() - 1, createBackButton());
        storage.setItem(storage.getSize() - 2, createCollectButton("Collect All"));
        storage.setItem(storage.getSize() - 3, createCollectButton("Collect from Chest"));
        return storage;
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
    private void processCell(Boolean forceMine) {
        if (minionArmorStand.isDead()) return;
        World world = minionArmorStand.getWorld();
        var persistentDataContainer = minionArmorStand.getPersistentDataContainer();
        String target = persistentDataContainer.getOrDefault(plugin.targetKey, PersistentDataType.STRING, Material.COBBLESTONE.name());
        Material mat = Material.valueOf(target);
        int idx = persistentDataContainer.getOrDefault(plugin.indexKey, PersistentDataType.INTEGER, 0);
        Location loc = minionArmorStand.getLocation();
        Block block = world.getBlockAt(loc.getBlockX() + (idx%3)-1,
            loc.getBlockY()-1, loc.getBlockZ() + (idx/3)-1);

        if (idx == 4) {
            persistentDataContainer.set(plugin.indexKey, PersistentDataType.INTEGER, (idx+1)%9);
            return;
        }

        Inventory storage = plugin.getMinionStorage(minionArmorStand.getUniqueId());
        if (storage == null) return;

        Inventory chestInv = checkForChest(world,loc);

        boolean didSomething = false;

        if (forceMine != null) { // Manual override from commands
            if (forceMine) { // Mine Once
                if (block.getType() == mat) {
                    didSomething = minionMineEvent(block, chestInv, storage, world);
                }
            } else { // Plant Once
                if (block.getType() == Material.AIR) {
                    didSomething = plantBlock(block, mat);
                }
            }
        } else { // Automation logic
            boolean storageSystemFull = isStorageFull(mat, storage, chestInv);
            if (storageSystemFull) {
                // If storage is full, only plant
                if (block.getType() == Material.AIR) {
                    didSomething = plantBlock(block, mat);
                }
            } else {
                // If storage is not full, prioritize mining, then planting
                if (block.getType() == mat) {
                    didSomething = minionMineEvent(block, chestInv, storage, world);
                } else if (block.getType() == Material.AIR) {
                    didSomething = plantBlock(block, mat);
                }
            }
        }

        if (!didSomething) {
            boolean storageSystemFull = isStorageFull(mat, storage, chestInv);
            if (storageSystemFull) {
                minionArmorStand.setCustomName(ChatColor.RED + "Storage Full!");
            } else if (block.getType() != mat && block.getType() != Material.AIR) {
                minionArmorStand.setCustomName(ChatColor.RED + "Occupied: " + block.getType());
            } else {
                minionArmorStand.setCustomName(ChatColor.RED + "Need " + mat.name());
            }
        }

        minionArmorStand.setCustomNameVisible(true);
        persistentDataContainer.set(plugin.indexKey, PersistentDataType.INTEGER, (idx+1)%9);
    }

    private boolean plantBlock(Block block, Material mat) {
        minionArmorStand.setCustomName(ChatColor.GREEN + "Planting");
        block.setType(mat);
        return true;
    }

    private boolean isStorageFull(Material mat, Inventory storage, Inventory chestInv) {
        boolean minionStorageFull = isFull(storage, mat);
        if (chestInv != null) {
            boolean chestFull = isFull(chestInv, mat);
            return chestFull && minionStorageFull;
        }
        return minionStorageFull;
    }

    private boolean isFull(Inventory inv, Material mat) {
        if (inv.firstEmpty() != -1) return false;
        return Arrays.stream(inv.getContents())
            .filter(item -> item != null && item.getType() == mat)
            .allMatch(item -> item.getAmount() >= item.getMaxStackSize());
    }

    public Boolean minionMineEvent(Block block, Inventory chestInv, Inventory storage, World world) {
        if (storage == null) return false;

        minionArmorStand.setCustomName(ChatColor.GOLD + "Mining");
        minionArmorStand.swingMainHand();
        block.getDrops().forEach(d -> {
            Map<Integer, ItemStack> left;

            if (chestInv != null) {
                // Always try to add to chest first. addItem will handle partial stacks.
                left = chestInv.addItem(d);

                // If there are leftovers, try adding to minion storage.
                if (!left.isEmpty()) {
                    left = storage.addItem(left.values().toArray(new ItemStack[0]));
                }
            } else {
                // No chest, add directly to minion storage.
                left = storage.addItem(d);
            }

            // Drop any remaining items on the ground.
            if (left != null && !left.isEmpty()) {
                left.values().forEach(o -> world.dropItemNaturally(block.getLocation(), o));
            }
        });
        block.setType(Material.AIR);
        return true;

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
            processCell(null);
         }, 0L, 60L);
    }
    // stop automation
    public void stopAutomation() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }

    public Inventory checkForChest(World world, Location loc) {
        int[][] crossOffsets = {{0,1}, {0,-1}, {1,0}, {-1,0}};  // x,z offsets for N,S,E,W
        for (int[] offset : crossOffsets) {
            Block adj;
            adj = world.getBlockAt(loc.getBlockX() + offset[0], loc.getBlockY(), loc.getBlockZ() + offset[1]);
            if (adj.getState() instanceof Chest chest) {
//                Bukkit.broadcastMessage(ChatColor.GREEN + "Chest found! ");
                return chest.getInventory();
            }
        }
        return null;
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
