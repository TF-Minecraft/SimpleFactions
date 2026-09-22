package me.Plugins.SimpleFactions.Map.presence;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import me.Plugins.SimpleFactions.Loaders.TitleLoader;
import me.Plugins.SimpleFactions.Tiers.Title;

public final class TitlePresenceResolver {

	public static final List<String> TIERS = List.of("county", "duchy", "kingdom", "empire");

	private TitlePresenceResolver() {
	}

	public static Map<String, ResolvedTitle> resolve(int provinceId) {
		if (provinceId == ProvincePresenceService.UNKNOWN_PROVINCE) {
			return Map.of();
		}
		Title current = TitleLoader.getByProvince(provinceId);
		if (current == null) {
			return Map.of();
		}
		Map<String, ResolvedTitle> found = new LinkedHashMap<>();
		int guard = 0;
		while (current != null && guard++ < 16) {
			String tierId = normalizeTier(current);
			if (tierId != null && TIERS.contains(tierId) && !found.containsKey(tierId)) {
				String name = current.getName() != null ? current.getName() : current.getId();
				found.put(tierId, new ResolvedTitle(current.getId(), name));
			}
			current = TitleLoader.getByTitle(current);
		}
		return Collections.unmodifiableMap(found);
	}

	private static String normalizeTier(Title title) {
		if (title == null || title.getTier() == null || title.getTier().getId() == null) {
			return null;
		}
		return title.getTier().getId().toLowerCase(Locale.ROOT);
	}

	public record ResolvedTitle(String id, String name) {
	}
}
