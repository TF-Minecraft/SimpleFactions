package me.Plugins.SimpleFactions.installation;

public enum InstallationKind {
    FORT("fort"),
    PORT("port"),
    AIRPORT("airport");

    private final String commandName;

    InstallationKind(String commandName) {
        this.commandName = commandName;
    }

    public String getCommandName() {
        return commandName;
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
