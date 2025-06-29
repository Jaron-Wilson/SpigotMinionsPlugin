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
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.Locale;


import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Minion {

    private final MinionPlugin plugin;
    private final ArmorStand minionArmorStand;
    private BukkitTask miningTask;

    private boolean notifiedStorageFull = false;
    private long lastNotificationTime = 0;
    private static final long NOTIFICATION_COOLDOWN = 5 * 60 * 1000; // 5 minutes in milliseconds
    private String string;


    public Minion(MinionPlugin plugin, ArmorStand minionArmorStand) {
        this.plugin = plugin;
        this.minionArmorStand = minionArmorStand;
    }

    public static ItemStack createBackButton() {
        ItemStack barrier = new ItemStack(Material.RED_STAINED_GLASS_PANE);
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

    /**
     * Creates and returns the minion action inventory with improved layout
     */
    public Inventory getActionInventory() {
        Inventory inv = Bukkit.createInventory(new MinionInventoryHolder(minionArmorStand.getUniqueId()), 36, "Minion Control Panel");

        // create border with black stained-glass panes
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        if (borderMeta != null) {
            borderMeta.setDisplayName(" ");
            border.setItemMeta(borderMeta);
        }

        // Add border around the edges
        for (int i : new int[]{0,1,2,3,4,5,6,7,8,9,17,18,26,27,28,29,30,32,33,34,35}) {
            inv.setItem(i, border);
        }

        // Get minion information
        String minionTypeStr = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.minionTypeKey, PersistentDataType.STRING, MinionType.BLOCK_MINER.name());
        MinionType minionType = MinionType.valueOf(minionTypeStr);
        int tier = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.tierKey, PersistentDataType.INTEGER, 1);

        // Minion type toggle button (slot 10)
        ItemStack typeToggle = new ItemStack(minionType == MinionType.BLOCK_MINER ? Material.DIAMOND_PICKAXE : Material.DIAMOND_HOE);
        ItemMeta typeMeta = typeToggle.getItemMeta();
        if (typeMeta != null) {
            typeMeta.setDisplayName(ChatColor.BLUE + "Toggle Type: " + ChatColor.YELLOW + minionType.name());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Current: " + ChatColor.GOLD + minionType.name());
            lore.add(ChatColor.GRAY + "Click to toggle to " +
                    (minionType == MinionType.BLOCK_MINER ? ChatColor.GREEN + "FARMER" : ChatColor.GREEN + "BLOCK_MINER"));
            typeMeta.setLore(lore);
            typeToggle.setItemMeta(typeMeta);
        }
        inv.setItem(10, typeToggle);

        // Minion inventory button (slot 13)
        ItemStack storage = new ItemStack(Material.CHEST);
        ItemMeta storageMeta = storage.getItemMeta();
        if (storageMeta != null) {
            storageMeta.setDisplayName(ChatColor.GOLD + "Open Minion Storage");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Click to view collected items");
            storageMeta.setLore(lore);
            storage.setItemMeta(storageMeta);
        }
        inv.setItem(13, storage);

        // Target selection button (slot 16)
        String targetStr = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.targetKey, PersistentDataType.STRING, "");
        Material targetMaterial = targetStr.isEmpty() ? null : Material.getMaterial(targetStr);

        ItemStack targetItem;
        if (targetMaterial != null) {
            targetItem = new ItemStack(targetMaterial);
            ItemMeta targetMeta = targetItem.getItemMeta();
            if (targetMeta != null) {
                targetMeta.setDisplayName(ChatColor.AQUA + "Target: " + ChatColor.YELLOW + targetMaterial.name());
                targetMeta.setLore(List.of(ChatColor.GRAY + "Click to change target"));
                targetItem.setItemMeta(targetMeta);
            }
        } else {
            targetItem = new ItemStack(Material.DIAMOND_PICKAXE);
            ItemMeta targetMeta = targetItem.getItemMeta();
            if (targetMeta != null) {
                targetMeta.setDisplayName(ChatColor.YELLOW + "Set Target");
                targetMeta.setLore(List.of(ChatColor.GRAY + "Click to set target block"));
                targetItem.setItemMeta(targetMeta);
            }
        }
        inv.setItem(16, targetItem);

        // Stats button (slot 19)
        ItemStack stats = new ItemStack(Material.PAPER);
        ItemMeta statsMeta = stats.getItemMeta();
        if (statsMeta != null) {
            statsMeta.setDisplayName(ChatColor.GREEN + "View Stats");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Click to view minion statistics");
            statsMeta.setLore(lore);
            stats.setItemMeta(statsMeta);
        }
        inv.setItem(19, stats);

        // Upgrade button (slot 25)
        ItemStack upgrade = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta upgradeMeta = upgrade.getItemMeta();
        if (upgradeMeta != null) {
            upgradeMeta.setDisplayName(ChatColor.GREEN + "Upgrade Minion");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Current Tier: " + ChatColor.GOLD + tier);
            int maxTier = plugin.getUpgradeManager().getMaxTier();
            if (tier < maxTier) {
                lore.add(ChatColor.YELLOW + "Click to upgrade to Tier " + (tier + 1));
            } else {
                lore.add(ChatColor.RED + "Maximum tier reached");
            }
            upgradeMeta.setLore(lore);
            upgrade.setItemMeta(upgradeMeta);
        }
        inv.setItem(25, upgrade);

        // Back button (slot 31)
        ItemStack back = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(ChatColor.RED + "Close");
            backMeta.setLore(List.of(ChatColor.GRAY + "Click to close this menu"));
            back.setItemMeta(backMeta);
        }
        inv.setItem(31, back);

        // Additional button: Remove minion (slot 22)
        ItemStack remove = new ItemStack(Material.BARRIER);
        ItemMeta removeMeta = remove.getItemMeta();
        if (removeMeta != null) {
            removeMeta.setDisplayName(ChatColor.RED + "Remove Minion");
            removeMeta.setLore(List.of(ChatColor.GRAY + "Click to remove this minion"));
            remove.setItemMeta(removeMeta);
        }
        inv.setItem(22, remove);

        if (bundleAlert) {
            ItemStack alertItem = new ItemStack(Material.RED_BANNER);
            ItemMeta alertMeta = alertItem.getItemMeta();
            if (alertMeta != null) {
                alertMeta.setDisplayName(ChatColor.RED + "⚠ Bundle Alert ⚠");
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.YELLOW + "A minion in this bundle");
                lore.add(ChatColor.YELLOW + "has full storage!");
                alertMeta.setLore(lore);
                alertItem.setItemMeta(alertMeta);
            }
            inv.setItem(20, alertItem);  // Place in a visible slot
        }

        if (minionType == MinionType.FARMER) {
            byte wantsSeeds = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.wantsSeedsKey, PersistentDataType.BYTE, (byte)1);
            ItemStack seedsToggle = new ItemStack(Material.WHEAT_SEEDS);
            ItemMeta seedsMeta = seedsToggle.getItemMeta();
            if (seedsMeta != null) {
                seedsMeta.setDisplayName(ChatColor.GOLD + "Toggle Seed Replanting: " +
                        (wantsSeeds == 1 ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Current: " + (wantsSeeds == 1 ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
                lore.add(ChatColor.GRAY + "Click to toggle");
                seedsMeta.setLore(lore);
                seedsToggle.setItemMeta(seedsMeta);
            }
            inv.setItem(11, seedsToggle);
        }

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


    private void sendStorageFullNotification(Player owner) {
        long currentTime = System.currentTimeMillis();

        if (!notifiedStorageFull || (currentTime - lastNotificationTime) > NOTIFICATION_COOLDOWN) {
            notifiedStorageFull = true;
            lastNotificationTime = currentTime;

            if (owner != null && owner.isOnline()) {
                owner.sendTitle(
                        ChatColor.RED + "Storage Full!",
                        ChatColor.YELLOW + "Your " + getMinionTypeName() + " minion needs attention",
                        10, 70, 20
                );

                Location loc = minionArmorStand.getLocation();
                String locationInfo = String.format("(x:%d, y:%d, z:%d)",
                        loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

                owner.sendMessage(ChatColor.RED + "[Minion Alert] " +
                        ChatColor.YELLOW + "Your " + getMinionTypeName() +
                        " minion's storage is full! " + ChatColor.GRAY + locationInfo);

                owner.playSound(owner.getLocation(),
                        org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
            }

            notifyMinionBundle(owner);
        }
    }
    private void resetStorageFullNotification() {
        notifiedStorageFull = false;
    }
    private String getMinionTypeName() {
        String minionTypeStr = minionArmorStand.getPersistentDataContainer()
                .getOrDefault(plugin.minionTypeKey, PersistentDataType.STRING, MinionType.BLOCK_MINER.name());


        return Arrays.stream(minionTypeStr.replace("_", " ").toLowerCase().split(" "))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .reduce((a, b) -> a + " " + b).orElse(minionTypeStr);
    }
    private void notifyMinionBundle(Player owner) {
        String bundleId = minionArmorStand.getPersistentDataContainer()
                .getOrDefault(plugin.minionBundleKey, PersistentDataType.STRING, "");

        if (bundleId.isEmpty()) return;

        for (Minion otherMinion : plugin.getActiveMinions()) {
            if (otherMinion.getUUID().equals(this.getUUID())) continue;

            String otherBundleId = otherMinion.getBundleId();
            if (bundleId.equals(otherBundleId)) {
                otherMinion.setBundleAlert(true);

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getOpenInventory().getTopInventory().getHolder() instanceof MinionInventoryHolder) {
                        MinionInventoryHolder holder = (MinionInventoryHolder)
                                player.getOpenInventory().getTopInventory().getHolder();

                        if (holder.getMinionUUID().equals(otherMinion.getUUID())) {
                            player.openInventory(otherMinion.getActionInventory());
                            break;
                        }
                    }
                }
            }
        }
    }
    public String getBundleId() {
        return minionArmorStand.getPersistentDataContainer()
                .getOrDefault(plugin.minionBundleKey, PersistentDataType.STRING, "");
    }
    private boolean bundleAlert = false;

    public void setBundleAlert(boolean alert) {
        this.bundleAlert = alert;
    }




    public void mineBlock() {
        processCell(true);
    }
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

        lookAtBlock(block);

        Inventory storage = plugin.getMinionStorage(minionArmorStand.getUniqueId());
        if (storage == null) return;

        Inventory chestInv = checkForChest(world,loc);

        boolean didSomething = false;

        if (forceMine != null) {
            if (forceMine) {
                if (block.getType() == mat) {
                    didSomething = minionMineEvent(block, chestInv, storage, world);
                }
            } else {
                if (block.getType() == Material.AIR) {
                    didSomething = plantBlock(block, mat);
                }
            }
        } else {
            boolean storageSystemFull = isStorageFull(mat, storage, chestInv);
            if (storageSystemFull) {
                if (block.getType() == Material.AIR) {
                    didSomething = plantBlock(block, mat);
                }
            } else {
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

                UUID ownerUUID = minionArmorStand.getPersistentDataContainer()
                        .getOrDefault(plugin.ownerKey, PersistentDataType.STRING, "")
                        .isEmpty() ? null : UUID.fromString(minionArmorStand.getPersistentDataContainer()
                        .get(plugin.ownerKey, PersistentDataType.STRING));

                if (ownerUUID != null) {
                    Player owner = Bukkit.getPlayer(ownerUUID);
                    sendStorageFullNotification(owner);
                }

            } else {
                resetStorageFullNotification();
                if (block.getType() != mat && block.getType() != Material.AIR) {
                    setMinionCustomName(ChatColor.RED + "Occupied: " + block.getType());
                } else {
                    setMinionCustomName(ChatColor.RED + "Need " + mat.name());
                }
            }
        }
        else {
            resetStorageFullNotification();
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::resetLook, 20L);

        minionArmorStand.setCustomNameVisible(true);
    }

    private void setMinionCustomName(String defaultName) {
        int tier = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.tierKey, PersistentDataType.INTEGER, 1);
        String minionTypeStr = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.minionTypeKey, PersistentDataType.STRING, MinionType.BLOCK_MINER.name());
        String minionTypeLower = minionTypeStr.toLowerCase();

        String actionType = "idle";

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

        String actionMessage = (String) plugin.getUpgradeManager().getCustomSetting(
            minionTypeLower, tier, "messages." + actionType, null);

        if (actionMessage != null && !actionMessage.isEmpty()) {
            minionArmorStand.setCustomName(ChatColor.translateAlternateColorCodes('&', actionMessage));
            return;
        }

        actionMessage = (String) plugin.getUpgradeManager().getCustomSetting(
            "default", tier, "messages." + actionType, null);

        if (actionMessage != null && !actionMessage.isEmpty()) {
            minionArmorStand.setCustomName(ChatColor.translateAlternateColorCodes('&', actionMessage));
            return;
        }

        if (actionType.equals("idle")) {
            String displayName = plugin.getUpgradeManager().getDisplayName(minionTypeLower, tier);

            String hologramMessage = plugin.getUpgradeManager().getHologramMessage(minionTypeLower, tier);
            resetLook();

            if (displayName != null && !displayName.isEmpty()) {
                if (hologramMessage != null && !hologramMessage.isEmpty() && Math.random() < 0.2) {
                    minionArmorStand.setCustomName(ChatColor.translateAlternateColorCodes('&', hologramMessage));
                } else {
                    minionArmorStand.setCustomName(ChatColor.translateAlternateColorCodes('&', displayName));
                }
                return;
            }

            if (hologramMessage != null && !hologramMessage.isEmpty()) {
                minionArmorStand.setCustomName(ChatColor.translateAlternateColorCodes('&', hologramMessage));
                return;
            }
        } else {
            String customHologram = plugin.getUpgradeManager().getHologramMessage(minionTypeLower, tier);
            if (customHologram != null && !customHologram.isEmpty()) {
                minionArmorStand.setCustomName(ChatColor.translateAlternateColorCodes('&', customHologram));
                return;
            }
        }

        minionArmorStand.setCustomName(defaultName);
    }

    private void processFarmerCell(int idx) {
        World world = minionArmorStand.getWorld();
        Inventory storage = plugin.getMinionStorage(minionArmorStand.getUniqueId());
        Inventory chestInv = checkForChest(world, minionArmorStand.getLocation());

        var persistentDataContainer = minionArmorStand.getPersistentDataContainer();
        String stateStr = persistentDataContainer.getOrDefault(plugin.farmerStateKey, PersistentDataType.STRING, FarmerState.HARVESTING.name());
        FarmerState currentState;
        try {
            currentState = FarmerState.valueOf(stateStr);
        } catch (IllegalArgumentException e) {
            currentState = FarmerState.HARVESTING;
        }

        String targetStr = persistentDataContainer.getOrDefault(plugin.targetKey, PersistentDataType.STRING, Material.WHEAT.name());
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

        int tier = persistentDataContainer.getOrDefault(plugin.tierKey, PersistentDataType.INTEGER, 1);
        double boneMealChance = plugin.getUpgradeManager().getBoneMealChance(tier);

        switch (currentState) {
            case HOEING:
                if (targetCrop != Material.NETHER_WART) {
                    if (soilBlock.getType() == Material.DIRT || soilBlock.getType() == Material.GRASS_BLOCK) {
                        soilBlock.setType(Material.FARMLAND);
                        setMinionCustomName(ChatColor.GREEN + "Hoeing...");
                        minionArmorStand.setRightArmPose(new EulerAngle(Math.toRadians(280), 0, 0));
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            minionArmorStand.setRightArmPose(new EulerAngle(0, 0, 0));
                        }, 10L);
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
                Material cropProduct = getHarvestedProduct(targetCrop);
                boolean storageSystemFull = isStorageFull(cropProduct, storage, chestInv);

                if (storageSystemFull) {
                    setMinionCustomName(ChatColor.RED + "Storage Full!");

                    UUID ownerUUID = minionArmorStand.getPersistentDataContainer()
                            .getOrDefault(plugin.ownerKey, PersistentDataType.STRING, "")
                            .isEmpty() ? null : UUID.fromString(minionArmorStand.getPersistentDataContainer()
                            .get(plugin.ownerKey, PersistentDataType.STRING));

                    if (ownerUUID != null) {
                        Player owner = Bukkit.getPlayer(ownerUUID);
                        sendStorageFullNotification(owner);
                    }
                    break;
                }

                // Harvest logic
                Material plantableCrop = getPlantableCrop(targetCrop);
                if (cropBlock.getType() == plantableCrop && cropBlock.getBlockData() instanceof Ageable ageable) {
                    if (ageable.getAge() == ageable.getMaximumAge()) {
                        harvestAndReplant(cropBlock, targetCrop);
                    } else {
                        setMinionCustomName(ChatColor.YELLOW + "Waiting to grow");

                        if (boneMealChance > 0 && Math.random() < boneMealChance) {
                            int currentAge = ageable.getAge();
                            int maxAge = ageable.getMaximumAge();

                            if (currentAge < maxAge) {
                                Ageable newAgeable = (Ageable) ageable.clone();
                                newAgeable.setAge(Math.min(currentAge + 1, maxAge));
                                cropBlock.setBlockData(newAgeable);
                                setMinionCustomName(ChatColor.GREEN + "Applied Bonemeal Effect");
                            }
                        }
                    }
                }
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

    private Material getHarvestedProduct(Material crop) {
        return switch (crop) {
            case WHEAT, WHEAT_SEEDS -> Material.WHEAT;
            case CARROTS, CARROT -> Material.CARROT;
            case POTATOES, POTATO -> Material.POTATO;
            case BEETROOTS, BEETROOT, BEETROOT_SEEDS -> Material.BEETROOT;
            case NETHER_WART -> Material.NETHER_WART;
            default -> crop;
        };
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
        minionArmorStand.setRightArmPose(new EulerAngle(Math.toRadians(280), 0, 0));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            minionArmorStand.setRightArmPose(new EulerAngle(0, 0, 0));
        }, 10L);

        lookAtBlock(block);

        World world = block.getWorld();
        Inventory storage = plugin.getMinionStorage(minionArmorStand.getUniqueId());
        Inventory chestInv = checkForChest(world, minionArmorStand.getLocation());
        Material seedType = getSeedMaterial(targetCrop);

        int tier = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.tierKey, PersistentDataType.INTEGER, 1);
        double doubleCropsChance = plugin.getUpgradeManager().getDoubleCropsChance(tier);

        Collection<ItemStack> drops = new ArrayList<>(block.getDrops());

        MinionStats stats = plugin.getMinionStats(minionArmorStand.getUniqueId());
        stats.incrementTimesHarvested();

        if (doubleCropsChance > 0 && Math.random() < doubleCropsChance) {
            Collection<ItemStack> extraDrops = new ArrayList<>();
            for (ItemStack drop : drops) {
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

        Block soil = block.getRelative(0, -1, 0);
        if (soil.getType() == Material.FARMLAND || (getPlantableCrop(targetCrop) == Material.NETHER_WART && soil.getType() == Material.SOUL_SAND)) {
            block.setType(getPlantableCrop(targetCrop));
        }

        boolean wantsSeeds = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.wantsSeedsKey, PersistentDataType.BYTE, (byte)1) == 1;
        if (!wantsSeeds) {
            drops.removeIf(item -> item.getType() == seedType ||
                           item.getType() == Material.WHEAT_SEEDS ||
                           item.getType() == Material.BEETROOT_SEEDS);
        }

        for (ItemStack drop : drops) {
            if (drop.getAmount() > 0) {
                addToStorage(drop, storage, chestInv, world, block.getLocation());
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::resetLook, 20L); // 1 second later
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
        lookAtBlock(block);
        block.setType(mat);

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
        minionArmorStand.setRightArmPose(new EulerAngle(Math.toRadians(280), 0, 0));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            minionArmorStand.setRightArmPose(new EulerAngle(0, 0, 0));
        }, 10L);


        lookAtBlock(block);

        int tier = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.tierKey, PersistentDataType.INTEGER, 1);
        double fortuneChance = plugin.getUpgradeManager().getFortuneChance(tier);

        Collection<ItemStack> drops = new ArrayList<>(block.getDrops());

        MinionStats stats = plugin.getMinionStats(minionArmorStand.getUniqueId());
        stats.incrementItemsMined();

        if (fortuneChance > 0 && Math.random() < fortuneChance) {
            Collection<ItemStack> extraDrops = new ArrayList<>();
            for (ItemStack drop : drops) {
                ItemStack extraDrop = drop.clone();
                if (!isSpecialRareDrop(extraDrop.getType())) {
                    extraDrops.add(extraDrop);
                    setMinionCustomName(ChatColor.GREEN + "Fortune Effect!");
                    stats.incrementFortuneProcs();
                }
            }
            drops.addAll(extraDrops);
        }

        for (ItemStack drop : drops) {
            addToStorage(drop, storage, chestInv, world, block.getLocation());
        }

        block.setType(Material.AIR);
        Bukkit.getScheduler().runTaskLater(plugin, this::resetLook, 20L); // 1 second later
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
        private final String inventoryType;

        public MinionInventoryHolder(UUID minionUUID) {
            this(minionUUID, "control");
        }

        public MinionInventoryHolder(UUID minionUUID, String inventoryType) {
            this.minionUUID = minionUUID;
            this.inventoryType = inventoryType;
        }

        public UUID getMinionUUID() {
            return minionUUID;
        }

        public String getInventoryType() {
            return inventoryType;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public Inventory getStatsInventory() {
        Inventory inv = Bukkit.createInventory(new MinionInventoryHolder(minionArmorStand.getUniqueId(), "stats"), 27, "Minion Statistics");

        // Get minion stats
        MinionStats stats = plugin.getMinionStats(minionArmorStand.getUniqueId());


        String minionTypeStr = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.minionTypeKey, PersistentDataType.STRING, MinionType.BLOCK_MINER.name());
        MinionType minionType = MinionType.valueOf(minionTypeStr);
        int tier = minionArmorStand.getPersistentDataContainer().getOrDefault(plugin.tierKey, PersistentDataType.INTEGER, 1);


        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        if (borderMeta != null) {
            borderMeta.setDisplayName(" ");
            border.setItemMeta(borderMeta);
        }

        for (int i = 0; i < 27; i++) {
            if (i < 9 || i > 17 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, border);
            }
        }

        ItemStack titleItem = createItem(Material.DIAMOND_HOE, ChatColor.GOLD +
                (minionType == MinionType.BLOCK_MINER ? "Block Miner" : "Farmer") +
                " Statistics " + ChatColor.WHITE + "[Tier " + tier + "]");


        ItemMeta meta = titleItem.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Target: " + ChatColor.YELLOW + getTargetMaterial().name());
            meta.setLore(lore);
            titleItem.setItemMeta(meta);
        }
        inv.setItem(4, titleItem);

        inv.setItem(10, createStatItem(Material.IRON_PICKAXE, "Items Mined", stats.getItemsMined()));
        inv.setItem(11, createStatItem(Material.IRON_SHOVEL, "Items Placed", stats.getItemsPlaced()));

        int delay = plugin.getUpgradeManager().getDelay(minionType, tier);
        String timeSaved = stats.getFormattedTimeSaved(delay);
        inv.setItem(12, createTextItem(Material.CLOCK, "Time Saved",
                ChatColor.WHITE + timeSaved,
                ChatColor.GRAY + "Based on tier delay: " + ChatColor.YELLOW + delay + "ms"));

        inv.setItem(13, createTextItem(Material.SUNFLOWER, "Uptime",
                ChatColor.WHITE + stats.getFormattedUptime()));


        if (minionType == MinionType.BLOCK_MINER) {
            double fortunePercent = stats.getFortunePercentage();
            inv.setItem(14, createTextItem(Material.DIAMOND, "Fortune Effect",
                    ChatColor.WHITE + " " + stats.getFortuneProcs() + " (" + String.format("%.2f", fortunePercent) + "%)",
                    ChatColor.GRAY + "Extra items from fortune"));
        } else {
            inv.setItem(14, createStatItem(Material.WHEAT, "Times Harvested", stats.getTimesHarvested()));

            double doubleCropsPercent = stats.getDoubleCropsPercentage();
            inv.setItem(15, createTextItem(Material.HAY_BLOCK, "Double Harvests",
                    ChatColor.WHITE + " " + stats.getDoubleCropsProcs() + " (" + String.format("%.2f", doubleCropsPercent) + "%)",
                    ChatColor.GRAY + "Extra crops from double harvest"));
        }

        ItemStack backButton = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta backMeta = backButton.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(ChatColor.RED + "Back");
            backMeta.setLore(List.of(ChatColor.GRAY + "Return to control panel"));
            backButton.setItemMeta(backMeta);
        }
        inv.setItem(22, backButton);

        return inv;
    }

    private ItemStack createStatItem(Material material, String name, long value) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + name);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.WHITE + "" + value);
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

    private void lookAtBlock(Block targetBlock) {
        if (minionArmorStand == null || targetBlock == null) return;

        Vector direction = targetBlock.getLocation().add(0.5, 0.5, 0.5)
                .subtract(minionArmorStand.getEyeLocation()).toVector();

        double pitch = -1 * Math.atan2(direction.getY(),
                Math.sqrt(direction.getX() * direction.getX() + direction.getZ() * direction.getZ()));

        float yaw = (float) Math.toDegrees(Math.atan2(direction.getZ(), direction.getX())) - 90;
        minionArmorStand.setRotation(yaw, 0);

        minionArmorStand.setHeadPose(new EulerAngle(pitch, 0, 0));
    }

    public void resetLook() {
        ArmorStand stand = minionArmorStand;
        if (stand != null) {
            stand.setHeadPose(new EulerAngle(0, 0, 0));
        }
    }

    public ArmorStand getMinionArmorStand() {
        return minionArmorStand;
    }

    public BukkitTask getMiningTask() {
        return miningTask;
    }

    public void setMiningTask(BukkitTask miningTask) {
        this.miningTask = miningTask;
    }

    public boolean isNotifiedStorageFull() {
        return notifiedStorageFull;
    }

    public void setNotifiedStorageFull(boolean notifiedStorageFull) {
        this.notifiedStorageFull = notifiedStorageFull;
    }

    public long getLastNotificationTime() {
        return lastNotificationTime;
    }

    public void setLastNotificationTime(long lastNotificationTime) {
        this.lastNotificationTime = lastNotificationTime;
    }

    public String getString() {
        return string;
    }

    public void setString(String string) {
        this.string = string;
    }

    public boolean isBundleAlert() {
        return bundleAlert;
    }
}
