package me.Plugins.SimpleFactions.Database;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.SerializedName;

public class MovementData {
    public String leader;
    public String id;
    public Double organization;
    public List<CauseData> causes = new ArrayList<>();
    public PoolData supporters = new PoolData();
    public String phase;
    public boolean frozen;
    
    @SerializedName("foreign backers")
    public List<String> foreignBackers = new ArrayList<>();
}
