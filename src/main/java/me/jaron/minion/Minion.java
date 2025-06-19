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
    private void processCell(boolean isMine) {
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

        Player owner = Bukkit.getPlayer(persistentDataContainer.get(plugin.ownerKey, PersistentDataType.STRING));
        Inventory storage = plugin.getMinionStorage(minionArmorStand.getUniqueId());

        Inventory chestInv = null;
        chestInv = checkForChest(world,loc);

        boolean didSomething = false;
        String actionPrefix = isMine ? "Minion Storage is: " : "Planting.... Minion Storage is: ";
        Inventory targetInventory = isMine ? storage : chestInv;
//        Bukkit.broadcastMessage(actionPrefix + targetInventory.firstEmpty());

        if (block.getType() == mat) {
            minionArmorStand.setCustomName(ChatColor.RED + "Mining" + (isMine ? "" : "..."));
            didSomething = minionMineEvent(block, chestInv, storage, world);
        } else if (block.getType() == Material.AIR) {
            block.setType(mat);
            minionArmorStand.setCustomName(ChatColor.GREEN + "Planting" + (isMine ? "" : "..."));
            minionArmorStand.swingOffHand();
            didSomething = true;
        }

        if (!didSomething){
            // neither action possible
            if (isMine) {
                minionArmorStand.setCustomName(ChatColor.RED + "Need " + mat.name());
            } else {
                minionArmorStand.setCustomName(ChatColor.RED + "Occupied: " + block.getType());
            }
        }
        minionArmorStand.setCustomNameVisible(true);
        // advance pointer
        persistentDataContainer.set(plugin.indexKey, PersistentDataType.INTEGER, (idx+1)%9);
    }


    boolean chestFull = false;
    boolean minionFull = false;

    public Boolean minionMineEvent(Block block, Inventory chestInv, Inventory storage, World world) {
        minionArmorStand.swingMainHand();
        block.getDrops().forEach(d -> {
            Map<Integer, ItemStack> left;
            if (chestInv != null && storage != null) {
                if (chestInv.firstEmpty() == -1) {
//                    Says its full but lets check the quantity in there, we might still have space!
                    ItemStack[] chestContents = chestInv.getContents();
                    ItemStack[] minionStorage = storage.getContents();

                    if (chestFull && minionFull) {
                        Bukkit.broadcastMessage(ChatColor.RED + "The Storages Are full!");
                    }else {
                        if (!chestFull) {
                            for (ItemStack content : chestContents) {
                                if (content.getAmount() != content.getMaxStackSize()) {
                                    Bukkit.broadcastMessage(ChatColor.GREEN + "We still have space!");
                                    chestFull = false;
                                } else {
                                    Bukkit.broadcastMessage(ChatColor.BLUE + "No more space look! \n" + content);
                                    chestFull = true;
                                }
                            }
                        } else if (!minionFull) {
                            for (ItemStack content : minionStorage) {
                                if (content.getAmount() != content.getMaxStackSize()) {
                                    Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "We still have space!");
                                    minionFull = false;
                                } else {
                                    Bukkit.broadcastMessage(ChatColor.GRAY + "No more space look! \n" + content);
                                    minionFull = true;
                                }
                            }

                        }
                    }


                    if (storage.firstEmpty() == -1) {
                        Bukkit.broadcastMessage("Minions inventorys are full");
                        minionArmorStand.setCustomName(ChatColor.DARK_AQUA + "!Inventorys Are full!");
                        left = null;
                    }
                    else {
                        Bukkit.broadcastMessage("Minion chest is full");
                        minionArmorStand.setCustomName(ChatColor.DARK_AQUA + "!Chest is full!");
                        left = storage.addItem(d);
                    }
                }
                else {
                    left = chestInv.addItem(d);
                }
            } else {
                left = storage.addItem(d);
            }
            if (left != null) {
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
            // random mine or plant
            boolean mine = ThreadLocalRandom.current().nextBoolean();
            processCell(mine);
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
