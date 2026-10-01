package net.tfminecraft.simplefactions.installation;

public enum InstallationKind {
    FORT("fort", "fort"),
    PORT("port", "port"),
    AIRPORT("airport", "airport"),
    TRAIN_STATION("train_station", "train station");

    private final String commandName;
    private final String displayName;

    InstallationKind(String commandName, String displayName) {
        this.commandName = commandName;
        this.displayName = displayName;
    }

    public String getCommandName() {
        return commandName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static InstallationKind fromCommand(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim().toLowerCase();
        for (InstallationKind kind : values()) {
            if (kind.commandName.equals(key)) {
                return kind;
            }
        }
        return null;
    }
}
