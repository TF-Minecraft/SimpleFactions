package net.tfminecraft.simplefactions.government.stability;

import java.util.ArrayList;
import java.util.List;

/** Plain inputs for {@link StabilityMath}. No faction objects. */
public final class StabilityFacts {
	public String government = "autocracy";
	public boolean electedLeadership;
	public String economy = "decentralized";
	public String borders = "closed_borders";
	public int provinces;
	public boolean bankrupt;
	public boolean plutocracySeated;
	public double temporary;
	public final List<Body> guilds = new ArrayList<>();
	public final List<Body> vassals = new ArrayList<>();

	public static final class Body {
		public String name = "";
		public boolean realm;
		public int members;
		public int branchLevels;
		public double tradePower;
		public String stance = "SUPPORT";
		public boolean favoured;
		public boolean repressed;
	}
}
