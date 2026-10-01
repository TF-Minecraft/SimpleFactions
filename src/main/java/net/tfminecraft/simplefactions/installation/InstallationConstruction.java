package net.tfminecraft.simplefactions.installation;

import net.tfminecraft.simplefactions.database.InstallationConstructionData;
import net.tfminecraft.simplefactions.loaders.InstallationConfigLoader;

public class InstallationConstruction {
    private final String id;
    private final String name;
    private final InstallationKind kind;
    private final int province;
    private final int centerX;
    private final int centerZ;
    private int timeLeft;
    private final long startedAt;
    private final boolean upgrade;

    public InstallationConstruction(
            String id,
            String name,
            InstallationKind kind,
            int province,
            int centerX,
            int centerZ,
            int timeLeft,
            long startedAt) {
        this(id, name, kind, province, centerX, centerZ, timeLeft, startedAt, false);
    }

    public InstallationConstruction(
            String id,
            String name,
            InstallationKind kind,
            int province,
            int centerX,
            int centerZ,
            int timeLeft,
            long startedAt,
            boolean upgrade) {
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.province = province;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.timeLeft = timeLeft;
        this.startedAt = startedAt;
        this.upgrade = upgrade;
    }

    public InstallationConstruction(
            String id,
            String name,
            InstallationKind kind,
            int province,
            int centerX,
            int centerZ) {
        this(
                id,
                name,
                kind,
                province,
                centerX,
                centerZ,
                InstallationConfigLoader.getConstructionTimeSeconds(kind, 1),
                System.currentTimeMillis());
    }

    public InstallationConstruction(InstallationConstructionData data) {
        if (data.id == null || data.name == null || data.kind == null || data.province == null) {
            throw new IllegalArgumentException("Installation construction data missing required fields");
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
        this.timeLeft = data.timeLeft != null ? data.timeLeft : 0;
        this.startedAt = data.startedAt != null ? data.startedAt : System.currentTimeMillis();
        this.upgrade = Boolean.TRUE.equals(data.upgrade);
    }

    public void tick() {
        if (timeLeft == 0) {
            return;
        }
        timeLeft--;
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

    public int getTimeLeft() {
        return timeLeft;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public boolean isUpgrade() {
        return upgrade;
    }

    public InstallationConstructionData toData() {
        InstallationConstructionData data = new InstallationConstructionData();
        data.id = id;
        data.name = name;
        data.kind = kind.getCommandName();
        data.province = province;
        data.centerX = centerX;
        data.centerZ = centerZ;
        data.timeLeft = timeLeft;
        data.startedAt = startedAt;
        data.upgrade = upgrade;
        return data;
    }
}
