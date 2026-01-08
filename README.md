# OminousChestLock

![Screenshot](https://i.imgur.com/lheQ9q9.png)

Paper plugin for Minecraft 1.21.0-1.21.11+ that locks chests, double chests, barrels, and shulker boxes to a named ominous trial key and optionally allows players to pick them!

## How it works
- Rename an ominous trial key in an anvil (example: `secretkeyname`).
- Right-click a chest, barrel, or shulker box while holding that key to lock it.
- Only players holding a matching named key can open, break, or otherwise interact with the locked container.
- Locks are key-name based, not player based. Keys can be stolen or copied.
- One key name can only lock one container at a time until that container is unlocked/destroyed.
- Optional: normal trial keys can be enabled. The first successful open with a normal key arms the lock; the next successful open consumes the key and permanently unlocks the container.

Vault sounds are used for success/failure feedback.

## Lock picking

![Crafting](https://i.imgur.com/7i5efl9.png)

Three lock picks can be crafted and used to pick locked containers when you do not have the correct key. Pick attempts are tracked per lock and pick tier.

- Rusty pick: `copper ingot + tripwire hook + stick`, 100% break chance, 5% open chance (10% on normal-key locks). Over-limit attempts always fail.
- Normal pick: `iron ingot + tripwire hook + breeze rod`, 50% break chance, 10% open chance (20% on normal-key locks). Over-limit attempts always fail.
- Silence pick: `normal pick + silence trim + echo shard` in a smithing table, 2% break chance, 50% base open chance. After the limit, each attempt halves the chance; the penalty resets after 1 hour.

On failed attempts, the player takes damage (0.5/1/2 hearts for rusty/normal/silence). Rusty/normal picks play chest-locked sounds, then vault deactivate on lockout and vault hit on further attempts. Silence picks only play a sound on the lockout (vault deactivate), then vault hit until the 1-hour reset.

### Resource pack
Item model overrides and textures are provided in `src/main/resources/resourcepack`. Use this folder as a resource pack to see the lock pick textures in-hand and in inventories.

A prepackaged zip will be provided in the releases page along with the compiled binary of this plugin.

## Data
Lock data is stored in `plugins/OminousChestLock/data.yml`. Entries include the key name, creator, and last user.
Existing entries from older versions are automatically read and upgraded when saved.

Example:
```yaml
locked-chests:
  world:10,64,20:
    key: secretkeyname
    world:
      name: world
      realm: OVERWORLD
      uuid: 01234567-89ab-cdef-0123-456789abcdef
    creator:
      name: Bingo
      uuid: 01234567-89ab-cdef-0123-456789abcdef
    last-user:
      name: Steve
      uuid: fedcba98-7654-3210-cake-ba9876543210
```

## Admin commands
Requires `chestlock.admin` (default: op).

- `/chestlock info` - Show key name, creator, and last user for the looked-at container.
- `/chestlock unlock` - Force unlock the looked-at container.
- `/chestlock keyinfo` - Show the locked container location and owner info for the key in hand.
- `/chestlock reload` - Reload lock data from disk.
- `/chestlock loglevel <0-3>` - Set log verbosity.
- `/chestlock normalkeys <on|off>` - Allow normal trial keys.
- `/chestlock lockpicks <on|off>` - Allow lock picking and crafting.
- `/chestlock lockoutscope <chest|player>` - Set lockout scope.
- `/chestlock give <player> <rusty|normal|silence> [amount]` - Give lock picks to a player.
- `/chestlock help` - Show help.

## Config
Config in `plugins/OminousChestLock/config.yml`:
- `logging.level`:
  - `0` = nothing
  - `1` = everything (includes lock pick successes and failures)
  - `2` = failed unlock + destruction/automation attempts (includes lock pick failures)
  - `3` = destruction/automation attempts only
- `keys.allow-normal`:
  - `false` = ominous keys only
  - `true` = ominous + normal trial keys
- `lockpicks.enabled`:
  - `false` = lock pick crafting/usage disabled
  - `true` = lock pick crafting/usage enabled
- `lockpicks.limit.min` / `lockpicks.limit.max`:
  - Random attempt limit (inclusive) before lockout per pick type
  - The limit is rolled once on the first pick attempt for that pick type and then stored on that locked container until it is unlocked/destroyed
- `lockpicks.lockout-scope`:
  - `chest` = pick lockout is shared by all players on that container
  - `player` = pick lockout is tracked separately per player
- `lockpicks.rusty.*`, `lockpicks.normal.*`, `lockpicks.silence.*`:
  - `open-chance` = base success chance (0.0–1.0)
  - `normal-key-chance` = success chance for normal-key locks (rusty/normal only)
  - `break-chance` = break chance per attempt (0.0–1.0)
  - `damage` = hearts of damage per failed attempt
  - `penalty-reset-minutes` = silence pick over-limit reset time (minutes)

### Example log entries
```
[OminousChestLock] EXPLOSION_DENY actor=TNT:CevAPI usedKey=none lockKey=secretkeyname creator=Bingo lastUser=George location=test:-9,104,40 (OVERWORLD)
[OminousChestLock] INVENTORY_MOVE_DENY actor=HOPPER:CevAPI usedKey=none lockKey=secretkeyname creator=Bingo lastUser=Bingo location=test:-21,104,22 (OVERWORLD) detail=source=CHEST dest=HOPPER
[OminousChestLock] BREAK_DENY actor=CevAPI usedKey=none lockKey=secretkeyname creator=Bingo lastUser=George location=test:-9,104,40 (OVERWORLD)
[OminousChestLock] INTERACT_DENY actor=CevAPI usedKey=cevapcici lockKey=secretkeyname creator=Bingo lastUser=Bingo location=test:-52,104,66 (OVERWORLD)
[OminousChestLock] OPEN_ALLOWED actor=CevAPI usedKey=secretkeeey lockKey=secretkeeey creator=CevAPI lastUser=CevAPI location=test:-6,104,37 (OVERWORLD)
[OminousChestLock] BREAK_ALLOWED actor=CevAPI usedKey=secretkeeey lockKey=secretkeeey creator=CevAPI lastUser=CevAPI location=test:-6,104,37 (OVERWORLD)
[OminousChestLock] PICK_FAIL actor=CevAPI usedKey=none lockKey=secretkeeey creator=Bingo lastUser=Bingo location=test:-6,104,37 (OVERWORLD) detail=pick=rusty
[OminousChestLock] PICK_SUCCESS actor=CevAPI usedKey=none lockKey=secretkeeey creator=Bingo lastUser=Bingo location=test:-6,104,37 (OVERWORLD) detail=pick=normal
```

## Build
```shell
./gradlew clean build
```
The jar will be in `build/libs/OminousChestLock-1.0.0.jar`.

## Permissions
- `chestlock.admin` - Allows use of admin commands (default: op).
