package net.tfminecraft.simplefactions.espionage;

/** The allied courts a Spymaster can open their faction's information to. */
public enum SharingPartner {
    OVERLORD("your overlord"), VASSALS("your vassals");

    private final String label;
    SharingPartner(String label) { this.label = label; }
    public String label() { return label; }
}
