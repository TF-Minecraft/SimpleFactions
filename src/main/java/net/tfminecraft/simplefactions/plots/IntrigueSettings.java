package net.tfminecraft.simplefactions.plots;

/** A character's plot approach and chosen defender. */
public final class IntrigueSettings {
    public PlotApproach approach = PlotApproach.NEUTRAL;
    public long approachChangedAt;
    public DefenderChoice defender = DefenderChoice.SELF;
    public PlotCharacter helper;
    public long defenderChangedAt;
    /** Characters who investigate plots this character has learned of. */
    public java.util.List<PlotCharacter> investigators = new java.util.ArrayList<>();

    PlotApproach approach() { return approach == null ? PlotApproach.NEUTRAL : approach; }
    DefenderChoice defender() { return defender == null || defender == DefenderChoice.CHARACTER && helper == null ? DefenderChoice.SELF : defender; }
}
