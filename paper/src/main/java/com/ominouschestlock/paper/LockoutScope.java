package com.ominouschestlock.paper;

enum LockoutScope {
    CHEST,
    PLAYER;

    static LockoutScope fromConfig(String value) {
        if (value == null) {
            return CHEST;
        }
        return switch (value.toLowerCase()) {
            case "player" -> PLAYER;
            default -> CHEST;
        };
    }
}

