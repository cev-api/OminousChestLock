package com.ominouschestlock.fabric;

import java.util.Locale;

enum LockoutScope {
    CHEST,
    PLAYER;

    static LockoutScope fromConfig(String value) {
        if (value == null) {
            return CHEST;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "player" -> PLAYER;
            default -> CHEST;
        };
    }
}

