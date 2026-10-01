package net.tfminecraft.simplefactions.government.stability;

import java.util.ArrayList;
import java.util.List;

public final class StabilityReport {
	public double legitimacy;
	public double weakStateMalus;
	public int otherLevels;
	public double requiredLevels;
	public double overextension;
	public double stability;
	public boolean illegitimate;
	public boolean singleBody;
	public double supportShare;
	public int realmLevels;
	public int leadingLevels;
	public StabilityStatus status = StabilityStatus.STABLE;
	public final List<String> lines = new ArrayList<>();
}
