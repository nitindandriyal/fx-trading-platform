package pub.lab.trading.common.model;

import org.agrona.collections.Int2ObjectHashMap;

public enum ClientTierLevel {
    BRONZE(1),
    SILVER(2),
    GOLD(3),
    PLATINUM(4);

    // Reverse lookup map
    private static final Int2ObjectHashMap<ClientTierLevel> ID_MAP = new Int2ObjectHashMap<>();

    static {
        for (ClientTierLevel level : values()) {
            ID_MAP.put(level.getId(), level);
        }
    }

    private final int id;

    ClientTierLevel(int id) {
        this.id = id;
    }

    public static ClientTierLevel fromId(int id) {
        return ID_MAP.get(id);
    }

    public int getId() {
        return id;
    }
}
