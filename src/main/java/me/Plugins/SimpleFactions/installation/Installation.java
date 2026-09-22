package me.Plugins.SimpleFactions.installation;

import me.Plugins.SimpleFactions.Database.InstallationData;

public class Installation {
    private final String id;
    private final String name;
    private final InstallationKind kind;
    private final int province;
    private final int centerX;
    private final int centerZ;
    private final long completedAt;

    public Installation(
            String id,
            String name,
            InstallationKind kind,
            int province,
            int centerX,
            int centerZ,
            long completedAt) {
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.province = province;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.completedAt = completedAt;
    }

    public Installation(InstallationData data) {
        if (data.id == null || data.name == null || data.kind == null || data.province == null) {
            throw new IllegalArgumentException("Installation data missing required fields");
        }
        InstallationKind parsed = InstallationKind.fromCommand(data.kind);
        if (parsed == null) {
            throw new IllegalArgumentException("Unknown installation kind: " + data.kind);
        }
        this.id = data.id;
        this.name = data.name;
        this.kind = parsed;
        this.province = data.province;
        this.centerX = data.centerX != null ? data.centerX : 0;
        this.centerZ = data.centerZ != null ? data.centerZ : 0;
        this.completedAt = data.completedAt != null ? data.completedAt : 0L;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public InstallationKind getKind() {
        return kind;
    }

    public int getProvince() {
        return province;
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterZ() {
        return centerZ;
    }

    public long getCompletedAt() {
        return completedAt;
    }

    public InstallationData toData() {
        InstallationData data = new InstallationData();
        data.id = id;
        data.name = name;
        data.kind = kind.getCommandName();
        data.province = province;
        data.centerX = centerX;
        data.centerZ = centerZ;
        data.completedAt = completedAt;
        return data;
    }
}
