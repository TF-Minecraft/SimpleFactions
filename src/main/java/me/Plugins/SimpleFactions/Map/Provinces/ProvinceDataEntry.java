package me.Plugins.SimpleFactions.Map.Provinces;

import me.Plugins.SimpleFactions.Guild.Guild;

public class ProvinceDataEntry {
    private final Guild guild;
    private double trade;
    private double production;
    private int distance;

    public ProvinceDataEntry(Guild guild) {
        this.guild = guild;
        this.trade = 0;
        this.production = 0;
        this.distance = 0;
    }

    public ProvinceDataEntry(Guild guild, double trade, double production) {
        this.guild = guild;
        this.trade = trade;
        this.production = production;
        this.distance = 0;
    }

    public ProvinceDataEntry(Guild guild, double trade, double production, int distance) {
        this.guild = guild;
        this.trade = trade;
        this.production = production;
        this.distance = distance;
    }

    public ProvinceDataEntry copy() {
        ProvinceDataEntry e = new ProvinceDataEntry(guild);
        e.setTrade(trade);
        e.setProduction(production);
        e.setDistance(distance);
        return e;
    }

    public boolean isConsidered() {
        return trade > 0 || production > 0;
    }

    public Guild getGuild() { return guild; }
    public double getTrade() { return trade; }
    public double getProduction() { return production; }
    public int getDistance() { return distance; }

    public void setTrade(double t) {
        trade = t;
    }

    public void setProduction(double p) {
        production = p;
    }

    public void setDistance(int distance) {
        this.distance = distance;
    }
}
