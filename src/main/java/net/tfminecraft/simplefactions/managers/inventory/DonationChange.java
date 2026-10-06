package net.tfminecraft.simplefactions.managers.inventory;

import net.tfminecraft.simplefactions.guild.Guild;

public class DonationChange {
    private final Guild guild;
    private int time;

    public DonationChange(Guild guild) {
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
