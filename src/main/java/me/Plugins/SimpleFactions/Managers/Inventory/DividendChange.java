package me.Plugins.SimpleFactions.Managers.Inventory;

import me.Plugins.SimpleFactions.Guild.Guild;

public class DividendChange {
    private Guild guild;
    private int time;

    public DividendChange(Guild guild) {
        this.guild = guild;
        time = 0;
    }

    public boolean tick() {
        time++;
        return time == 30;
    }

    public Guild getGuild() {
        return guild;
    }
}
