package net.tfminecraft.simplefactions.managers.inventory;

/** Chat prompt for a new slot count, following the tax and dividend prompts. */
public class SlotChangePrompt {
    private final String guildId;
    private final String contractId;
    private int time;

    public SlotChangePrompt(String guildId, String contractId) {
        this.guildId = guildId;
        this.contractId = contractId;
    }

    public boolean tick() {
        time++;
        return time == 30;
    }

    public String getGuildId() {
        return guildId;
    }

    public String getContractId() {
        return contractId;
    }
}
