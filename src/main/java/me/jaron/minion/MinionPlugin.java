package me.jaron.minion;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Objects;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

public class MinionPlugin extends JavaPlugin {

    public final NamespacedKey ownerKey = new NamespacedKey(this, "minion_owner");
    public final NamespacedKey isMinionKey = new NamespacedKey(this, "is_minion");
    public final NamespacedKey minionEggKey = new NamespacedKey(this, "minion_spawn_egg");
    public final NamespacedKey tierKey = new NamespacedKey(this, "minion_tier");
    public final NamespacedKey modeKey = new NamespacedKey(this, "minion_mode");
    public final NamespacedKey indexKey = new NamespacedKey(this, "minion_index");
    public final NamespacedKey targetKey = new NamespacedKey(this, "minion_target");

    private final Set<UUID> automationPlayers = new HashSet<>();
    private final Map<UUID, Inventory> minionInventories = new HashMap<>();
    private MinionBundleManager bundleManager;

    public Inventory getMinionStorage(UUID uuid) {
        return minionInventories.computeIfAbsent(uuid, u -> {
            Entity e = Bukkit.getServer().getEntity(u);
            int tier = 1;
            if (e instanceof ArmorStand as) {
                tier = as.getPersistentDataContainer().getOrDefault(tierKey, PersistentDataType.INTEGER, 1);
            }
            int size = Math.min(tier * 9, 54);
            return Bukkit.createInventory(new StorageHolder(u), size, ChatColor.AQUA + "Minion Storage");
        });
    }

    public class StorageHolder implements InventoryHolder {
        private final UUID uuid;
        public StorageHolder(UUID uuid) { this.uuid = uuid; }
        public UUID getMinionUUID() { return uuid; }
        @Override
        public Inventory getInventory() { return minionInventories.get(uuid); }
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new ArmorStandClicked(this), this);
        getServer().getPluginManager().registerEvents(new InventoryClick(this), this);
        getServer().getPluginManager().registerEvents(new MinionSpawnListener(this), this);
        getServer().getPluginManager().registerEvents(new BundleInteractListener(this), this);

        bundleManager = new MinionBundleManager(this);

        Objects.requireNonNull(getCommand("createminion")).setExecutor(new CreateMinionCommand(this));
        Objects.requireNonNull(getCommand("showminioninventory")).setExecutor(new ShowMinionInventoryCommand(this));
        Objects.requireNonNull(getCommand("giveminionegg")).setExecutor(new GiveMinionEggCommand(this));
        Objects.requireNonNull(getCommand("minionautomation")).setExecutor(new MinionAutomationCommand(this));
        Objects.requireNonNull(getCommand("collectall")).setExecutor(new CollectAllCommand(this));
        Objects.requireNonNull(getCommand("getbundle")).setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
                return true;
            }
            bundleManager.createBundle(player);
            player.sendMessage(ChatColor.GREEN + "You received a Minion Collection Bundle!");
            return true;
        });
    }

    public MinionBundleManager getBundleManager() {
        return bundleManager;
    }

    public void spawnMinion(Player owner, Location location) {
        if (location.getWorld() == null) return;

        location.getWorld().spawn(location, ArmorStand.class, as -> {
            as.setBasePlate(false);
            as.setSmall(true);
            as.setGravity(false);
            as.setInvulnerable(true);
            as.setArms(true);

            as.setCustomNameVisible(true);

            if (as.getEquipment() != null) {
                as.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
                as.getEquipment().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
                as.getEquipment().setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
                as.getEquipment().setBoots(new ItemStack(Material.LEATHER_BOOTS));
            }

            as.getPersistentDataContainer().set(isMinionKey, PersistentDataType.BYTE, (byte) 1);
            as.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
            as.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, 1);
            as.getPersistentDataContainer().set(modeKey, PersistentDataType.BYTE, (byte) 0);
            as.getPersistentDataContainer().set(indexKey, PersistentDataType.INTEGER, 0);
            as.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, Material.COBBLESTONE.name());

            // auto-start if owner has automation active
            if (automationPlayers.contains(owner.getUniqueId())) {
                new Minion(this, as).startAutomation();
            }
        });

        owner.sendMessage(ChatColor.GREEN + "A new CobbleMinion has been spawned!");
    }

    /** Called by MinionAutomationCommand */
    public boolean isAutomationActive(UUID player) {
        return automationPlayers.contains(player);
    }
    public void setAutomationActive(UUID player, boolean active) {
        if (active) automationPlayers.add(player);
        else automationPlayers.remove(player);
    }
}