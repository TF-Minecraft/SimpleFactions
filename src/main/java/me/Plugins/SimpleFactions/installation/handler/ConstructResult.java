package me.Plugins.SimpleFactions.installation.handler;

import me.Plugins.SimpleFactions.installation.Installation;

public class ConstructResult {
    private final boolean success;
    private final String message;
    private final Installation installation;

    public ConstructResult(boolean success, String message) {
        this(success, message, null);
    }

    public ConstructResult(boolean success, String message, Installation installation) {
        this.success = success;
        this.message = message;
        this.installation = installation;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Installation getInstallation() {
        return installation;
    }

    public static ConstructResult fail(String message) {
        return new ConstructResult(false, message);
    }

    public static ConstructResult ok(String message) {
        return new ConstructResult(true, message);
    }

    public static ConstructResult ok(String message, Installation installation) {
        return new ConstructResult(true, message, installation);
    }
}
