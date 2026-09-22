package me.Plugins.SimpleFactions.settlement;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import me.Plugins.SimpleFactions.Database.SettlementData;

public class Settlement {
    private final String id;
    private final String name;
    private final int centerProvince;
    private final int centerX;
    private final int centerZ;
    private final Set<Integer> provinces = new LinkedHashSet<>();

    public Settlement(String id, String name, int centerProvince, int centerX, int centerZ) {
        this.id = id;
        this.name = name;
        this.centerProvince = centerProvince;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.provinces.add(centerProvince);
    }

    public Settlement(SettlementData data) {
        if (data.id == null || data.name == null || data.centerProvince == null) {
            throw new IllegalArgumentException("Settlement data missing required fields");
        }
        this.id = data.id;
        this.name = data.name;
        this.centerProvince = data.centerProvince;
        this.centerX = data.centerX != null ? data.centerX : 0;
        this.centerZ = data.centerZ != null ? data.centerZ : 0;
        if (data.provinces != null) {
            for (Number p : data.provinces) {
                provinces.add(p.intValue());
            }
        }
        if (!provinces.contains(centerProvince)) {
            provinces.add(centerProvince);
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getCenterProvince() {
        return centerProvince;
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterZ() {
        return centerZ;
    }

    public Set<Integer> getProvinces() {
        return provinces;
    }

    public boolean contains(int provinceId) {
        return provinces.contains(provinceId);
    }

    public boolean isCenter(int provinceId) {
        return centerProvince == provinceId;
    }

    public void addProvince(int provinceId) {
        if (provinceId != centerProvince) {
            throw new IllegalArgumentException(
                    "Settlements are single-province; cannot add province " + provinceId);
        }
        provinces.add(centerProvince);
    }

    public void removeProvince(int provinceId) {
        provinces.remove(provinceId);
    }

    public void normalizeToCenterOnly() {
        provinces.clear();
        provinces.add(centerProvince);
    }

    public SettlementData toData() {
        SettlementData data = new SettlementData();
        data.id = id;
        data.name = name;
        data.centerProvince = centerProvince;
        data.centerX = centerX;
        data.centerZ = centerZ;
        data.provinces = new ArrayList<>();
        for (int p : provinces) {
            data.provinces.add(p);
        }
        return data;
    }
}
