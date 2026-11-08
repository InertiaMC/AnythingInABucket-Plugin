package net.inertiamc.anythinginabucket.listeners;

import net.inertiamc.anythinginabucket.AnythingInABucket;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Right-clicking a block with a filled bucket releases its mob there. The vanilla uses of the items underneath (a spawn
 * egg spawning or changing a spawner, a bucket scooping water or using a cauldron) are blocked.
 */
public final class ListenerRelease implements Listener {

    private final AnythingInABucket plugin;
    /** World tick of each player's last release click, so the off-hand event is not a second release. */
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    public ListenerRelease(AnythingInABucket plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        ItemStack held = event.getItem();
        boolean filled = plugin.getItems().filledOf(held) != null;
        if (!filled && !plugin.getItems().isBucket(held)) return;
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        // Read these before denying anything, since a denied click reads as DENY afterwards.
        boolean blockAllowed = event.useInteractedBlock() != Event.Result.DENY;
        boolean itemAllowed = event.useItemInHand() != Event.Result.DENY;
        event.setUseItemInHand(Event.Result.DENY);
        // A chest or door still opens, unless the player sneaks to release against it.
        if (clicked != null && clicked.getType().isInteractable() && !player.isSneaking() && blockAllowed) return;
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setCancelled(true);
        if (plugin.getBuckets().heldClick(player)) return;
        if (!filled || clicked == null) return;
        if (!itemAllowed && plugin.getConfigManager().getSettings().respectClaims()) {
            plugin.getMessageManager().sendMessage(player, "release.protected");
            return;
        }
        // Game time, not time of day: the day clock stops when a server turns the daylight cycle off.
        long tick = player.getWorld().getGameTime();
        Long last = lastClick.put(player.getUniqueId(), tick);
        if (last != null && last == tick) return;
        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();
        plugin.getBuckets().tryRelease(player, hand, held, clicked, event.getBlockFace());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastClick.remove(event.getPlayer().getUniqueId());
    }
}
