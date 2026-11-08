package net.inertiamc.anythinginabucket;

import net.inertiamc.core.InertiaPlugin;
import net.inertiamc.core.item.ItemTokens;
import net.inertiamc.core.storage.DatabaseBackups;
import net.inertiamc.anythinginabucket.api.AnythingInABucketApi;
import net.inertiamc.anythinginabucket.commands.CommandBucket;
import net.inertiamc.anythinginabucket.config.ConfigManager;
import net.inertiamc.anythinginabucket.config.MessageManager;
import net.inertiamc.anythinginabucket.listeners.ListenerCatch;
import net.inertiamc.anythinginabucket.listeners.ListenerGuard;
import net.inertiamc.anythinginabucket.util.MobPermissions;
import net.inertiamc.anythinginabucket.listeners.ListenerRelease;
import net.inertiamc.anythinginabucket.managers.BucketItems;
import net.inertiamc.anythinginabucket.managers.BucketManager;
import net.inertiamc.anythinginabucket.managers.BucketRecipe;
import net.inertiamc.anythinginabucket.managers.HeadPack;
import net.inertiamc.anythinginabucket.managers.MobCodec;
import net.inertiamc.anythinginabucket.storage.BucketStorage;
import lombok.Getter;
import org.bukkit.NamespacedKey;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * All scheduling goes through SchedulerUtil so the plugin runs on Folia: catching and releasing run on the player's
 * thread (which owns the mob and the blocks next to them), database work on the storage writer thread and the recipe
 * on the global thread.
 */
@Getter
public final class AnythingInABucket extends InertiaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    /** Opened first and closed last, since item codes load from it and pending writes are flushed to it on shutdown. */
    private BucketStorage storage;
    private BucketItems items;
    private BucketManager buckets;
    private BucketRecipe recipe;
    private HeadPack headPack;
    private final MobPermissions mobPermissions = new MobPermissions();

    @Override
    protected void enable() {
        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);
        try {
            storage = BucketStorage.open(getDataFolder(), configManager.database(), getLogger());
            items = new BucketItems(this, new ItemTokens(new NamespacedKey(this, "item-token"), storage.itemTokens()));
        } catch (SQLException e) {
            // Without the database a caught mob could not be saved, so the plugin does not run at all.
            getLogger().log(Level.SEVERE, "Could not open the database; AnythingInABucket is disabled. Check database.yml.", e);
            if (storage != null) storage.close();
            storage = null;
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        buckets = new BucketManager(this, storage);
        items.tokens().whenLost((item, count, player) -> buckets.lost(item));
        recipe = new BucketRecipe(this);
        recipe.apply();
        headPack = new HeadPack(this);
        headPack.refresh();
        getLogger().info("Mobs are saved as " + (MobCodec.paper() ? "Paper's entity data." : "entity snapshots."));

        mobPermissions.register(getServer().getPluginManager());
        listen(items.tokens(), new ListenerCatch(this), new ListenerRelease(this), new ListenerGuard(this));
        command("bucket", CommandBucket.buildExecutor(this), CommandBucket.buildTabCompleter(this));
        startTasks();
    }

    @Override
    protected void startTasks() {
        track(DatabaseBackups.schedule(this, configManager.database()));
    }

    /** Reloads the config files and returns the syntax errors of any file that kept its previous values. */
    public List<String> reload() {
        List<String> errors = new ArrayList<>(configManager.reload());
        messageManager.reload();
        if (messageManager.lastError() != null) errors.add(messageManager.lastError());
        recipe.apply();
        headPack.refresh();
        restartTasks();
        return errors;
    }

    /** Tasks are already cancelled here. Removes the recipe, then flushes queued writes and closes the database. */
    @Override
    protected void disable() {
        mobPermissions.unregister(getServer().getPluginManager());
        if (recipe != null) recipe.remove();
        if (buckets != null) buckets.shutdown();
        if (storage != null) storage.close();
    }

    /** The API other plugins reach by reflection. */
    public AnythingInABucketApi api() {
        return new AnythingInABucketApi(this);
    }
}
