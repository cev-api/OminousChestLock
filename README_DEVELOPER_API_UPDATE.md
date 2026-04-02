# OminousChestLock Developer Event/API Update (Paper)

This update adds a developer-facing integration layer for Paper without changing core lock/pick gameplay logic.

## Goals of this update

- Add clean custom events for lock/pick lifecycle hook-ins.
- Add a minimal public API service for integrations.
- Keep current lock behavior, pick behavior, minigame flow, commands, and permissions intact.
- Keep changes additive and backward compatible for existing servers.

## New API service

Service interface:

- `com.ominouschestlock.paper.api.OminousChestLockApi`

Registered via Bukkit `ServicesManager` during plugin enable.

Main methods:

- `isLocked(Location)`
- `getLockSnapshot(Location)`
- `createLock(Location, keyName, creator, normalKey)`
- `removeLock(Location, actor)`
- `unlock(Location, actor)`
- `getRegisteredPickTypes()`
- `getRegisteredLockTypes()`
- `registerLockType(id)` (groundwork)
- `registerPickType(id)` (groundwork)

Snapshot payload:

- `LockSnapshot` provides immutable lock metadata and locations for safe consumption by external plugins.

## New custom events

Package:

- `com.ominouschestlock.paper.api.event`

Added events:

- `LockCreateEvent` (`Cancellable`)
- `LockRemoveEvent` (`Cancellable`)
- `LockUnlockEvent` (`Cancellable`)
- `LockPickAttemptEvent` (`Cancellable`)
- `LockPickSuccessEvent` (informational)
- `LockPickFailEvent` (informational)
- `LockoutAppliedEvent` (informational)

Supporting enums:

- `LockActionCause`
- `LockPickMode` (`DIRECT`, `MINIGAME`)

## Event timing/order

### Lock create

1. `LockCreateEvent` fires before lock state is created.
2. If cancelled, lock creation does not proceed.

### Unlock/remove

1. `LockUnlockEvent` fires before unlock mutation.
2. `LockRemoveEvent` fires before lock entry removal.
3. If either is cancelled, unlock/removal is aborted.

### Direct pick flow (non-minigame)

1. `LockPickAttemptEvent` fires before pick resolution.
2. If cancelled, attempt stops.
3. On success: `LockPickSuccessEvent`, then unlock path.
4. On failure: `LockPickFailEvent`.
5. When lockout/over-limit applies: `LockoutAppliedEvent`.

### Minigame pick flow

1. `LockPickAttemptEvent` fires on turn attempt before evaluation.
2. If cancelled, turn stops.
3. On success: `LockPickSuccessEvent`, then unlock path.
4. On failure: `LockPickFailEvent`.
5. When lockout/over-limit state applies: `LockoutAppliedEvent`.

## Compatibility notes

- This is additive: no existing gameplay constants/chances/mechanics were intentionally changed.
- Existing lock and pick code paths remain in place; events wrap those paths.
- New cancellable events only affect behavior when external plugins actively cancel them.
- Service/API avoids exposing mutable internal lock state directly.

## Example integration ideas

- Block picks in protected regions by cancelling `LockPickAttemptEvent`.
- React to breach success with alerts on `LockPickSuccessEvent`.
- Trigger defender cooldown logic via `LockoutAppliedEvent`.
- Gate or deny lock creation/removal via `LockCreateEvent` / `LockRemoveEvent`.

## Files added

- `paper/src/main/java/com/ominouschestlock/paper/api/OminousChestLockApi.java`
- `paper/src/main/java/com/ominouschestlock/paper/api/LockSnapshot.java`
- `paper/src/main/java/com/ominouschestlock/paper/api/event/LockActionCause.java`
- `paper/src/main/java/com/ominouschestlock/paper/api/event/LockPickMode.java`
- `paper/src/main/java/com/ominouschestlock/paper/api/event/LockCreateEvent.java`
- `paper/src/main/java/com/ominouschestlock/paper/api/event/LockRemoveEvent.java`
- `paper/src/main/java/com/ominouschestlock/paper/api/event/LockUnlockEvent.java`
- `paper/src/main/java/com/ominouschestlock/paper/api/event/LockPickAttemptEvent.java`
- `paper/src/main/java/com/ominouschestlock/paper/api/event/LockPickSuccessEvent.java`
- `paper/src/main/java/com/ominouschestlock/paper/api/event/LockPickFailEvent.java`
- `paper/src/main/java/com/ominouschestlock/paper/api/event/LockoutAppliedEvent.java`

## Files modified

- `paper/src/main/java/com/ominouschestlock/paper/ChestLockPlugin.java`

## Future follow-up recommendations

- Expand custom lock/pick registration from metadata groundwork into behavior plugins (strategy registry).
- Add optional event docs/Javadocs with nullability annotations for third-party plugin authors.
- Add integration tests with a mock plugin listener validating event order and cancellation outcomes.

