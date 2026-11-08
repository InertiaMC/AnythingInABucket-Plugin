package net.inertiamc.anythinginabucket.util;

import org.bukkit.Registry;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers {@code aiab.catch.<mob>} for every mob type on the server, defaulting to true, so a server can deny one mob
 * to a rank or allow it for one rank only. Built from the game's registry (which changes between versions) so
 * permission plugins can list and complete the nodes; an unregistered node would default to op only.
 */
public final class MobPermissions {

    private final List<Permission> added = new ArrayList<>();

    public void register(PluginManager plugins) {
        for (EntityType type : Registry.ENTITY_TYPE) {
            Class<?> kind = type.getEntityClass();
            if (kind == null || !Mob.class.isAssignableFrom(kind)) continue;
            String node = PermissionNodes.catchMob(type.getKey().getKey());
            if (plugins.getPermission(node) != null) continue;
            Permission permission = new Permission(node, "Catch a " + type.getKey().getKey() + ".", PermissionDefault.TRUE);
            plugins.addPermission(permission);
            added.add(permission);
        }
    }

    public void unregister(PluginManager plugins) {
        for (Permission permission : added) plugins.removePermission(permission);
        added.clear();
    }
}
