# AnythingInABucket

Catch any mob in a bucket and release it anywhere later, exactly as it was.

[Download on Modrinth](https://modrinth.com/mod/anythinginabucket) · Also available as a
[mod](https://github.com/InertiaMC/AnythingInABucket-Mod) for Fabric, NeoForge and Forge.

## Features

- Catch any mob with a Capture Bucket and release it by right-clicking a block.
- The whole mob is kept: name, health, variant, owner, saddle, armour, chest, villager trades and more.
- Filled buckets show the mob's details in their lore.
- Copied buckets do nothing, so mobs can't be duplicated.
- Pets can only be caught by their owner, and protected areas (WorldGuard, claims) are respected.
- Per-mob permissions and dispenser releases.

## Requirements

- Minecraft 1.21 to 26.3
- Bukkit, Spigot, Paper, Purpur or Folia
- Java 21+

## Commands

| Command | Permission |
|---|---|
| `/bucket help` | `aiab.command.help` |
| `/bucket give <player> [amount]` | `aiab.command.give` |
| `/bucket admin info` | `aiab.command.admin.info` |
| `/bucket admin reload` | `aiab.command.admin.reload` |

`/aiab` works too.

## Permissions

| Permission | Default |
|---|---|
| `aiab.catch` | everyone |
| `aiab.catch.<mob>` (e.g. `aiab.catch.villager`) | everyone |
| `aiab.release` | everyone |
| `aiab.bypass.owner` (catch other players' pets) | op |
| `aiab.admin` (everything) | op |

## Configuration

Everything is in `config.yml` and `messages.yml`, with comments. You can change which bucket catches (the Capture
Bucket, a normal bucket while sneaking, or both), the blacklist, the cooldown, disabled worlds, the recipe and all
messages. The database is SQLite by default, or MySQL in `database.yml`.

## Resource pack

Optional. Each filled bucket shows the mob inside it instead of a spawn egg. Get it on
[Modrinth](https://modrinth.com/resourcepack/anythinginabucket-resource-pack), or set `resource-pack.send: true` to
offer it to players when they join. The `resourcepack/` folder in this repo is the pack.

## Building

```bash
mvn package
```

Needs JDK 21. It uses InertiaMC's `inertia-core` library, which isn't public yet, so building needs access to it;
ready-built jars are on [Modrinth](https://modrinth.com/mod/anythinginabucket).
