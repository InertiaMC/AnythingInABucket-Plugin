package net.inertiamc.anythinginabucket.managers;

import net.inertiamc.core.FormatUtil;
import net.inertiamc.core.SchedulerUtil;
import net.inertiamc.core.item.ItemTokens;
import net.inertiamc.core.util.Cooldowns;
import net.inertiamc.anythinginabucket.AnythingInABucket;
import net.inertiamc.anythinginabucket.config.ConfigManager;
import net.inertiamc.anythinginabucket.models.StoredMob;
import net.inertiamc.anythinginabucket.storage.BucketStorage;
import net.inertiamc.anythinginabucket.util.PermissionNodes;
import net.inertiamc.anythinginabucket.util.Settle;
import net.inertiamc.anythinginabucket.util.Words;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Catching and releasing. A caught mob is saved whole to the database under the filled bucket's id and removed from the
 * world in the same tick. Releasing uses up the filled bucket's item code before the mob is spawned, so a copy releases
 * nothing.
 *
 * <p>The sets are concurrent because on Folia each player clicks on their own region thread.
 */
public final class BucketManager {

    /** True while a test place event is being fired to ask protection plugins. */
    public static final ThreadLocal<Boolean> CHECKING = ThreadLocal.withInitial(() -> false);

    private final AnythingInABucket plugin;
    private final BucketStorage storage;
    private final Cooldowns cooldowns = new Cooldowns();
    /** Players whose release is reading its mob from the database. */
    private final Set<UUID> releasing = ConcurrentHashMap.newKeySet();
    /** Filled buckets being released by a dispenser, so a fast redstone clock reads each one only once. */
    private final Set<UUID> dispensing = ConcurrentHashMap.newKeySet();
    /** Ignores the repeats of a held right click after a catch or release. */
    private final Settle settle = new Settle();

    public BucketManager(AnythingInABucket plugin, BucketStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    private ConfigManager.Settings settings() {
        return plugin.getConfigManager().getSettings();
    }

    private BucketItems items() {
        return plugin.getItems();
    }

    private ItemTokens tokens() {
        return plugin.getItems().tokens();
    }

    // ---------------------------------------------------------------- catching

    /**
     * Runs on the player's thread, which also owns the mob. {@code plain} means a normal bucket rather than a Capture
     * Bucket.
     */
    public void tryCatch(Player player, Mob mob, EquipmentSlot hand, ItemStack held, boolean plain) {
        ConfigManager.Settings settings = settings();
        String type = mob.getType().getKey().getKey();
        String kind = Words.pretty(type);
        if (!player.hasPermission(PermissionNodes.CATCH)) {
            deny(player, "catch.no-permission");
            return;
        }
        if (!player.hasPermission(PermissionNodes.catchMob(type))) {
            deny(player, "catch.no-permission-mob", "mob", kind);
            return;
        }
        if (settings.disabledWorlds().contains(mob.getWorld().getName())) {
            deny(player, "catch.world");
            return;
        }
        if (!mob.isValid() || mob.isDead()) return;
        if (settings.blacklist().contains(type)) {
            deny(player, "catch.blacklisted", "mob", kind);
            return;
        }
        if (pluginMob(mob)) {
            deny(player, "catch.plugin-mob");
            return;
        }
        // Paper reports every camel as tamed with no owner, so only a mob with an owner counts as a pet.
        if (settings.petsOwnerOnly() && mob instanceof Tameable tameable && tameable.isTamed()
                && !player.hasPermission(PermissionNodes.BYPASS_OWNER)) {
            AnimalTamer owner = tameable.getOwner();
            if (owner != null && !owner.getUniqueId().equals(player.getUniqueId())) {
                String name = owner.getName() == null ? plugin.getMessageManager().getRawMessage("filled.words.unknown") : owner.getName();
                deny(player, "catch.not-yours", "mob", kind, "owner", name);
                return;
            }
        }
        for (Entity passenger : mob.getPassengers()) {
            if (passenger instanceof Player) {
                deny(player, "catch.ridden", "mob", kind);
                return;
            }
        }
        if (settings.respectClaims() && !mayBuild(player, mob.getLocation().getBlock(), held, hand)) {
            deny(player, "catch.protected");
            return;
        }
        long cooldown = settings.cooldown().toMillis();
        long left = cooldowns.remainingMillis(player.getUniqueId(), cooldown);
        if (left > 0) {
            deny(player, "catch.cooldown", "time", FormatUtil.formatDuration(Math.max(1, (left + 999) / 1000)));
            return;
        }
        boolean coded = !plain && settings.copyProtection();
        if (coded && tokens().backed(held) <= 0) {
            tokens().reportCopy(player, held);
            deny(player, "catch.copy");
            return;
        }

        for (Entity passenger : List.copyOf(mob.getPassengers())) mob.removePassenger(passenger);
        if (mob.isInsideVehicle()) mob.leaveVehicle();
        if (mob.isLeashed()) {
            mob.setLeashHolder(null);
            mob.getWorld().dropItemNaturally(mob.getLocation(), new ItemStack(Material.LEAD));
        }
        List<MobFacts.Fact> facts = MobFacts.read(mob, (key, placeholders) ->
                plugin.getMessageManager().getRawMessage("filled.words." + key, placeholders));
        String head = HeadKey.read(mob);
        MobCodec.Saved saved;
        try {
            saved = MobCodec.save(mob);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save a " + type + " into a bucket for " + player.getName() + ".", e);
            deny(player, "catch.failed");
            return;
        }
        if (coded && !tokens().use(player, held, 1)) {
            deny(player, "catch.copy");
            return;
        }
        cooldowns.tryUse(player.getUniqueId(), cooldown);
        settle.acted(player.getUniqueId());
        mob.remove();

        UUID id = UUID.randomUUID();
        StoredMob stored = new StoredMob(id, type, saved.format(), saved.data(), saved.dataVersion(),
                plain ? StoredMob.PLAIN_BUCKET : StoredMob.CAPTURE_BUCKET, player.getUniqueId(), System.currentTimeMillis());
        storage.write(database -> database.save(stored));
        ItemStack filled = items().filled(id, type, head, mob.getCustomName(), player.getName(), facts, player.getUniqueId());

        held.setAmount(held.getAmount() - 1);
        if (held.getAmount() <= 0) {
            player.getInventory().setItem(hand, filled);
        } else {
            player.getInventory().setItem(hand, held);
            tokens().give(player, filled);
        }
        plugin.getConfigManager().sound("catch").play(player);
        plugin.getMessageManager().sendMessage(player, "catch.caught", "mob", kind);
    }

    /**
     * A mob owned by another plugin: a Citizens NPC, or one with data under another plugin's namespace (dungeon mobs,
     * arena bosses, minions).
     */
    static boolean pluginMob(Mob mob) {
        if (mob.hasMetadata("NPC")) return true;
        for (NamespacedKey key : mob.getPersistentDataContainer().getKeys()) {
            if (!key.getNamespace().equals(NamespacedKey.MINECRAFT) && !key.getNamespace().equals("anythinginabucket")) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- releasing

    /**
     * Reads the mob from the database off the server thread, then releases it on the player's thread if they still hold
     * the bucket.
     */
    public void tryRelease(Player player, EquipmentSlot hand, ItemStack held, Block clicked, BlockFace face) {
        UUID id = items().filledOf(held);
        if (id == null) return;
        if (!player.hasPermission(PermissionNodes.RELEASE)) {
            deny(player, "release.no-permission");
            return;
        }
        ConfigManager.Settings settings = settings();
        if (settings.disabledWorlds().contains(clicked.getWorld().getName())) {
            deny(player, "catch.world");
            return;
        }
        Block spot = clicked.isPassable() ? clicked : clicked.getRelative(face);
        if (!spot.isPassable()) {
            deny(player, "release.blocked");
            return;
        }
        if (settings.respectClaims() && !mayBuild(player, spot, held, hand)) {
            deny(player, "release.protected");
            return;
        }
        if (settings.copyProtection() && tokens().backed(held) <= 0) {
            tokens().reportCopy(player, held);
            deny(player, "release.copy");
            return;
        }
        if (!releasing.add(player.getUniqueId())) return;
        Location at = spot.getLocation().add(0.5, 0, 0.5);
        at.setYaw(player.getLocation().getYaw() + 180);
        storage.afterWrites(() -> {
            StoredMob mob = null;
            boolean failed = false;
            try {
                mob = storage.database().mob(id);
            } catch (SQLException e) {
                failed = true;
                plugin.getLogger().log(Level.SEVERE, "Could not read filled bucket " + id + ".", e);
            }
            StoredMob found = mob;
            boolean broken = failed;
            SchedulerUtil.runForEntity(plugin, player, () -> finishRelease(player, hand, id, found, broken, at));
        });
    }

    private void finishRelease(Player player, EquipmentSlot hand, UUID id, StoredMob mob, boolean failed, Location at) {
        releasing.remove(player.getUniqueId());
        if (!player.isOnline()) return;
        ItemStack held = player.getInventory().getItem(hand);
        if (!id.equals(items().filledOf(held))) return;
        if (failed) {
            deny(player, "release.failed");
            return;
        }
        if (mob == null) {
            deny(player, "release.empty");
            return;
        }
        if (!SchedulerUtil.isOwnedByCurrentRegion(at)) {
            deny(player, "release.blocked");
            return;
        }
        // With copy protection off the row is kept, so every copy can release the same mob again.
        boolean coded = settings().copyProtection();
        if (coded && !tokens().use(player, held, 1)) {
            deny(player, "release.copy");
            return;
        }
        Entity spawned;
        try {
            spawned = MobCodec.spawn(mob, at);
        } catch (ReflectiveOperationException | RuntimeException e) {
            spawned = null;
            plugin.getLogger().log(Level.SEVERE, "Could not release filled bucket " + id + " (" + mob.type() + ", " + mob.format()
                    + ", data version " + mob.dataVersion() + ") for " + player.getName() + ".", e);
        }
        if (spawned == null) {
            if (coded) tokens().restore(held, 1);
            deny(player, "release.failed");
            return;
        }
        settle.acted(player.getUniqueId());
        if (coded) storage.write(database -> database.delete(id));
        ItemStack back = null;
        if (settings().giveBack()) {
            // A Capture Bucket only while Capture Buckets are enabled, otherwise a normal bucket.
            back = StoredMob.PLAIN_BUCKET.equals(mob.bucket()) || !settings().catchWith().capture()
                    ? new ItemStack(Material.BUCKET) : items().stampedBucket(1, player.getUniqueId());
        }
        player.getInventory().setItem(hand, back);
        plugin.getConfigManager().sound("release").play(player);
        plugin.getMessageManager().sendMessage(player, "release.released", "mob", Words.pretty(mob.type()));
    }

    /**
     * Releases a filled bucket fired by a dispenser into the block in front, facing away from it. The empty bucket takes
     * its slot if give-bucket-back is on. There is no player to message, so a copy, a blocked spot or a missing mob
     * leaves the bucket where it is. Runs on the dispenser's region thread.
     */
    public void dispense(Block dispenser, ItemStack item) {
        UUID id = items().filledOf(item);
        if (id == null || settings().disabledWorlds().contains(dispenser.getWorld().getName())) return;
        if (!(dispenser.getBlockData() instanceof Directional directional)) return;
        BlockFace facing = directional.getFacing();
        Block spot = dispenser.getRelative(facing);
        if (!spot.isPassable()) return;
        if (settings().copyProtection() && tokens().backed(item) <= 0) return;
        if (!dispensing.add(id)) return;
        Location at = spot.getLocation().add(0.5, 0, 0.5);
        at.setYaw(yaw(facing));
        storage.afterWrites(() -> {
            StoredMob mob = null;
            try {
                mob = storage.database().mob(id);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not read filled bucket " + id + " for a dispenser.", e);
            }
            StoredMob found = mob;
            SchedulerUtil.runAtLocation(plugin, dispenser.getLocation(), () -> finishDispense(dispenser, id, found, at));
        });
    }

    private void finishDispense(Block dispenser, UUID id, StoredMob mob, Location at) {
        dispensing.remove(id);
        if (mob == null || !at.getBlock().isPassable() || !(dispenser.getState() instanceof Container container)) return;
        Inventory inventory = container.getInventory();
        int slot = -1;
        for (int i = 0; i < inventory.getSize() && slot < 0; i++) {
            if (id.equals(items().filledOf(inventory.getItem(i)))) slot = i;
        }
        if (slot < 0) return;   // taken out meanwhile
        ItemStack held = inventory.getItem(slot);
        boolean coded = settings().copyProtection();
        if (coded && !tokens().use(held, 1)) return;
        Entity spawned;
        try {
            spawned = MobCodec.spawn(mob, at);
        } catch (ReflectiveOperationException | RuntimeException e) {
            spawned = null;
            plugin.getLogger().log(Level.SEVERE, "Could not release filled bucket " + id + " (" + mob.type() + ", " + mob.format()
                    + ", data version " + mob.dataVersion() + ") from a dispenser.", e);
        }
        if (spawned == null) {
            if (coded) tokens().restore(held, 1);
            return;
        }
        if (coded) storage.write(database -> database.delete(id));
        ItemStack back = null;
        if (settings().giveBack()) {
            back = StoredMob.PLAIN_BUCKET.equals(mob.bucket()) || !settings().catchWith().capture()
                    ? new ItemStack(Material.BUCKET) : items().stampedBucket(1, null);
        }
        inventory.setItem(slot, back);
    }

    /** The yaw that faces away from a dispenser pointing {@code facing}. */
    static float yaw(BlockFace facing) {
        return switch (facing) {
            case NORTH -> 180;
            case EAST -> -90;
            case WEST -> 90;
            default -> 0;
        };
    }

    /** A filled bucket destroyed on the ground takes its mob with it. */
    public void lost(ItemStack item) {
        UUID id = items().filledOf(item);
        if (id != null && settings().copyProtection()) storage.write(database -> database.delete(id));
    }

    // ---------------------------------------------------------------- shared

    /** Whether protection plugins let the player build at the block, checked by firing a place event that is never placed. */
    boolean mayBuild(Player player, Block block, ItemStack held, EquipmentSlot hand) {
        CHECKING.set(true);
        try {
            BlockPlaceEvent event = new BlockPlaceEvent(block, block.getState(), block.getRelative(BlockFace.DOWN), held,
                    player, true, hand);
            plugin.getServer().getPluginManager().callEvent(event);
            return !event.isCancelled() && event.canBuild();
        } finally {
            CHECKING.set(false);
        }
    }

    private void deny(Player player, String key, String... placeholders) {
        plugin.getMessageManager().sendMessage(player, key, placeholders);
        plugin.getConfigManager().sound("deny").play(player);
    }

    /**
     * Checked first on every right click with a Capture Bucket or filled bucket. Returns true for the repeats of a held
     * click after a catch or release, which are ignored.
     */
    public boolean heldClick(Player player) {
        return settle.repeat(player.getUniqueId());
    }

    /** Cooldowns are kept through a relog; only pending releases are dropped. */
    public void quit(UUID player) {
        releasing.remove(player);
        settle.clear(player);
    }

    public void shutdown() {
        releasing.clear();
        settle.clearAll();
    }
}
