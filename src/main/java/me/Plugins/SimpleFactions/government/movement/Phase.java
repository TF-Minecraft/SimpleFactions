package me.Plugins.SimpleFactions.government.movement;

public enum Phase {
    GATHERING("#65c97cGathering", 20, 0),
    PRESSURING("#75a7d9Pressuring", 40, 1),
    AGITATED("#edbd4cAgitated", 75, 2),
    REBELLIOUS("#e33320Rebellious", 100, 3);

    private final String displayName;
    private final int maxOrganization;
    private final int index;

    Phase(String displayName, int maxOrganization, int index) {
        this.displayName = displayName;
        this.maxOrganization = maxOrganization;
        this.index = index;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMaxOrganization() {
        return maxOrganization;
    }

    public int getIndex() {
        return index;
    }
}
