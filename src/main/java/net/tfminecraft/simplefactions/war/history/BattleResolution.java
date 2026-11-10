package net.tfminecraft.simplefactions.war.history;

/** How a campaign battle was settled, as the website's battle ledger labels it. */
public enum BattleResolution {
	/** A live battle ended. */
	FOUGHT("fought"),
	AUTORESOLVE("autoresolve"),
	/** The side on the offensive could not attack. */
	FORFEIT("forfeit"),
	/** The attacker had no berthed navy for a naval battle. */
	NAVAL_DEFAULT("naval_default"),
	/** {@code /war admin schedule <id> winbattle}. */
	STAFF("staff");

	private final String json;

	BattleResolution(String json) {
		this.json = json;
	}

	public String toJson() {
		return json;
	}
}
