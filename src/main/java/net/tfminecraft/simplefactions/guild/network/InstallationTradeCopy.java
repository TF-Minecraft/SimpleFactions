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
		if (other == null) {
			return null;
		}
		boolean blocked = type.blocksInstallations() || other.blocksInstallations();
		return agreementLine(type.getInstallationAccess(), other.getInstallationAccess(), blocked);
	}

	/**
	 * The other side of an agreement. Null when a link id is configured but does not
	 * resolve and no loaded type links back, so the installation line must be omitted.
	 * The same type is used only when the agreement is mutual or has no link configured.
	 */
	private static RelationType counterpart(RelationType type) {
		if (type.hasLink()) {
			RelationType linked = type.getLink();
			if (linked != null) {
				return linked;
			}
		} else if (type.isMutual()) {
			return type;
		}
		String id = type.getId();
		for (RelationType candidate : RelationLoader.getTypes()) {
			if (candidate != type && candidate.hasLink() && id != null
					&& id.equalsIgnoreCase(candidate.getLinkString())) {
				return candidate;
			}
		}
		if (!type.hasLink()) {
			return type;
		}
		return null;
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
