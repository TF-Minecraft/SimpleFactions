package me.Plugins.SimpleFactions.Managers.Inventory;

import java.util.Locale;
import java.util.Optional;

/**
 * Encodes queue-cancel targets on item PDC and confirm buttons as
 * {@code type:ownerId:indexOrDetail}.
 */
public final class QueueCancelPayload {

	public enum Type {
		MILITARY,
		GUILD_UPGRADE,
		INSTALLATION,
		COMPANY_SLOT,
		COMPANY_UPGRADE;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		static Optional<Type> fromId(String raw) {
			if (raw == null || raw.isBlank()) {
				return Optional.empty();
			}
			for (Type type : values()) {
				if (type.id().equalsIgnoreCase(raw)) {
					return Optional.of(type);
				}
			}
			return Optional.empty();
		}
	}

	public record Parsed(Type type, String ownerId, String detail, int index) {}

	private QueueCancelPayload() {
	}

	public static String military(String factionId, int index) {
		return encode(Type.MILITARY, factionId, String.valueOf(index));
	}

	public static String guildUpgrade(String guildId, int index) {
		return encode(Type.GUILD_UPGRADE, guildId, String.valueOf(index));
	}

	public static String installation(String factionId, String constructionId) {
		return encode(Type.INSTALLATION, factionId, constructionId);
	}

	public static String companySlot(String guildId, int index) {
		return encode(Type.COMPANY_SLOT, guildId, String.valueOf(index));
	}

	public static String companyUpgrade(String guildId, int index) {
		return encode(Type.COMPANY_UPGRADE, guildId, String.valueOf(index));
	}

	public static String encode(Type type, String ownerId, String detail) {
		return type.id() + ":" + ownerId + ":" + detail;
	}

	public static Optional<Parsed> parse(String payload) {
		if (payload == null || payload.isBlank()) {
			return Optional.empty();
		}
		String[] parts = payload.split(":", 3);
		if (parts.length != 3) {
			return Optional.empty();
		}
		Optional<Type> type = Type.fromId(parts[0]);
		if (type.isEmpty()) {
			return Optional.empty();
		}
		String ownerId = parts[1];
		String detail = parts[2];
		if (ownerId.isBlank() || detail.isBlank()) {
			return Optional.empty();
		}
		int index = 0;
		if (type.get() != Type.INSTALLATION) {
			try {
				index = Integer.parseInt(detail);
			} catch (NumberFormatException ex) {
				return Optional.empty();
			}
		}
		return Optional.of(new Parsed(type.get(), ownerId, detail, index));
	}
}
