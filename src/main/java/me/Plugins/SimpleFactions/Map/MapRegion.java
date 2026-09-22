package me.Plugins.SimpleFactions.Map;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A named gameplay zone made of provinces. Not a de jure title and not
 * {@link me.Plugins.SimpleFactions.enums.Region} (law scope).
 */
public final class MapRegion {
	private final String id;
	private final String name;
	private final Set<Integer> provinces;

	public MapRegion(String id, String name, Set<Integer> provinces) {
		this.id = Objects.requireNonNull(id, "id");
		this.name = name != null && !name.isBlank() ? name : id;
		this.provinces = Collections.unmodifiableSet(new LinkedHashSet<>(provinces));
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Set<Integer> getProvinces() {
		return provinces;
	}

	public boolean containsProvince(int provinceId) {
		return provinces.contains(provinceId);
	}
}
