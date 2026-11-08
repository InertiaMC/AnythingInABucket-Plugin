package net.inertiamc.anythinginabucket.commands.sub;

import net.inertiamc.core.command.CommandRouter;
import net.inertiamc.anythinginabucket.AnythingInABucket;
import net.inertiamc.anythinginabucket.commands.sub.admin.SubAdminInfo;
import net.inertiamc.anythinginabucket.commands.sub.admin.SubAdminReload;
import net.inertiamc.anythinginabucket.util.PermissionNodes;
import org.bukkit.command.CommandSender;

import java.util.List;

/** {@code /bucket admin <sub>}: its own router. No arguments or an unknown subcommand shows the help. */
public final class SubAdmin extends AbstractBucketSub {

    private final CommandRouter router;

    public SubAdmin(AnythingInABucket plugin) {
        super(plugin);
        this.router = new CommandRouter()
                .messages(plugin.getMessageManager())
                .onEmptyArgs((sender, args) -> SubHelp.send(plugin, sender, new String[0], 0))
                .register(new SubAdminInfo(plugin))
                .register(new SubAdminReload(plugin));
    }

    @Override public String name() { return "admin"; }
    @Override public String permission() { return PermissionNodes.ADMIN; }
    @Override public String usage() { return "/bucket admin <command>"; }
    @Override public String description() { return "Bucket admin commands."; }
    @Override public String example() { return "/bucket admin info"; }
    @Override public boolean hideWhenNoPermission() { return true; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length > 0 && router.find(args[0]) == null) {
            SubHelp.send(plugin, sender, new String[0], 0);
            return true;
        }
        return router.onCommand(sender, null, "bucket", args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return router.onTabComplete(sender, null, "bucket", args);
    }
}
