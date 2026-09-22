package me.Plugins.SimpleFactions.Objects;

import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class Bracket {
    private double min;
    private double max;

    public Bracket(double mn, double mx) {
        min = mn;
        max = mx;
    }

    public double getMin() { return min; }
    public double getMax() { return max; }

    public String getString() {
        return StringFormatter.formatHex("§7[#d6d165"+min+"§7-#d6d165"+max+"§7]");
    }
}
