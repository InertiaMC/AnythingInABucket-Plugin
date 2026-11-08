package net.inertiamc.anythinginabucket.commands;

import net.inertiamc.core.command.CommandRouter;
import net.inertiamc.anythinginabucket.AnythingInABucket;
import net.inertiamc.anythinginabucket.commands.sub.SubAdmin;
import net.inertiamc.anythinginabucket.commands.sub.SubGive;
import net.inertiamc.anythinginabucket.commands.sub.SubHelp;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;

import java.util.List;

/** {@code /bucket}: wiring only. No arguments or an unknown subcommand shows the help. */
public final class CommandBucket {

    private CommandBucket() {}

    public static CommandExecutor buildExecutor(AnythingInABucket plugin) {
        CommandRouter router = buildRouter(plugin);
        return (sender, command, label, args) -> {
            if (args.length > 0 && (router.find(args[0]) == null || hidden(plugin, args[0]))) {
                SubHelp.send(plugin, sender, new String[0], 0);
                return true;
            }
            return router.onCommand(sender, command, label, args);
        };
    }

    public static TabCompleter buildTabCompleter(AnythingInABucket plugin) {
        CommandRouter router = buildRouter(plugin);
        return (sender, command, label, args) -> {
            List<String> out = router.onTabComplete(sender, command, label, args);
            if (out == null || args.length > 1 && hidden(plugin, args[0])) return List.of();
            return args.length == 1 ? out.stream().filter(name -> !hidden(plugin, name)).toList() : out;
        };
    }

    /** {@code give} while the Capture Bucket is off: there is nothing to give. */
    static boolean hidden(AnythingInABucket plugin, String sub) {
        return sub.equalsIgnoreCase("give") && !plugin.getConfigManager().getSettings().catchWith().capture();
    }

    static CommandRouter buildRouter(AnythingInABucket plugin) {
        return new CommandRouter()
                .messages(plugin.getMessageManager())
                .onEmptyArgs((sender, args) -> SubHelp.send(plugin, sender, new String[0], 0))
                .register(new SubHelp(plugin))
                .register(new SubGive(plugin))
                .register(new SubAdmin(plugin));
    }
}
