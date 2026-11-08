package net.inertiamc.anythinginabucket.managers;

import net.inertiamc.core.SchedulerUtil;
import net.inertiamc.anythinginabucket.AnythingInABucket;
import net.inertiamc.anythinginabucket.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ShapedRecipe;

import java.util.Map;

/**
 * The Capture Bucket's crafting recipe ({@code anythinginabucket:capture_bucket}), added at enable and again on reload.
 * Its result is an unstamped bucket shown only in the grid; ListenerGuard crafts the stamped one when it is taken.
 */
public final class BucketRecipe {

    private final AnythingInABucket plugin;
    private final NamespacedKey key;
    private volatile boolean registered;

    public BucketRecipe(AnythingInABucket plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "capture_bucket");
    }

    public NamespacedKey key() {
        return key;
    }

    public boolean registered() {
        return registered;
    }

    /** Removes the old recipe and adds the configured one, on the global thread. */
    public void apply() {
        SchedulerUtil.runTask(plugin, () -> {
            remove();
            ConfigManager.RecipeSpec spec = plugin.getConfigManager().getSettings().recipe();
            if (spec == null) return;
            ShapedRecipe recipe = new ShapedRecipe(key, plugin.getItems().bucket(1));
            recipe.shape(spec.shape().toArray(new String[0]));
            for (Map.Entry<Character, Material> ingredient : spec.ingredients().entrySet()) {
                recipe.setIngredient(ingredient.getKey(), ingredient.getValue());
            }
            registered = plugin.getServer().addRecipe(recipe);
            if (registered) {
                plugin.getServer().getOnlinePlayers().forEach(player -> SchedulerUtil.runForEntity(plugin, player,
                        () -> player.discoverRecipe(key)));
            }
        });
    }

    /** Idempotent. */
    public void remove() {
        if (plugin.getServer().getRecipe(key) != null) plugin.getServer().removeRecipe(key);
        registered = false;
    }
}
