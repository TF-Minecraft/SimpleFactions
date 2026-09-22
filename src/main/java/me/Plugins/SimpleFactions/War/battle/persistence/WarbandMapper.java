package me.Plugins.SimpleFactions.War.battle.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import me.Plugins.SimpleFactions.Database.WarbandData;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;

public final class WarbandMapper {
	private WarbandMapper() {
	}

	public static WarbandData toData(Warband warband) {
		if (warband == null) {
			return null;
		}
		WarbandData data = new WarbandData();
		data.id = warband.getId();
		data.name = warband.getName();
		data.leaderId = warband.getLeaderId() != null ? warband.getLeaderId().toString() : null;
		data.locked = warband.isLocked();
		data.faction = warband.isFaction();
		data.campaignSideId = warband.getCampaignSideId();
		for (UUID memberId : warband.getMemberIds()) {
			if (warband.isDummyMember(memberId)) {
				continue;
			}
			data.memberIds.add(memberId.toString());
		}
		for (UUID invitedId : warband.getInvitedIds()) {
			data.invitedIds.add(invitedId.toString());
		}
		return data;
	}

	public static Warband fromData(WarbandData data) {
		if (data == null || data.id == null || data.id.isBlank()) {
			return null;
		}
		return Warband.fromPersistence(
				data.id,
				data.name != null ? data.name : data.id,
				parseUuid(data.leaderId),
				parseUuidList(data.memberIds),
				parseUuidList(data.invitedIds),
				data.locked,
				data.faction,
				data.campaignSideId);
	}

	private static List<UUID> parseUuidList(List<String> ids) {
		List<UUID> parsed = new ArrayList<>();
		if (ids == null) {
			return parsed;
		}
		for (String id : ids) {
			UUID uuid = parseUuid(id);
			if (uuid != null) {
				parsed.add(uuid);
			}
		}
		return parsed;
	}

	private static UUID parseUuid(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
