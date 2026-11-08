package net.inertiamc.anythinginabucket.listeners;

import net.inertiamc.anythinginabucket.AnythingInABucket;
import net.inertiamc.anythinginabucket.managers.BucketRecipe;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

/**
 * Stops the vanilla items underneath from acting as themselves: dispensers don't hatch filled buckets or fill Capture
 * Buckets, and Capture Buckets don't scoop water. With dispensers-release on, a dispenser releases the mob instead.
 * Crafting is done by hand, one bucket per click, so each bucket gets its own item code.
 */
public final class ListenerGuard implements Listener {

    private final AnythingInABucket plugin;

    public ListenerGuard(AnythingInABucket plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        ItemStack item = event.getItem();
        if (!plugin.getItems().ours(item)) return;
        event.setCancelled(true);
        // A protection plugin that refused this dispenser has already cancelled the event (HIGH, ignoreCancelled).
        if (event.getBlock().getType() == Material.DISPENSER && plugin.getItems().filledOf(item) != null
                && plugin.getConfigManager().getSettings().dispensersRelease()) {
            plugin.getBuckets().dispense(event.getBlock(), item);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItem(event.getHand());
        if (plugin.getItems().ours(hand)) event.setCancelled(true);
    }

    /** Crafts by hand, one bucket per click (shift-click included), so every bucket is stamped with its own code. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getRecipe() instanceof Keyed keyed) || !keyed.getKey().equals(plugin.getRecipe().key())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        CraftingInventory grid = event.getInventory();
        ItemStack[] matrix = grid.getMatrix();
        for (int slot = 0; slot < matrix.length; slot++) {
            ItemStack item = matrix[slot];
            if (item == null || item.getAmount() <= 0) continue;
            if (plugin.getItems().ours(item)) return;
            item.setAmount(item.getAmount() - 1);
            matrix[slot] = item.getAmount() <= 0 ? null : item;
        }
        grid.setMatrix(matrix);
        plugin.getItems().tokens().give(player, plugin.getItems().stampedBucket(1, player.getUniqueId()));
        plugin.getConfigManager().sound("catch").play(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        if (event.getRecipe().getKey().equals(plugin.getRecipe().key())) event.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        BucketRecipe recipe = plugin.getRecipe();
        if (recipe.registered()) event.getPlayer().discoverRecipe(recipe.key());
        plugin.getHeadPack().offer(event.getPlayer());
    }
}
