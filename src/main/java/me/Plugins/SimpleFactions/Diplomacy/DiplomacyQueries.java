package me.Plugins.SimpleFactions.Diplomacy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;

public final class DiplomacyQueries {

	public static final int SEPARATOR_ROWS = 2;
	public static final int SLOTS_PER_ROW = 9;
	public static final int SEPARATOR_COUNT = SEPARATOR_ROWS * SLOTS_PER_ROW;

	private DiplomacyQueries() {}

	public static boolean hasOfficialRelation(Faction origin, Faction target) {
		if (origin == null || target == null) {
			return false;
		}
		if (origin.getId() != null && origin.getId().equalsIgnoreCase(target.getId())) {
			return false;
		}
		Relation relation = origin.getRelation(target.getId());
		if (relation != null && relation.getType() != null && !relation.getType().isDefault()) {
			return true;
		}
		DiplomacyHandler handler = origin.getDiplomacyHandler();
		if (handler == null) {
			return false;
		}
		return handler.hasTradeRelation(target.getId()) || handler.hasTreatyRelation(target.getId());
	}

	public static List<Faction> officialPartners(Faction origin) {
		List<Faction> partners = new ArrayList<>();
		if (origin == null || FactionManager.factions == null) {
			return partners;
		}
		for (Faction other : FactionManager.factions) {
			if (hasOfficialRelation(origin, other)) {
				partners.add(other);
			}
		}
		partners.sort(nameOrder());
		return partners;
	}

	public static List<Faction> otherFactions(Faction origin) {
		List<Faction> others = new ArrayList<>();
		if (origin == null || FactionManager.factions == null) {
			return others;
		}
		for (Faction other : FactionManager.factions) {
			if (other == null || other.getId() == null) {
				continue;
			}
			if (other.getId().equalsIgnoreCase(origin.getId())) {
				continue;
			}
			if (hasOfficialRelation(origin, other)) {
				continue;
			}
			others.add(other);
		}
		others.sort(nameOrder());
		return others;
	}

	public static List<DiplomacyListEntry> ownDirectory(Faction origin) {
		List<DiplomacyListEntry> entries = new ArrayList<>();
		for (Faction partner : officialPartners(origin)) {
			entries.add(DiplomacyListEntry.faction(partner));
		}
		for (int i = 0; i < SEPARATOR_COUNT; i++) {
			entries.add(DiplomacyListEntry.separator());
		}
		for (Faction other : otherFactions(origin)) {
			entries.add(DiplomacyListEntry.faction(other));
		}
		return entries;
	}

	public static List<DiplomacyListEntry> foreignList(Faction viewed) {
		List<DiplomacyListEntry> entries = new ArrayList<>();
		for (Faction partner : officialPartners(viewed)) {
			entries.add(DiplomacyListEntry.faction(partner));
		}
		return entries;
	}

	private static Comparator<Faction> nameOrder() {
		return Comparator.comparing(
				(Faction f) -> f.getName() == null ? "" : f.getName(),
				String.CASE_INSENSITIVE_ORDER);
	}

	public static final class DiplomacyListEntry {
		public enum Kind {
			FACTION,
			SEPARATOR
		}

		private final Kind kind;
		private final Faction faction;

		private DiplomacyListEntry(Kind kind, Faction faction) {
			this.kind = kind;
			this.faction = faction;
		}

		public static DiplomacyListEntry faction(Faction faction) {
			return new DiplomacyListEntry(Kind.FACTION, faction);
		}

		public static DiplomacyListEntry separator() {
			return new DiplomacyListEntry(Kind.SEPARATOR, null);
		}

		public Kind getKind() {
			return kind;
		}

		public Faction getFaction() {
			return faction;
		}

		public boolean isSeparator() {
			return kind == Kind.SEPARATOR;
		}
	}
}
