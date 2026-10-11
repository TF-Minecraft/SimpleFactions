package net.tfminecraft.simplefactions.plots;

/** A recruited conspirator. The plot master assigns the role and a collaborator's task. */
public final class PlotMember {
    public PlotCharacter character;
    public PlotRole role = PlotRole.UNASSIGNED;
    public CollaboratorTask task;

    public PlotMember() {}

    public PlotMember(PlotCharacter character) { this.character = character; }

    public String roleLabel() {
        return role == PlotRole.COLLABORATOR && task != null ? role.label() + " (" + task.label() + ")" : role.label();
    }
}
