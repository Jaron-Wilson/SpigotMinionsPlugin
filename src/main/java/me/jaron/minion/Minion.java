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
import org.bukkit.block.data.Ageable;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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

        String minionTypeStr = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.minionTypeKey, PersistentDataType.STRING, MinionType.BLOCK_MINER.name());
        MinionType minionType = MinionType.valueOf(minionTypeStr);

        ItemStack typeItem;
        if (minionType == MinionType.BLOCK_MINER) {
            typeItem = createItem(Material.DIAMOND_PICKAXE, ChatColor.GOLD + "Block Miner");
        } else {
            typeItem = createItem(Material.DIAMOND_HOE, ChatColor.GOLD + "Farmer");
        }

        inv.setItem(0, typeItem);

        if (minionType == MinionType.FARMER) {
            boolean wantsSeeds = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.wantsSeedsKey, PersistentDataType.BYTE, (byte)1) == 1;
            ItemStack seedsToggle = createItem(Material.WHEAT_SEEDS, ChatColor.GOLD + "Collect Seeds: " + (wantsSeeds ? "On" : "Off"));
            inv.setItem(1, seedsToggle);
        }

        String targetName = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.targetKey, PersistentDataType.STRING, Material.COBBLESTONE.name());
        Material targetMaterial;
        try {
            targetMaterial = Material.valueOf(targetName);
        } catch (IllegalArgumentException e) {
            targetMaterial = Material.COBBLESTONE;
        }

        ItemStack selector = createItem(targetMaterial, ChatColor.AQUA + "Target: " + targetName);
        ItemStack storage = createItem(Material.CHEST, ChatColor.BLUE + "Open Minion Storage");

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

        var pdc = minionArmorStand.getPersistentDataContainer();
        String minionTypeStr = pdc.getOrDefault(plugin.minionTypeKey, PersistentDataType.STRING, MinionType.BLOCK_MINER.name());
        MinionType minionType = MinionType.valueOf(minionTypeStr);

        int idx = pdc.getOrDefault(plugin.indexKey, PersistentDataType.INTEGER, 0);

        if (idx == 4) { // center spot
            if (minionType == MinionType.FARMER) {
                String targetStr = pdc.getOrDefault(plugin.targetKey, PersistentDataType.STRING, Material.WHEAT.name());
                Material targetCrop = Material.valueOf(targetStr);
                Block underMinion = minionArmorStand.getLocation().getBlock().getRelative(0, -1, 0);

                if (targetCrop == Material.NETHER_WART) {
                    if (underMinion.getType() != Material.LAVA) {
                        underMinion.setType(Material.LAVA);
                    }
                } else {
                    if (underMinion.getType() != Material.WATER) {
                        underMinion.setType(Material.WATER);
                    }
                }
            }
            pdc.set(plugin.indexKey, PersistentDataType.INTEGER, (idx + 1) % 9);
            return;
        }

        if (minionType == MinionType.FARMER) {
            processFarmerCell(idx);
        } else { // BLOCK_MINER
            processBlockMinerCell(forceMine, idx);
        }

        pdc.set(plugin.indexKey, PersistentDataType.INTEGER, (idx + 1) % 9);
    }

    private void processBlockMinerCell(Boolean forceMine, int idx) {
        World world = minionArmorStand.getWorld();
        var persistentDataContainer = minionArmorStand.getPersistentDataContainer();
        String target = persistentDataContainer.getOrDefault(plugin.targetKey, PersistentDataType.STRING, Material.COBBLESTONE.name());
        Material mat = Material.valueOf(target);
        Location loc = minionArmorStand.getLocation();
        Block block = world.getBlockAt(loc.getBlockX() + (idx%3)-1,
            loc.getBlockY()-1, loc.getBlockZ() + (idx/3)-1);

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
    }

    private void processFarmerCell(int idx) {
        World world = minionArmorStand.getWorld();
        var pdc = minionArmorStand.getPersistentDataContainer();
        String targetStr = pdc.getOrDefault(plugin.targetKey, PersistentDataType.STRING, Material.WHEAT.name());
        Material targetCrop;
        try {
            targetCrop = Material.valueOf(targetStr);
        } catch (IllegalArgumentException e) {
            minionArmorStand.setCustomName(ChatColor.RED + "Invalid Target!");
            minionArmorStand.setCustomNameVisible(true);
            return;
        }

        if (targetCrop == Material.SUGAR_CANE) {
            if (idx == 0 || idx == 2 || idx == 6 || idx == 8) { // Skip corners
                return;
            }
        }

        Location loc = minionArmorStand.getLocation();
        Block soilBlock = world.getBlockAt(loc.getBlockX() + (idx % 3) - 1,
                loc.getBlockY() - 1,
                loc.getBlockZ() + (idx / 3) - 1);

        Block cropBlock = soilBlock.getRelative(0, 1, 0);

        // Harvest logic
        Material plantableCrop = getPlantableCrop(targetCrop);
        if (cropBlock.getType() == plantableCrop && cropBlock.getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() == ageable.getMaximumAge()) {
                harvestAndReplant(cropBlock, targetCrop);
            } else {
                minionArmorStand.setCustomName(ChatColor.YELLOW + "Waiting to grow");
            }
        } else if (cropBlock.getType() == Material.SUGAR_CANE && targetCrop == Material.SUGAR_CANE) {
            harvestSugarCane(cropBlock);
        }
        // Planting logic
        else if (cropBlock.getType() == Material.AIR) {
            // canPlant checks the soil, so we pass the cropBlock
            if (canPlant(cropBlock, targetCrop)) {
                tryPlanting(cropBlock, targetCrop);
            } else {
                minionArmorStand.setCustomName(ChatColor.RED + "Cannot plant here");
            }
        } else if (cropBlock.getType() != Material.AIR) {
            minionArmorStand.setCustomName(ChatColor.RED + "Occupied: " + cropBlock.getType());
        }
        minionArmorStand.setCustomNameVisible(true);
    }

    private void harvestAndReplant(Block block, Material targetCrop) {
        minionArmorStand.setCustomName(ChatColor.GREEN + "Harvesting");
        minionArmorStand.swingMainHand();

        World world = block.getWorld();
        Inventory storage = plugin.getMinionStorage(minionArmorStand.getUniqueId());
        Inventory chestInv = checkForChest(world, minionArmorStand.getLocation());
        Material seedType = getSeedMaterial(targetCrop);

        Collection<ItemStack> drops = new ArrayList<>(block.getDrops());
        block.setType(Material.AIR);

        // Replant logic - seeds are infinite
        Block soil = block.getRelative(0, -1, 0);
        if (soil.getType() == Material.FARMLAND || (getPlantableCrop(targetCrop) == Material.NETHER_WART && soil.getType() == Material.SOUL_SAND)) {
            block.setType(getPlantableCrop(targetCrop));
        }

        // Seed collection toggle logic
        boolean wantsSeeds = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.wantsSeedsKey, PersistentDataType.BYTE, (byte)1) == 1;
        if (!wantsSeeds) {
            if (targetCrop == Material.WHEAT || targetCrop == Material.BEETROOTS) {
                drops.removeIf(item -> item.getType() == seedType);
            }
        }

        // Add remaining drops to storage
        for (ItemStack drop : drops) {
            if (drop.getAmount() > 0) {
                addToStorage(drop, storage, chestInv, world, block.getLocation());
            }
        }
    }

    private void harvestSugarCane(Block baseBlock) {
        Block blockAbove = baseBlock.getRelative(0, 1, 0);
        if (blockAbove.getType() == Material.SUGAR_CANE) {
            minionArmorStand.setCustomName(ChatColor.GREEN + "Harvesting");
            minionArmorStand.swingMainHand();

            World world = baseBlock.getWorld();
            Inventory storage = plugin.getMinionStorage(minionArmorStand.getUniqueId());
            Inventory chestInv = checkForChest(world, minionArmorStand.getLocation());

            Block currentBlock = blockAbove;
            while (currentBlock.getType() == Material.SUGAR_CANE) {
                Collection<ItemStack> drops = currentBlock.getDrops();
                currentBlock.setType(Material.AIR);
                for (ItemStack drop : drops) {
                    addToStorage(drop, storage, chestInv, world, currentBlock.getLocation());
                }
                currentBlock = currentBlock.getRelative(0, 1, 0);
            }
        } else {
            minionArmorStand.setCustomName(ChatColor.YELLOW + "Waiting to grow");
        }
    }

    private void tryPlanting(Block block, Material targetCrop) {
        if (canPlant(block, targetCrop)) {
            Block soilBlock = block.getRelative(0, -1, 0);
            if (targetCrop == Material.NETHER_WART) {
                if (soilBlock.getType() != Material.SOUL_SAND) {
                    soilBlock.setType(Material.SOUL_SAND);
                }
            } else if (targetCrop != Material.SUGAR_CANE) {
                if (soilBlock.getType() == Material.DIRT || soilBlock.getType() == Material.GRASS_BLOCK) {
                    soilBlock.setType(Material.FARMLAND);
                }
            }

            minionArmorStand.setCustomName(ChatColor.GREEN + "Planting");
            block.setType(getPlantableCrop(targetCrop));
        } else {
            minionArmorStand.setCustomName(ChatColor.RED + "Cannot plant here");
        }
    }

    private boolean canPlant(Block block, Material crop) {
        Block soilBlock = block.getRelative(0, -1, 0);
        Material soil = soilBlock.getType();
        if (crop == Material.SUGAR_CANE) {
            if (soil != Material.GRASS_BLOCK && soil != Material.DIRT && soil != Material.SAND && soil != Material.RED_SAND) {
                return false;
            }
            // Check for water adjacent to the soil block
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && z == 0) continue;
                    if (soilBlock.getRelative(x, 0, z).getType() == Material.WATER) {
                        return true;
                    }
                }
            }
            return false;
        } else if (crop == Material.NETHER_WART) {
            return soil == Material.SOUL_SAND || soil == Material.DIRT || soil == Material.GRASS_BLOCK;
        } else { // Wheat, carrots, potatoes, beetroot
            return soil == Material.FARMLAND || soil == Material.DIRT || soil == Material.GRASS_BLOCK;
        }
    }

    private Material getPlantableCrop(Material targetCrop) {
        return switch (targetCrop) {
            case CARROT -> Material.CARROTS;
            case POTATO -> Material.POTATOES;
            default -> targetCrop;
        };
    }

    private Material getSeedMaterial(Material crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROTS, CARROT -> Material.CARROT;
            case POTATOES, POTATO -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case NETHER_WART -> Material.NETHER_WART;
            default -> crop;
        };
    }

    private void addToStorage(ItemStack item, Inventory storage, Inventory chestInv, World world, Location dropLocation) {
        Map<Integer, ItemStack> left;
        if (chestInv != null) {
            left = chestInv.addItem(item);
            if (!left.isEmpty()) {
                left = storage.addItem(left.values().toArray(new ItemStack[0]));
            }
        } else {
            left = storage.addItem(item);
        }

        if (left != null && !left.isEmpty()) {
            left.values().forEach(o -> world.dropItemNaturally(dropLocation, o));
        }
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
        block.getDrops().forEach(d -> addToStorage(d, storage, chestInv, world, block.getLocation()));
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
