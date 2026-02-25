package com.ominouschestlock.fabric;

enum PickType {
    RUSTY("rusty", "Rusty Lock Pick", 11001, 1.0),
    NORMAL("normal", "Lock Pick", 11002, 0.5),
    SILENCE("silence", "Silence Lock Pick", 11003, 0.05);

    final String id;
    final String displayName;
    final int modelData;
    final double breakChance;

    PickType(String id, String displayName, int modelData, double breakChance) {
        this.id = id;
        this.displayName = displayName;
        this.modelData = modelData;
        this.breakChance = breakChance;
    }

    static PickType fromId(String id) {
        for (PickType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }
}

