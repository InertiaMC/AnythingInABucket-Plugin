package net.inertiamc.anythinginabucket.util;

/** Every permission node, declared in plugin.yml. */
public final class PermissionNodes {

    private PermissionNodes() {}

    public static final String HELP = "aiab.command.help";
    public static final String GIVE = "aiab.command.give";
    public static final String CATCH = "aiab.catch";
    /** Catching one mob type, e.g. {@code aiab.catch.villager}. Registered at startup, true for everyone. */
    public static String catchMob(String type) {
        return CATCH + "." + type;
    }
    public static final String RELEASE = "aiab.release";
    /** Catching a pet that belongs to someone else. */
    public static final String BYPASS_OWNER = "aiab.bypass.owner";
    /** Opens {@code /bucket admin}; each admin command also needs its own node. aiab.admin grants them all. */
    public static final String ADMIN = "aiab.command.admin";
    public static final String ADMIN_INFO = "aiab.command.admin.info";
    public static final String ADMIN_RELOAD = "aiab.command.admin.reload";
}
