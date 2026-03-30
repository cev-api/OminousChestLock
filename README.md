# OminousChestLock

![Screenshot](https://i.imgur.com/lheQ9q9.png)

Paper plugin (1.21.0–1.21.11+) and Fabric server mod (1.21.11) that locks chests, double chests, barrels, and shulker boxes to a named Ominous or regular Trial Key, with an optional lockpicking minigame.

## How it works
- Rename an ominous trial key in an anvil (example: `secretkeyname`).
- Right-click a chest, barrel, or shulker box while holding that key to lock it.
- Only players holding a matching named key can open, break, or otherwise interact with the locked container.
- Locks are key-name based, not player based. Keys can be stolen or copied.
- One player can lock only one container per key name at a time.
- Different players can reuse the same key name for their own single container.
- If two players use the same key name, either matching key can open either container.
- Optional: normal trial keys can be enabled. The first successful open with a normal key arms the lock; the next successful open consumes the key and permanently unlocks the container.
- Paper only: optional paid recovery via Vault economy. If the player is both the lock owner and last user, has no matching key in hand or inventory, they get a clickable locksmith service fee prompt (or `/chestlockrecover confirm`) to unlock.

Vault sounds are used for success/failure feedback.

### Player feedback messages
- Opening a locked container with no key shows: `This chest is locked. You need the correct key.`
- Opening a locked container with the wrong key shows: `Wrong key.`
- Breaking a locked container without the correct key shows: `This chest is locked and cannot be broken.`
- Break-attempt chat warnings are rate-limited to avoid spam when repeatedly attempting to break.
- Successful lockpicking does not reveal the key name in chat (`Chest lock successfully picked.`).
- Optional verbose lock/unlock chat can be toggled with:
  - Config: `messages.verbose-lock-actions`
  - Command: `/chestlock verbosemessages <on|off>`
- Eligible paid recovery shows a clickable locksmith service fee prompt and `/chestlockrecover confirm` fallback.

## Lock picking

![Crafting](https://i.imgur.com/7i5efl9.png)

Three lock picks can be crafted and used to pick locked containers when you do not have the correct key. Attempts and lockouts are tracked per pick type, using the configured lockout scope.

- Rusty pick: `copper ingot + tripwire hook + stick` (default 88% break chance).
- Normal pick: `iron ingot + tripwire hook + breeze rod` (default 33% break chance).
- Silence pick: `normal pick + silence trim + echo shard` in a smithing table (default 5% break chance).

Lockout limit is a random roll between `lockpicks.limit.min` and `lockpicks.limit.max` (inclusive). The roll happens on the first pick attempt for that pick type and is stored on the lock until it is unlocked/destroyed.

Lockout scope can be set to `chest` (shared across all players) or `player` (each player has their own lockout/attempts) via `lockpicks.lockout-scope`.

On failed attempts, the player takes damage (defaults: 0.5/1/2 hearts for rusty/normal/silence). Rusty/normal lock out hard when over limit. Silence uses soft over-limit penalties and resets after `lockpicks.silence.penalty-reset-minutes`. The silence reset timer is anchored to the first over-limit attempt and does not extend with additional over-limit tries.

The attempt limit roll, lockout tracking, and fail-damage rules apply in both lockpick modes (legacy RNG and minigame). In minigame mode, each `TURN LOCK` press counts as an attempt, and failed turns apply the configured pick damage.

### Lockpick modes
- `lockpicks.minigame.enabled: false` -> legacy RNG mode (uses `open-chance` and `normal-key-chance`).
- `lockpicks.minigame.enabled: true` -> pin minigame mode (default mode).

### Minigame mode
- Opens a custom inventory UI (pins as columns, depths as rows).
- Depth range is capped at 5 (`1..5`) for generated lock secrets and config values.
- Left click: select depth for a pin. Right click: eliminate candidate.
- `TURN LOCK` consumes one full attempt each press (attempt/break/damage rules apply on failed turns).
- Success requires all selected depths to match the secret pinout.
- Turn-distance meter is rendered on the bottom row of the minigame inventory (full width).
- Bossbar feedback:
  - Normal fail: yellow turn distance, then red `Chest Locked!`, then reset.
  - Hard lockout fail: red `Locked Out` (no turn-distance animation).
  - Success: fills to full, flashes green `Chest Unlocked!`, then unlocks and opens the container.
- Optional visual feedback alternative (inside GUI):
  - Bottom row meter (full width) fills with turn distance, then flashes red/green on fail/success.
  - Inventory title behavior:
    - Idle title: `Locked Chest`
    - During feedback events: title changes to status text (`Turn Distance`, `Chest Locked!`, `Locked Out`, `Chest Unlocked!`) with matching color
    - Returns to `Locked Chest` after the event
- `lockpicks.minigame.bossbar.enabled` toggles bossbar on/off.
- `lockpicks.minigame.visual-feedback.enabled` toggles inventory meter feedback on/off.
- Session ownership: only one active picker per container.
- Session timeout: controlled by `lockpicks.minigame.session-timeout-seconds`.
- Pin regeneration on lockout/over-limit turn is configurable per lock type:
  - Trial default: stable pinout (`regenerate-on-attempt: false`)
  - Ominous default: reroll on lockout/over-limit (`regenerate-on-attempt: true`)

![Unlock](https://i.imgur.com/c86Anda.png)
![Almost](https://i.imgur.com/NpRPTgA.png)
![Fail](https://i.imgur.com/ffeTuho.png)


### Resource pack
Item model overrides and textures are provided in `src/main/resources/resourcepack`. Use this folder as a resource pack to see the lock pick textures in-hand and in inventories.

A prepackaged zip will be provided in the releases page along with the compiled binary of this plugin.

## Data
Lock data is stored in `plugins/OminousChestLock/data.yml`. Entries include the key name, creator, and last user. If enabled, it also stores lockpick attempt/lockout state and minigame pin data.

Unlocked chests are removed from the ```data.yml```.

Existing entries from older versions are automatically read and upgraded when saved.

Example:
```yaml
locked-chests:
  test:-8,104,40:
    key: key2
    world:
      name: test
      realm: OVERWORLD
      uuid: 01234567-89ab-cdef-0123-456789abcdef
    creator:
      name: Billy
      uuid: 01234567-89ab-cdef-0123-456789abcdef
    last-user:
      name: Tito
      uuid: 01234567-89ab-cdef-0123-456789abcdef
    pick:
      last:
        name: CevAPI
        uuid: 01234567-89ab-cdef-0123-456789abcdef
        type: silence
        timestamp: 1767877437911
      silence:
        limit: 2
        attempts: 3
        over-limit-attempts: 3
        penalty-timestamp: 1767877437911
    minigame:
      type: ominous
      pins: 6
      depths: 5
      secret: [1, 4, 2, 5, 1, 3]
      created: 1767877437911
      salt-version: 1
```

## Admin commands
Requires `chestlock.admin` (default: op).

- `/chestlock info` - Show key name, creator, last user, and minigame pin combo for the looked-at container.
- `/chestlock unlock` - Force unlock the looked-at container.
- `/chestlock keyinfo` - Show the locked container location and owner info for the key in hand.
- `/chestlock reload` - Reload lock data from disk.
- `/chestlock loglevel <0-3>` - Set log verbosity.
- `/chestlock normalkeys <on|off>` - Allow normal trial keys.
- `/chestlock lockpicks <on|off>` - Allow lock picking and crafting.
- `/chestlock verbosemessages <on|off>` - Toggle verbose lock/unlock chat messages.
- `/chestlock minigame <on|off>` - Toggle lockpick minigame mode.
- `/chestlock minigamebossbar <on|off>` - Toggle bossbar feedback for minigame.
- `/chestlock minigamevisual <on|off>` - Toggle inventory meter visual feedback for minigame.
- `/chestlock pinicon <material>` - Set selected pin icon material (example: `end_rod`).
- `/chestlock lockoutscope <chest|player>` - Set lockout scope.
- `/chestlock settings` - Show current loaded settings with color formatting.
- `/chestlock give <player> <rusty|normal|silence> [amount]` - Give lock picks to a player.
- `/chestlock help` - Show help.
- `/chestlockrecover confirm` - Confirm locksmith service fee unlock after prompt (Paper + Vault economy only).

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
- `messages.verbose-lock-actions`:
  - `false` = no optional verbose lock/unlock action chat
  - `true` = show lock/unlock action chat to the acting player only
- `recovery.paid-unlock.enabled` (Paper only):
  - `false` = locksmith service recovery disabled
  - `true` = allows eligible players to confirm paid recovery unlock
- `recovery.paid-unlock.cost` (Paper only):
  - locksmith service fee amount charged by Vault economy provider
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
- `lockpicks.minigame.*`:
  - `enabled` = use minigame instead of RNG pick chances
  - `trial.pins` / `trial.depths` = trial lock minigame size
  - `trial.assist-eliminate-one` = optional trial helper
  - `trial.regenerate-on-attempt` = reroll trial secret on lockout/over-limit turn
  - `ominous.pins` / `ominous.depths` = ominous lock minigame size
  - `ominous.regenerate-on-attempt` = reroll ominous secret on lockout/over-limit turn
  - `session-timeout-seconds` = auto-timeout for active sessions
  - `bossbar.enabled` = enable/disable bossbar feedback
  - `bossbar.animate-ticks` / `bossbar.peak-hold-ticks` / `bossbar.snapback-delay-ticks` = turn bar timing
  - `visual-feedback.enabled` = enable/disable inventory meter feedback
  - `visual-feedback.rename-inventory-title` = show status text in inventory title
  - `ui.pin-icon` = material used for selected pin cells in the minigame grid
  - `sounds.click-per-correct-pin` = click sequence feedback mode
  - `security.require-holding-pick` = must keep matching pick in hand
  - `salt` + `salt-version` = seed inputs for first-time secret generation

Example:
```yaml
logging:
  # 0 = nothing, 1 = everything, 2 = failed + destruction/automation, 3 = destruction/automation only
  level: 1
keys:
  # false = ominous trial keys only, true = ominous + normal trial keys
  allow-normal: true
messages:
  # true = send the acting player lock/unlock status chat messages
  verbose-lock-actions: false
recovery:
  paid-unlock:
    # requires Vault + an economy provider (e.g., EssentialsX Economy via Vault)
    enabled: false
    # locksmith service fee to recover a lock when eligible
    cost: 5000.0
lockpicks:
  # true = allow lock pick crafting and usage
  enabled: true
  # random attempt limit before lockout
  limit:
    min: 1
    max: 20
  # lockout scope: "chest" = shared for all players, "player" = per-player lockout tracking
  lockout-scope: chest
  rusty:
    open-chance: 0.05
    normal-key-chance: 0.1
    break-chance: 0.88
    damage: 1.0
  normal:
    open-chance: 0.1
    normal-key-chance: 0.2
    break-chance: 0.33
    damage: 2.0
  silence:
    open-chance: 0.5
    break-chance: 0.05
    damage: 4.0
    # time until over-limit penalty resets
    penalty-reset-minutes: 60
  minigame:
    enabled: true
    trial:
      pins: 4
      depths: 4
      assist-eliminate-one: true
      regenerate-on-attempt: false
    ominous:
      pins: 6
      depths: 5
      regenerate-on-attempt: true
    session-timeout-seconds: 90
    bossbar:
      enabled: true
      animate-ticks: 12
      peak-hold-ticks: 20
      snapback-delay-ticks: 20
    visual-feedback:
      enabled: true
      rename-inventory-title: true
    ui:
      pin-icon: end_rod
    sounds:
      click-per-correct-pin: true
    security:
      require-holding-pick: true
    salt: "change-me"
    salt-version: 1
```

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
[OminousChestLock] PICK_FAIL actor=CevAPI usedKey=none lockKey=secretkeeey creator=Bingo lastUser=Bingo location=test:-40,64,-82 (OVERWORLD) detail=pick=silence mode=minigame combo=1-4-5-?-?-? actual=1-4-5-5-6-4 limit=9/16 untilLimit=7
```

## Build
```shell
./gradlew clean build
```
Paper jar: `paper/build/libs/OminousChestLock-1.x.0_Paper.jar`

Fabric jar: `fabric/build/libs/OminousChestLock-1.x.0_Fabric.jar`

## Permissions
- `chestlock.admin` - Allows use of admin commands (default: op).
