# OminousChestLock Storage Backend Update (Paper)

This document summarizes the Paper-side storage update that adds SQLite and MySQL support while preserving the existing YAML behavior.

## What changed

- Added a storage abstraction (`LockRepository`) so persistence is no longer hardcoded.
- Kept YAML storage fully supported and default (`storage.type: yaml`).
- Added new SQL backends:
  - `SqliteLockRepository`
  - `MysqlLockRepository`
- Added runtime backend selection through `config.yml`.
- Added explicit migration command:
  - `/chestlock migrate yaml <sqlite|mysql>`
- Added startup/shutdown lifecycle handling for SQL initialization and close.

## Backward compatibility

- Existing servers continue to work with no changes.
- If `storage.type` is not changed, behavior remains YAML-based as before.
- Existing `data.yml` format is still readable and writable.
- Lock mechanics, gameplay behavior, permissions, and existing command semantics are unchanged.

## Storage design

- `LockRepository`: common persistence contract for all backends.
- `YamlLockRepository`: wraps prior YAML model semantics.
- `SqlLockRepository`: shared SQL load/save/upsert logic.
- `SqliteLockRepository`: SQLite schema + upsert syntax.
- `MysqlLockRepository`: MySQL schema + HikariCP pooling.
- `YamlStorageCodec`: central codec preserving YAML field semantics and backward compatibility parsing behavior.

## Config

Added section in `paper/src/main/resources/config.yml`:

```yml
storage:
  type: yaml
  sqlite:
    file: plugins/OminousChestLock/data.db
  mysql:
    host: localhost
    port: 3306
    database: ominouschestlock
    username: root
    password: change_me
    parameters: "?useSSL=false&autoReconnect=true"
    pool:
      maximumPoolSize: 10
      minimumIdle: 2
      connectionTimeoutMs: 30000
```

## SQL schema summary

Tables:

- `ocl_locks`
  - Primary key: `location_key`
  - Stores all main lock fields (owner/user, pick metadata, lockout counters, world/location metadata).
- `ocl_lock_pick_states`
  - Composite primary key: `(location_key, player_uuid)`
  - Stores per-player lockout state.
- `ocl_lock_minigame`
  - Primary key: `location_key`
  - Stores minigame payload for a lock.

Indexes:

- `ocl_locks(key_name)`
- `ocl_locks(world_name, x, y, z)`

## Migration behavior

Command:

- `/chestlock migrate yaml sqlite`
- `/chestlock migrate yaml mysql`

Rules:

- Source is currently `yaml` only.
- Target must match currently configured `storage.type` (after `/chestlock reload`).
- Migration is additive/upsert-based and does not delete `data.yml`.
- Command returns processed and verified counts.
- Migration is blocked while minigame sessions are active.

## Dependencies added (Paper module)

- `org.xerial:sqlite-jdbc:3.46.1.3`
- `mysql:mysql-connector-java:8.0.33`
- `com.zaxxer:HikariCP:5.1.0`

## Operational notes

- On startup, selected backend initializes and SQL schemas are created automatically.
- On shutdown/reload, storage is flushed and closed cleanly.
- If storage config is invalid or backend init fails, a clear error is logged and plugin disables to avoid unsafe operation.

## Files touched

- `paper/src/main/java/com/ominouschestlock/paper/ChestLockPlugin.java`
- `paper/src/main/java/com/ominouschestlock/paper/LockRepository.java`
- `paper/src/main/java/com/ominouschestlock/paper/LockRepositoryException.java`
- `paper/src/main/java/com/ominouschestlock/paper/StorageConfig.java`
- `paper/src/main/java/com/ominouschestlock/paper/YamlStorageCodec.java`
- `paper/src/main/java/com/ominouschestlock/paper/YamlLockRepository.java`
- `paper/src/main/java/com/ominouschestlock/paper/SqlLockRepository.java`
- `paper/src/main/java/com/ominouschestlock/paper/SqliteLockRepository.java`
- `paper/src/main/java/com/ominouschestlock/paper/MysqlLockRepository.java`
- `paper/src/main/resources/config.yml`
- `paper/src/main/resources/plugin.yml`
- `paper/build.gradle.kts`

