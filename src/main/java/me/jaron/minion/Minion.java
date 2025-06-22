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
import me.jaron.minion.FarmerState;


import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Minion {

    private final MinionPlugin plugin;
    private final ArmorStand minionArmorStand;
    private BukkitTask miningTask;

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
        int tier = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.tierKey, PersistentDataType.INTEGER, 1);

        ItemStack typeItem;
        if (minionType == MinionType.BLOCK_MINER) {
            typeItem = createItem(Material.DIAMOND_PICKAXE, ChatColor.GOLD + "Block Miner " + ChatColor.WHITE + "[Tier " + tier + "]");
        } else {
            typeItem = createItem(Material.DIAMOND_HOE, ChatColor.GOLD + "Farmer " + ChatColor.WHITE + "[Tier " + tier + "]");
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
            // Convert seed materials to their crop display versions
            if (targetMaterial == Material.BEETROOT_SEEDS) {
                targetMaterial = Material.BEETROOT;
            } else if (targetMaterial == Material.WHEAT_SEEDS) {
                targetMaterial = Material.WHEAT;
            }
        } catch (IllegalArgumentException e) {
            targetMaterial = Material.COBBLESTONE;
        }

        ItemStack selector = createItem(targetMaterial, ChatColor.AQUA + "Target: " + targetName);
        ItemStack storage = createItem(Material.CHEST, ChatColor.BLUE + "Open Minion Storage");
        ItemStack stats = createItem(Material.BOOK, ChatColor.GREEN + "View Minion Statistics");

        // Use the dynamic max tier from the upgrade manager instead of hardcoded value
        int maxTier = plugin.getUpgradeManager().getMaxTier();
        ItemStack upgrade = createItem(Material.EXPERIENCE_BOTTLE, ChatColor.GREEN + "Upgrade Minion" +
                                     (tier < maxTier ? "" : ChatColor.RED + " (Max Tier)"));

        inv.setItem(2, stats);
        inv.setItem(3, storage);
        inv.setItem(4, selector);
        inv.setItem(6, upgrade);
        inv.setItem(8, createBackButton());
        return inv;
    }

    public Inventory getMinionStorage() {
        Inventory storage = plugin.getMinionStorage(minionArmorStand.getUniqueId());

        // Calculate positions for control buttons - always place in bottom row
        int size = storage.getSize();
        int bottomRowStart = size - (size % 9);
        if (bottomRowStart == size) bottomRowStart = size - 9; // Ensure we're using the bottom row

        // Place the control buttons in the bottom row
        storage.setItem(bottomRowStart + 8, createBackButton()); // Last slot in bottom row
        storage.setItem(bottomRowStart + 7, createCollectButton("Collect All")); // Second last slot
        storage.setItem(bottomRowStart + 6, createCollectButton("Collect from Chest")); // Third last slot

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
        if (idx == 8 && minionType == MinionType.FARMER) {
            checkFarmerStateTransition();
        }
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
                setMinionCustomName(ChatColor.RED + "Storage Full!");
            } else if (block.getType() != mat && block.getType() != Material.AIR) {
                setMinionCustomName(ChatColor.RED + "Occupied: " + block.getType());
            } else {
                setMinionCustomName(ChatColor.RED + "Need " + mat.name());
            }
        }

        minionArmorStand.setCustomNameVisible(true);
    }

    // Helper method to set minion's custom name with tier-based custom names from config
    private void setMinionCustomName(String defaultName) {
        int tier = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.tierKey, PersistentDataType.INTEGER, 1);
        String minionTypeStr = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.minionTypeKey, PersistentDataType.STRING, MinionType.BLOCK_MINER.name());
        String minionTypeLower = minionTypeStr.toLowerCase();

        // Extract the action type from the default name to determine what the minion is doing
        String actionType = "idle"; // Default action

        if (defaultName.contains("Mining")) {
            actionType = "mining";
        } else if (defaultName.contains("Planting")) {
            actionType = "planting";
        } else if (defaultName.contains("Storage Full")) {
            actionType = "storage_full";
        } else if (defaultName.contains("Need")) {
            actionType = "need_material";
        } else if (defaultName.contains("Occupied")) {
            actionType = "occupied";
        } else if (defaultName.contains("Harvesting")) {
            actionType = "harvesting";
        } else if (defaultName.contains("Hoeing")) {
            actionType = "hoeing";
        } else if (defaultName.contains("Waiting to grow")) {
            actionType = "waiting";
        } else if (defaultName.contains("Applied Bonemeal")) {
            actionType = "bonemeal";
        } else if (defaultName.contains("Double Harvest")) {
            actionType = "double_harvest";
        } else if (defaultName.contains("Fortune")) {
            actionType = "fortune";
        } else if (defaultName.contains("Cannot plant")) {
            actionType = "cannot_plant";
        }

        // Try to get action-specific message from config
        String actionMessage = (String) plugin.getUpgradeManager().getCustomSetting(
            minionTypeLower, tier, "messages." + actionType, null);

        if (actionMessage != null && !actionMessage.isEmpty()) {
            minionArmorStand.setCustomName(ChatColor.translateAlternateColorCodes('&', actionMessage));
            return;
        }

        // Fall back to default action message from default section
        actionMessage = (String) plugin.getUpgradeManager().getCustomSetting(
            "default", tier, "messages." + actionType, null);

        if (actionMessage != null && !actionMessage.isEmpty()) {
            minionArmorStand.setCustomName(ChatColor.translateAlternateColorCodes('&', actionMessage));
            return;
        }

        // When idle, alternate between display_name (more frequent) and hologram_message (less frequent)
        if (actionType.equals("idle")) {
            // Get display_name from config
            String displayName = plugin.getUpgradeManager().getDisplayName(minionTypeLower, tier);

            // Get hologram_message from config
            String hologramMessage = plugin.getUpgradeManager().getHologramMessage(minionTypeLower, tier);

            // Random chance to show hologram_message (20% chance) or display_name (80% chance)
            if (displayName != null && !displayName.isEmpty()) {
                if (hologramMessage != null && !hologramMessage.isEmpty() && Math.random() < 0.2) {
                    // 20% chance to show hologram_message
                    minionArmorStand.setCustomName(ChatColor.translateAlternateColorCodes('&', hologramMessage));
                } else {
                    // 80% chance to show display_name
                    minionArmorStand.setCustomName(ChatColor.translateAlternateColorCodes('&', displayName));
                }
                return;
            }

            // Fall back to hologram_message if display_name wasn't available
            if (hologramMessage != null && !hologramMessage.isEmpty()) {
                minionArmorStand.setCustomName(ChatColor.translateAlternateColorCodes('&', hologramMessage));
                return;
            }
        } else {
            // For non-idle states, use the existing logic for hologram_message
            String customHologram = plugin.getUpgradeManager().getHologramMessage(minionTypeLower, tier);
            if (customHologram != null && !customHologram.isEmpty()) {
                minionArmorStand.setCustomName(ChatColor.translateAlternateColorCodes('&', customHologram));
                return;
            }
        }

        // Fall back to default message if no custom messages found
        minionArmorStand.setCustomName(defaultName);
    }

    private void processFarmerCell(int idx) {
        World world = minionArmorStand.getWorld();
        var pdc = minionArmorStand.getPersistentDataContainer();
        String stateStr = pdc.getOrDefault(plugin.farmerStateKey, PersistentDataType.STRING, FarmerState.HARVESTING.name());
        FarmerState currentState;
        try {
            currentState = FarmerState.valueOf(stateStr);
        } catch (IllegalArgumentException e) {
            currentState = FarmerState.HARVESTING; // Default state
        }

        String targetStr = pdc.getOrDefault(plugin.targetKey, PersistentDataType.STRING, Material.WHEAT.name());
        Material targetCrop;
        try {
            targetCrop = Material.valueOf(targetStr);
        } catch (IllegalArgumentException e) {
            setMinionCustomName(ChatColor.RED + "Invalid Target!");
            minionArmorStand.setCustomNameVisible(true);
            return;
        }

        Location loc = minionArmorStand.getLocation();
        Block soilBlock = world.getBlockAt(loc.getBlockX() + (idx % 3) - 1,
                loc.getBlockY() - 1,
                loc.getBlockZ() + (idx / 3) - 1);

        Block cropBlock = soilBlock.getRelative(0, 1, 0);

        // Get tier and bonemeal chance from config
        int tier = pdc.getOrDefault(plugin.tierKey, PersistentDataType.INTEGER, 1);
        double boneMealChance = plugin.getUpgradeManager().getBoneMealChance(tier);

        switch (currentState) {
            case HOEING:
                if (targetCrop != Material.NETHER_WART) {
                    if (soilBlock.getType() == Material.DIRT || soilBlock.getType() == Material.GRASS_BLOCK) {
                        soilBlock.setType(Material.FARMLAND);
                        setMinionCustomName(ChatColor.GREEN + "Hoeing...");
                        minionArmorStand.swingMainHand();
                    }
                }
                break;
            case PLANTING:
                if (cropBlock.getType() == Material.AIR && soilBlock.getType() == Material.FARMLAND) {
                    if (canPlant(cropBlock, targetCrop)) {
                        tryPlanting(cropBlock, targetCrop);
                        setMinionCustomName(ChatColor.GREEN + "Planting...");
                    }
                }
                break;
            case HARVESTING:
                // Harvest logic
                Material plantableCrop = getPlantableCrop(targetCrop);
                if (cropBlock.getType() == plantableCrop && cropBlock.getBlockData() instanceof Ageable ageable) {
                    if (ageable.getAge() == ageable.getMaximumAge()) {
                        harvestAndReplant(cropBlock, targetCrop);
                    } else {
                        setMinionCustomName(ChatColor.YELLOW + "Waiting to grow");

                        // Apply bonemeal chance based on tier
                        if (boneMealChance > 0 && Math.random() < boneMealChance) {
                            // Apply bonemeal effect (increase age)
                            int currentAge = ageable.getAge();
                            int maxAge = ageable.getMaximumAge();

                            // Only apply if not max age
                            if (currentAge < maxAge) {
                                Ageable newAgeable = (Ageable) ageable.clone();
                                newAgeable.setAge(Math.min(currentAge + 1, maxAge));
                                cropBlock.setBlockData(newAgeable);
                                setMinionCustomName(ChatColor.GREEN + "Applied Bonemeal Effect");
                            }
                        }
                    }
                }
                // Planting logic
                else if (cropBlock.getType() == Material.AIR) {
                    // canPlant checks the soil, so we pass the cropBlock
                    if (canPlant(cropBlock, targetCrop)) {
                        tryPlanting(cropBlock, targetCrop);
                    } else {
                        setMinionCustomName(ChatColor.RED + "Cannot plant here");
                    }
                } else if (cropBlock.getType() != Material.AIR) {
                    setMinionCustomName(ChatColor.RED + "Occupied: " + cropBlock.getType());
                }
                break;
        }
        minionArmorStand.setCustomNameVisible(true);
    }

    private void checkFarmerStateTransition() {
        var pdc = minionArmorStand.getPersistentDataContainer();
        String stateStr = pdc.getOrDefault(plugin.farmerStateKey, PersistentDataType.STRING, FarmerState.HARVESTING.name());
        FarmerState currentState = FarmerState.valueOf(stateStr);
        World world = minionArmorStand.getWorld();
        Location loc = minionArmorStand.getLocation();

        if (currentState == FarmerState.HOEING) {
            String targetCropStr = pdc.get(plugin.targetKey, PersistentDataType.STRING);
            if (targetCropStr != null) {
                Material targetCrop = Material.valueOf(targetCropStr);
                if (targetCrop == Material.NETHER_WART) {
                    pdc.set(plugin.farmerStateKey, PersistentDataType.STRING, FarmerState.PLANTING.name());
                    return;
                }
            }
            boolean allHoed = true;
            for (int i = 0; i < 9; i++) {
                if (i == 4) continue;
                Block currentSoil = world.getBlockAt(loc.getBlockX() + (i % 3) - 1, loc.getBlockY() - 1, loc.getBlockZ() + (i / 3) - 1);
                if (currentSoil.getType() != Material.FARMLAND) {
                    allHoed = false;
                    break;
                }
            }
            if (allHoed) {
                pdc.set(plugin.farmerStateKey, PersistentDataType.STRING, FarmerState.PLANTING.name());
            }
        } else if (currentState == FarmerState.PLANTING) {
            boolean allPlanted = true;
            for (int i = 0; i < 9; i++) {
                if (i == 4) continue;
                Block currentCropBlock = world.getBlockAt(loc.getBlockX() + (i % 3) - 1, loc.getBlockY(), loc.getBlockZ() + (i / 3) - 1);
                Material targetCrop = Material.valueOf(pdc.get(plugin.targetKey, PersistentDataType.STRING));
                Material plantableCrop = getPlantableCrop(targetCrop);
                if (currentCropBlock.getType() != plantableCrop) {
                    allPlanted = false;
                    break;
                }
            }
            if (allPlanted) {
                pdc.set(plugin.farmerStateKey, PersistentDataType.STRING, FarmerState.HARVESTING.name());
            }
        }
    }

    private void harvestAndReplant(Block block, Material targetCrop) {
        setMinionCustomName(ChatColor.GREEN + "Harvesting");
        minionArmorStand.swingMainHand();

        World world = block.getWorld();
        Inventory storage = plugin.getMinionStorage(minionArmorStand.getUniqueId());
        Inventory chestInv = checkForChest(world, minionArmorStand.getLocation());
        Material seedType = getSeedMaterial(targetCrop);

        // Get tier for configuration-based features
        int tier = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.tierKey, PersistentDataType.INTEGER, 1);
        double doubleCropsChance = plugin.getUpgradeManager().getDoubleCropsChance(tier);

        Collection<ItemStack> drops = new ArrayList<>(block.getDrops());

        // Get minion stats
        MinionStats stats = plugin.getMinionStats(minionArmorStand.getUniqueId());
        stats.incrementTimesHarvested();

        // Apply double crops chance based on tier
        if (doubleCropsChance > 0 && Math.random() < doubleCropsChance) {
            // Clone the drops to simulate getting double
            Collection<ItemStack> extraDrops = new ArrayList<>();
            for (ItemStack drop : drops) {
                // Only duplicate non-seed items
                if (drop.getType() != seedType) {
                    ItemStack clone = drop.clone();
                    extraDrops.add(clone);
                    setMinionCustomName(ChatColor.GREEN + "Double Harvest!");
                    stats.incrementDoubleCropsProcs();
                }
            }
            drops.addAll(extraDrops);
        }

        block.setType(Material.AIR);

        // Replant logic - seeds are infinite
        Block soil = block.getRelative(0, -1, 0);
        if (soil.getType() == Material.FARMLAND || (getPlantableCrop(targetCrop) == Material.NETHER_WART && soil.getType() == Material.SOUL_SAND)) {
            block.setType(getPlantableCrop(targetCrop));
        }

        // Seed collection toggle logic
        boolean wantsSeeds = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.wantsSeedsKey, PersistentDataType.BYTE, (byte)1) == 1;
        if (!wantsSeeds) {
            // Filter out any seed items when seeds are not wanted
            drops.removeIf(item -> item.getType() == seedType ||
                           item.getType() == Material.WHEAT_SEEDS ||
                           item.getType() == Material.BEETROOT_SEEDS);
        }

        // Add remaining drops to storage
        for (ItemStack drop : drops) {
            if (drop.getAmount() > 0) {
                addToStorage(drop, storage, chestInv, world, block.getLocation());
            }
        }
    }

    private void tryPlanting(Block block, Material targetCrop) {
        if (canPlant(block, targetCrop)) {
            Block soilBlock = block.getRelative(0, -1, 0);
            if (targetCrop == Material.NETHER_WART) {
                if (soilBlock.getType() != Material.SOUL_SAND) {
                    soilBlock.setType(Material.SOUL_SAND);
                }
            } else {
                if (soilBlock.getType() == Material.DIRT || soilBlock.getType() == Material.GRASS_BLOCK) {
                    soilBlock.setType(Material.FARMLAND);
                }
            }

            setMinionCustomName(ChatColor.GREEN + "Planting");
            block.setType(getPlantableCrop(targetCrop));

            // Track planting statistics
            MinionStats stats = plugin.getMinionStats(minionArmorStand.getUniqueId());
            stats.incrementItemsPlaced();
        } else {
            setMinionCustomName(ChatColor.RED + "Cannot plant here");
        }
    }

    private boolean canPlant(Block block, Material crop) {
        Block soilBlock = block.getRelative(0, -1, 0);
        Material soil = soilBlock.getType();
        if (crop == Material.NETHER_WART) {
            return soil == Material.SOUL_SAND || soil == Material.DIRT || soil == Material.GRASS_BLOCK;
        } else { // Wheat, carrots, potatoes, beetroot
            return soil == Material.FARMLAND || soil == Material.DIRT || soil == Material.GRASS_BLOCK;
        }
    }

    private Material getPlantableCrop(Material targetCrop) {
        return switch (targetCrop) {
            case CARROT -> Material.CARROTS;
            case POTATO -> Material.POTATOES;
            case WHEAT_SEEDS -> Material.WHEAT;
            case BEETROOT, BEETROOT_SEEDS -> Material.BEETROOTS;
            default -> targetCrop;
        };
    }

    private Material getSeedMaterial(Material crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROTS, CARROT -> Material.CARROT;
            case POTATOES, POTATO -> Material.POTATO;
            case BEETROOTS, BEETROOT, BEETROOT_SEEDS -> Material.BEETROOT_SEEDS;
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
        setMinionCustomName(ChatColor.GREEN + "Planting");
        block.setType(mat);

        // Track planting statistics
        MinionStats stats = plugin.getMinionStats(minionArmorStand.getUniqueId());
        stats.incrementItemsPlaced();

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

        setMinionCustomName(ChatColor.GOLD + "Mining");
        minionArmorStand.swingMainHand();

        // Get tier for fortune ability
        int tier = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.tierKey, PersistentDataType.INTEGER, 1);
        double fortuneChance = plugin.getUpgradeManager().getFortuneChance(tier);

        // Get initial drops
        Collection<ItemStack> drops = new ArrayList<>(block.getDrops());

        // Get minion stats
        MinionStats stats = plugin.getMinionStats(minionArmorStand.getUniqueId());
        stats.incrementItemsMined();

        // Apply fortune chance based on tier
        if (fortuneChance > 0 && Math.random() < fortuneChance) {
            // Add extra items (similar to Fortune enchantment)
            Collection<ItemStack> extraDrops = new ArrayList<>();
            for (ItemStack drop : drops) {
                ItemStack extraDrop = drop.clone();
                // Don't duplicate special rare items or blocks that should remain singular
                if (!isSpecialRareDrop(extraDrop.getType())) {
                    extraDrops.add(extraDrop);
                    setMinionCustomName(ChatColor.GREEN + "Fortune Effect!");
                    stats.incrementFortuneProcs();
                }
            }
            drops.addAll(extraDrops);
        }

        // Add all drops to storage
        for (ItemStack drop : drops) {
            addToStorage(drop, storage, chestInv, world, block.getLocation());
        }

        block.setType(Material.AIR);
        return true;

    }

    private boolean isSpecialRareDrop(Material material) {
        // Materials that shouldn't be duplicated by fortune
        return material == Material.DIAMOND_BLOCK ||
               material == Material.EMERALD_BLOCK ||
               material == Material.NETHERITE_BLOCK ||
               material == Material.BEACON ||
               material == Material.DRAGON_EGG ||
               material == Material.NETHER_STAR ||
               material == Material.COMMAND_BLOCK ||
               material == Material.SPAWNER;
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

        int tier = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.tierKey, PersistentDataType.INTEGER, 1);
        String minionTypeStr = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.minionTypeKey, PersistentDataType.STRING, MinionType.BLOCK_MINER.name());
        MinionType minionType = MinionType.valueOf(minionTypeStr);

        // Get delay based on minion type and tier from config
        int delay = plugin.getUpgradeManager().getDelay(minionType, tier);
        int delayTicks = delay / 50; // Convert milliseconds to ticks (1 tick = ~50ms)

        miningTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            processCell(null);
         }, 0L, Math.max(delayTicks, 10L)); // Minimum 10 ticks (0.5 seconds)
    }
    // stop automation
    public void stopAutomation() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }

    public MinionType getMinionType() {
        String minionTypeStr = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.minionTypeKey, PersistentDataType.STRING, MinionType.BLOCK_MINER.name());
        return MinionType.valueOf(minionTypeStr);
    }

    public Location getLocation() {
        return minionArmorStand.getLocation();
    }

    public Material getTargetMaterial() {
        String targetName = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.targetKey, PersistentDataType.STRING, Material.COBBLESTONE.name());
        try {
            return Material.valueOf(targetName);
        } catch (IllegalArgumentException e) {
            return Material.COBBLESTONE;
        }
    }

    public UUID getUUID() {
        return minionArmorStand.getUniqueId();
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

    public Inventory getStatsInventory() {
        Inventory inv = Bukkit.createInventory(new MinionInventoryHolder(minionArmorStand.getUniqueId()), 27, "Minion Statistics");

        // Get minion stats
        MinionStats stats = plugin.getMinionStats(minionArmorStand.getUniqueId());

        // Get minion info
        String minionTypeStr = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.minionTypeKey, PersistentDataType.STRING, MinionType.BLOCK_MINER.name());
        MinionType minionType = MinionType.valueOf(minionTypeStr);
        int tier = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.tierKey, PersistentDataType.INTEGER, 1);

        // Create title item
        ItemStack titleItem;
        if (minionType == MinionType.BLOCK_MINER) {
            titleItem = createItem(Material.DIAMOND_PICKAXE, ChatColor.GOLD + "Block Miner Statistics " + ChatColor.WHITE + "[Tier " + tier + "]");
        } else {
            titleItem = createItem(Material.DIAMOND_HOE, ChatColor.GOLD + "Farmer Statistics " + ChatColor.WHITE + "[Tier " + tier + "]");
        }

        // Add lore with basic information
        ItemMeta meta = titleItem.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Target: " + ChatColor.YELLOW + getTargetMaterial().name());
            meta.setLore(lore);
            titleItem.setItemMeta(meta);
        }
        inv.setItem(4, titleItem);

        // Basic stats items
        inv.setItem(10, createStatItem(Material.IRON_PICKAXE, "Items Mined", stats.getItemsMined()));
        inv.setItem(11, createStatItem(Material.IRON_SHOVEL, "Items Placed", stats.getItemsPlaced()));

        // Display time saved based on tier delay
        int delay = plugin.getUpgradeManager().getDelay(minionType, tier);
        String timeSaved = stats.getFormattedTimeSaved(delay);
        inv.setItem(12, createTextItem(Material.CLOCK, "Time Saved",
                ChatColor.WHITE + timeSaved,
                ChatColor.GRAY + "Based on tier delay: " + ChatColor.YELLOW + delay + "ms"));

        // Display uptime
        inv.setItem(13, createTextItem(Material.SUNFLOWER, "Uptime",
                ChatColor.WHITE + stats.getFormattedUptime()));

        // Specialized stats based on minion type
        if (minionType == MinionType.BLOCK_MINER) {
            // Fortune stats for miners
            double fortunePercent = stats.getFortunePercentage();
            inv.setItem(14, createTextItem(Material.DIAMOND, "Fortune Effect",
                    ChatColor.WHITE + " " + stats.getFortuneProcs() + " (" + String.format("%.2f", fortunePercent) + "%)",
                    ChatColor.GRAY + "Extra items from fortune"));
        } else {
            // Farming-specific stats
            inv.setItem(14, createStatItem(Material.WHEAT, "Times Harvested", stats.getTimesHarvested()));

            double doubleCropsPercent = stats.getDoubleCropsPercentage();
            inv.setItem(15, createTextItem(Material.HAY_BLOCK, "Double Harvests",
                    ChatColor.WHITE + " " + stats.getDoubleCropsProcs() + " (" + String.format("%.2f", doubleCropsPercent) + "%)",
                    ChatColor.GRAY + "Extra crops from double harvest"));
        }

        // Add back button at the bottom
        inv.setItem(26, createBackButton());

        return inv;
    }

    private ItemStack createStatItem(Material material, String name, long value) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + name);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.WHITE + "" + value);  // Using empty string to force string concatenation
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createTextItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + name);
            if (loreLines.length > 0) {
                List<String> lore = new ArrayList<>();
                for (String line : loreLines) {
                    lore.add(line);
                }
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
