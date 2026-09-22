package me.Plugins.SimpleFactions.government.session;

public enum Vote {
    YAY("Yay"),
    NAY("Nay"),
    ABSTAIN("Abstain");
    
    private String display;
    
    Vote(String display) {
        this.display = display;
    }
    
    public String getDisplay() {
        return display;
    }
    
    public static Vote fromString(String input) {
        if (input == null) return null;
        for (Vote v : values()) {
            if (v.name().equalsIgnoreCase(input)) {
                return v;
            }
        }
        return null;
    }
}
