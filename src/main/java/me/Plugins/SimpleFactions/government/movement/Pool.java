package me.Plugins.SimpleFactions.government.movement;

import java.util.ArrayList;
import java.util.List;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class Pool {
    private List<String> citizens = new ArrayList<>();
    private List<Guild> guilds = new ArrayList<>();
    private List<Faction> factions = new ArrayList<>();

    public Pool() {}

    public Pool(List<String> citizens, List<Guild> guilds, List<Faction> factions) {
        this.citizens = citizens;
        this.guilds = guilds;
        this.factions = factions;
    }

    public void addCitizen(String citizen) {
        citizens.add(citizen);
    }

    public void addGuild(Guild guild) {
        guilds.add(guild);
    }

    public void addFaction(Faction faction) {
        factions.add(faction);
    }

    public void removeCitizen(String citizen) {
        citizens.remove(citizen);
    }

    public void removeGuild(Guild guild) {
        guilds.remove(guild);
    }

    public void removeFaction(Faction faction) {
        factions.remove(faction);
    }

    public List<String> getCitizens() {
        return citizens;
    }

    public List<Guild> getGuilds() {
        return guilds;
    }

    public List<Faction> getFactions() {
        return factions;
    }

    public List<String> getAllMembers() {
        List<String> allMembers = new ArrayList<>(citizens);
        for (Guild guild : guilds) {
            allMembers.addAll(guild.getMembers());
        }
        for (Faction faction : factions) {
            allMembers.addAll(faction.getMembers());
        }
        return allMembers;
    }

    public List<String> getFormattedList() {
        List<String> formattedList = new ArrayList<>();
        for (String citizen : citizens) {
            formattedList.add(StringFormatter.formatHex(citizen + " #77d1a3(Citizen)"));
        }
        for (Guild guild : guilds) {
            formattedList.add(StringFormatter.formatHex(guild.getName() + " #d1b83b(Guild)"));
        }
        for (Faction faction : factions) {
            formattedList.add(StringFormatter.formatHex(faction.getName() + " #77a6d1(Faction)"));
        }
        return formattedList;
    }

    public void remove(String pool, String member) {
        switch(pool) {
            case "citizens":
                citizens.remove(member);
                break;
            case "guilds":
                guilds.removeIf(g -> g.getId().equals(member));
                break;
            case "factions":
                factions.removeIf(f -> f.getId().equals(member));
                break;
        }
    }
}
