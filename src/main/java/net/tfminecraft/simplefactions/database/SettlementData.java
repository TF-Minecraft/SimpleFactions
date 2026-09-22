package net.tfminecraft.simplefactions.database;

import java.util.ArrayList;
import java.util.List;

public class SettlementData {
    public String id;
    public String name;
    public Integer centerProvince;
    public Integer centerX;
    public Integer centerZ;
    public List<Number> provinces = new ArrayList<>();
}
