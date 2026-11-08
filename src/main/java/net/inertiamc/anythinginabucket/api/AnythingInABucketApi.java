package net.inertiamc.anythinginabucket.api;

import net.inertiamc.core.SchedulerUtil;
import net.inertiamc.anythinginabucket.AnythingInABucket;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * The API other plugins reach by reflection (inertia-core's {@code hook.ReflectiveApi}). Method names and parameter
 * types are a runtime contract: core's {@code ItemTokens} calls {@code restamp} and {@code left} by name.
 */
public final class AnythingInABucketApi {

    private final AnythingInABucket plugin;

    public AnythingInABucketApi(AnythingInABucket plugin) {
        this.plugin = plugin;
    }

    /**
     * Gives an online player Capture Buckets on their thread. Returns false if they are offline, the amount is outside
     * 1 to 64 or the Capture Bucket is disabled.
     */
    public boolean giveBuckets(UUID player, int amount) {
        Player target = plugin.getServer().getPlayer(player);
        if (target == null || amount < 1 || amount > 64 || !plugin.getConfigManager().getSettings().catchWith().capture()) return false;
        SchedulerUtil.runForEntity(plugin, target, () -> plugin.getItems().giveBuckets(target, amount));
        return true;
    }

    /** The mob type key of a filled bucket (e.g. {@code horse}), or null if the item is not a filled bucket. */
    public String mobIn(ItemStack item) {
        return plugin.getItems().filledOf(item) == null ? null : plugin.getItems().mobOf(item);
    }

    /** Safe from any thread. Called by name from core's {@code ItemTokens.restampAny}. */
    public ItemStack restamp(ItemStack item) {
        return plugin.getItems().tokens().token(item) == null ? item : plugin.getItems().tokens().restamp(item);
    }

    /** How many uses an item code has left. Called by name from core's {@code ItemTokens.leftAny}. */
    public int left(String token) {
        return plugin.getItems().tokens().ledger().left(token);
    }
}
