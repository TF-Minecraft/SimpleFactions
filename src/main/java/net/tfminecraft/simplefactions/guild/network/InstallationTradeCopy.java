package net.tfminecraft.simplefactions.guild.network;

import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.loaders.RelationLoader;

public final class InstallationTradeCopy {
	private InstallationTradeCopy() {}

	/** Null when the line should be left off the item. */
	public static String agreementLine(double viewerAccess, double otherAccess, boolean blocksInstallations) {
		if (blocksInstallations) {
			return "Installations: closed both ways";
		}
		if (near(viewerAccess, 1.0) && near(otherAccess, 1.0)) {
			return "Installations: full use both ways";
		}
		if (near(viewerAccess, 1.0) && near(otherAccess, 0.5)) {
			return "Installations: we use theirs fully, they use ours at half";
		}
		if (near(viewerAccess, 0.5) && near(otherAccess, 1.0)) {
			return "Installations: we use theirs at half, they use ours fully";
		}
		if (near(viewerAccess, 0) && near(otherAccess, 0)) {
			return null;
		}
		return "Installations: we use theirs at " + percent(viewerAccess)
				+ "%, they use ours at " + percent(otherAccess) + "%";
	}

	public static String agreementLine(RelationType type) {
		if (type == null) {
			return null;
		}
		RelationType other = counterpart(type);
		boolean blocked = type.blocksInstallations() || other.blocksInstallations();
		return agreementLine(type.getInstallationAccess(), other.getInstallationAccess(), blocked);
	}

	private static RelationType counterpart(RelationType type) {
		if (type.hasLink()) {
			RelationType linked = type.getLink();
			return linked != null ? linked : type;
		}
		if (type.isMutual()) {
			return type;
		}
		String id = type.getId();
		for (RelationType candidate : RelationLoader.getTypes()) {
			if (candidate != type && candidate.hasLink() && id != null
					&& id.equalsIgnoreCase(candidate.getLinkString())) {
				return candidate;
			}
		}
		return type;
	}

	private static boolean near(double value, double target) {
		return Math.abs(value - target) < 1e-6;
	}

	private static String percent(double access) {
		double rounded = Math.round(access * 10000.0) / 100.0;
		if (rounded == (long) rounded) {
			return Long.toString((long) rounded);
		}
		return Double.toString(rounded);
	}
}
