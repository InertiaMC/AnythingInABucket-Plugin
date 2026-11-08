package net.inertiamc.anythinginabucket.managers;

import net.inertiamc.anythinginabucket.models.StoredMob;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.UnsafeValues;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntitySnapshot;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Base64;

/**
 * Saves a whole mob as text and restores it. On Paper this uses Paper's entity serialization (with the data version, so
 * the game upgrades a mob caught before an update), reached by reflection on the public API interfaces. Elsewhere it
 * uses Bukkit's entity snapshot, available from 1.21. Both keep everything the game saves about the mob except its
 * position and UUID.
 *
 * <p>A {@link StoredMob#PAPER} bucket cannot be released on Spigot.
 */
public final class MobCodec {

    /** What {@link #save} wrote. */
    public record Saved(String format, String data, int dataVersion) {}

    private static final Method SERIALIZE = find(UnsafeValues.class, "serializeEntity", Entity.class);
    private static final Method DESERIALIZE = find(UnsafeValues.class, "deserializeEntity", byte[].class, World.class, boolean.class);
    private static final Method SPAWN_AT = find(Entity.class, "spawnAt", Location.class);

    private MobCodec() {}

    /** Whether this server supports the Paper format. */
    public static boolean paper() {
        return SERIALIZE != null && DESERIALIZE != null && SPAWN_AT != null;
    }

    /** Call on the entity's own thread, with no passengers and not riding anything. */
    @SuppressWarnings("deprecation")
    public static Saved save(Entity entity) throws ReflectiveOperationException {
        UnsafeValues unsafe = Bukkit.getUnsafe();
        int version = unsafe.getDataVersion();
        if (paper()) {
            byte[] bytes = (byte[]) invoke(SERIALIZE, unsafe, entity);
            return new Saved(StoredMob.PAPER, Base64.getEncoder().encodeToString(bytes), version);
        }
        EntitySnapshot snapshot = entity.createSnapshot();
        if (snapshot == null) throw new IllegalStateException(entity.getType() + " has no snapshot");
        return new Saved(StoredMob.SNAPSHOT, snapshot.getAsString(), version);
    }

    /** Spawns the mob at {@code at}. Call on the thread that owns {@code at}. */
    @SuppressWarnings("deprecation")
    public static Entity spawn(StoredMob mob, Location at) throws ReflectiveOperationException {
        if (StoredMob.PAPER.equals(mob.format())) {
            if (!paper()) throw new IllegalStateException("this bucket was filled on Paper and needs Paper to release");
            byte[] bytes = Base64.getDecoder().decode(mob.data());
            Entity entity = (Entity) invoke(DESERIALIZE, Bukkit.getUnsafe(), bytes, at.getWorld(), false);
            Object spawned = invoke(SPAWN_AT, entity, at);
            if (Boolean.FALSE.equals(spawned)) throw new IllegalStateException("the server refused to add the mob");
            // The saved data keeps the old rotation, which overrides the location's, so set it explicitly.
            entity.setRotation(at.getYaw(), at.getPitch());
            return entity;
        }
        Entity entity = Bukkit.getEntityFactory().createEntitySnapshot(mob.data()).createEntity(at);
        entity.setRotation(at.getYaw(), at.getPitch());
        return entity;
    }

    /**
     * Finds the method whose first parameters are {@code types}. Trailing parameters added in newer versions (such as
     * Paper's serialization flags) are passed empty by {@link #invoke}.
     */
    private static Method find(Class<?> owner, String name, Class<?>... types) {
        Method best = null;
        for (Method method : owner.getMethods()) {
            if (!method.getName().equals(name)) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length < types.length) continue;
            boolean match = true;
            for (int i = 0; i < types.length && match; i++) match = params[i] == types[i];
            for (int i = types.length; i < params.length && match; i++) {
                match = params[i].isArray() || params[i] == boolean.class;
            }
            if (match && (best == null || params.length < best.getParameterCount())) best = method;
        }
        return best;
    }

    private static Object invoke(Method method, Object target, Object... given) throws ReflectiveOperationException {
        Class<?>[] params = method.getParameterTypes();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            if (i < given.length) args[i] = given[i];
            else if (params[i].isArray()) args[i] = Array.newInstance(params[i].getComponentType(), 0);
            else args[i] = false;
        }
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw e;
        }
    }
}
