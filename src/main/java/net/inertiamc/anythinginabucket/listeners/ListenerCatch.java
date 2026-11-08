package net.inertiamc.anythinginabucket.listeners;

import net.inertiamc.anythinginabucket.AnythingInABucket;
import net.inertiamc.anythinginabucket.config.ConfigManager;
import net.inertiamc.core.SchedulerUtil;
import org.bukkit.Material;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Right-clicking a mob with a Capture Bucket catches it. Clicking a mob with a filled bucket does nothing, so the spawn
 * egg underneath cannot hatch a baby. Runs at HIGH and skips clicks another plugin cancelled (such as a protection
 * plugin), unless respect-claims is off.
 */
public final class ListenerCatch implements Listener {

    private final AnythingInABucket plugin;
    /** World tick of each player's last mob click, so one click is one catch. */
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    public ListenerCatch(AnythingInABucket plugin) {
        this.plugin = plugin;
    }

    /**
     * Paper 26.2 fires only {@link PlayerInteractAtEntityEvent} for a right click on an entity, while older servers fire
     * it and then {@link PlayerInteractEntityEvent}. The two have separate handler lists, so both are handled.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClickAt(PlayerInteractAtEntityEvent event) {
        rightClick(event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEntityEvent event) {
        if (!(event instanceof PlayerInteractAtEntityEvent)) rightClick(event);
    }

    private void rightClick(PlayerInteractEntityEvent event) {
        ConfigManager.Settings settings = plugin.getConfigManager().getSettings();
        if (event.isCancelled() && settings.respectClaims()) return;
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        ItemStack held = player.getInventory().getItem(hand);
        ConfigManager.CatchWith catchWith = settings.catchWith();
        boolean bucket = plugin.getItems().isBucket(held);
        boolean filled = !bucket && plugin.getItems().filledOf(held) != null;
        boolean plain = !bucket && !filled && held != null && held.getType() == Material.BUCKET && !held.hasItemMeta()
                && player.isSneaking() && catchWith.normal();
        if (!bucket && !filled && !plain) return;
        event.setCancelled(true);
        resync(player);
        if (plugin.getBuckets().heldClick(player)) return;
        if (filled || !(clicked(event.getRightClicked()) instanceof Mob mob) || mob instanceof Player) return;
        if (bucket && !catchWith.capture()) {
            plugin.getMessageManager().sendMessage(player, "catch.capture-off");
            return;
        }
        // Game time, not time of day: the day clock stops when a server turns the daylight cycle off.
        long tick = player.getWorld().getGameTime();
        Long last = lastClick.put(player.getUniqueId(), tick);
        if (last != null && last == tick) return;
        plugin.getBuckets().tryCatch(player, mob, hand, held, plain);
    }

    /**
     * The client predicts bucket use on a mob (milking a cow, scooping a fish; in creative the result lands in a free
     * slot) and a cancelled click is not sent back, so the inventory is resent on the next tick.
     */
    private void resync(Player player) {
        SchedulerUtil.runForEntity(plugin, player, player::updateInventory);
    }

    /** A click on the ender dragon hits one of its body parts, a separate entity whose parent is the dragon. */
    static Entity clicked(Entity entity) {
        return entity instanceof ComplexEntityPart part ? part.getParent() : entity;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastClick.remove(event.getPlayer().getUniqueId());
        plugin.getBuckets().quit(event.getPlayer().getUniqueId());
    }
}
