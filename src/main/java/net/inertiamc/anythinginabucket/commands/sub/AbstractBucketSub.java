package net.inertiamc.anythinginabucket.commands.sub;

import net.inertiamc.core.command.SubSupport;
import net.inertiamc.anythinginabucket.AnythingInABucket;
import net.inertiamc.anythinginabucket.config.MessageManager;

/** What every subcommand shares: inertia-core's {@link SubSupport} (player checks, the error keys) and the plugin. */
public abstract class AbstractBucketSub extends SubSupport {

    protected final AnythingInABucket plugin;

    protected AbstractBucketSub(AnythingInABucket plugin) {
        super(plugin);
        this.plugin = plugin;
    }

    @Override
    protected MessageManager messages() {
        return plugin.getMessageManager();
    }
}
