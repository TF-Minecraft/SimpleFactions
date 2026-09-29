package net.tfminecraft.simplefactions.government.stability;

import java.util.ArrayList;
import java.util.List;

public final class StabilityReport {
	public double legitimacy;
	public double weakStateMalus;
	public int otherLevels;
	public double requiredLevels;
	public double economic;
	public double diversity;
	public double diversityLaw;
	public double overextension;
	public double stability;
	public boolean illegitimate;
	public boolean stateLeads;
	public boolean singleBody;
	public double supportShare;
	public String leadingGuild = "";
	public String largestOther = "";
	public int realmLevels;
	public int leadingLevels;
	public double leadingTradeShare;
	public StabilityStatus status = StabilityStatus.STABLE;
	public final List<String> lines = new ArrayList<>();
}
