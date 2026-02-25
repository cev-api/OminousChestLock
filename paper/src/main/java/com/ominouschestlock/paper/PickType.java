package com.ominouschestlock.paper;

enum PickType {
    RUSTY("rusty", "Rusty Lock Pick", 11001),
    NORMAL("normal", "Lock Pick", 11002),
    SILENCE("silence", "Silence Lock Pick", 11003);

    final String id;
    final String displayName;
    final int modelData;

    PickType(String id, String displayName, int modelData) {
        this.id = id;
        this.displayName = displayName;
        this.modelData = modelData;
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

