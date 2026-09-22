package me.Plugins.SimpleFactions.government.election;

public enum Candidate {
    LEADER("Leader"),
    COUNCIL("Council");

    private final String name;

    public String getName() {
        return name;
    }

    Candidate(String name) {
        this.name = name;
    }
}