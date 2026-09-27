package net.tfminecraft.simplefactions.tiers.admin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.tiers.Title;

/**
 * Staff edits to the title definitions in Input/&lt;tier&gt;.json. Title ids never change here:
 * faction saves, wars and the web map all reference them.
 */
public final class TitleAdminService {
	public static final String PREFIX = "§a[SimpleFactions]§c ";
	public static final int MAX_NAME_LENGTH = 48;

	private TitleAdminService() {
	}

	/** A web map region to regenerate: the title's tier and its rgb key. */
	public record MapKey(String tier, String rgb) {
	}

	public record Result(boolean ok, List<String> lines, Set<Title> changed, Set<MapKey> regenerate) {
		static Result error(String message) {
			return new Result(false, List.of(PREFIX + message), Set.of(), Set.of());
		}
	}

	private static final class Change {
		private final List<String> lines = new ArrayList<>();
		private final Set<Title> changed = new LinkedHashSet<>();
		private final Set<MapKey> regenerate = new LinkedHashSet<>();

		void touch(Title title) {
			changed.add(title);
			// Parents cover the moved provinces too. The set guards against a hand-edited cycle.
			Set<Title> seen = new LinkedHashSet<>();
			for (Title t = title; t != null && seen.add(t); t = TitleLoader.getByTitle(t)) {
				regenerate.add(key(t));
			}
		}

		Result done() {
			return new Result(true, lines, changed, regenerate);
		}
	}

	static MapKey key(Title title) {
		return new MapKey(title.getTier().getId(), title.getRgb());
	}

	public static Result rename(String id, String rawName) {
		Title title = TitleLoader.getById(id);
		if (title == null) return Result.error("No title with the id " + id);
		String name = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
		if (name.isEmpty()) return Result.error("The name cannot be empty");
		if (name.contains("§")) return Result.error("Names cannot contain § colour codes");
		if (name.length() > MAX_NAME_LENGTH) return Result.error("Names can be at most " + MAX_NAME_LENGTH + " characters");
		String old = title.getName();
		if (name.equals(old)) return Result.error(title.getId() + " is already called " + name);
		title.setName(name);
		Change change = new Change();
		change.touch(title);
		change.lines.add("§aRenamed §7" + title.getId() + " §afrom §f" + old + " §ato §f" + name);
		return change.done();
	}

	public static Result setColour(String id, String rawRgb) {
		Title title = TitleLoader.getById(id);
		if (title == null) return Result.error("No title with the id " + id);
		String rgb = parseRgb(rawRgb);
		if (rgb == null) return Result.error("Colour must be R,G,B with each value 0-255, e.g. 200,168,80");
		String old = title.getRgb();
		if (rgb.equals(old)) return Result.error(title.getId() + " already uses " + rgb);
		for (Title other : TitleLoader.getByTier(title.getTier())) {
			if (other != title && rgb.equals(other.getRgb())) {
				return Result.error(other.getId() + " already uses " + rgb + "; colours must be unique within a tier");
			}
		}
		Change change = new Change();
		// The web map keys regions by rgb, so the old colour's overlay must be cleared too.
		if (old != null) change.regenerate.add(new MapKey(title.getTier().getId(), old));
		title.setRgb(rgb);
		change.touch(title);
		change.lines.add("§aSet the colour of §f" + title.getName() + " §7(" + title.getId() + ") §afrom §7" + old + " §ato §f" + rgb);
		return change.done();
	}

	public static Result setComplete(String id, String rawValue) {
		Title title = TitleLoader.getById(id);
		if (title == null) return Result.error("No title with the id " + id);
		if (!"true".equalsIgnoreCase(rawValue) && !"false".equalsIgnoreCase(rawValue)) {
			return Result.error("Value must be true or false");
		}
		boolean value = Boolean.parseBoolean(rawValue.toLowerCase());
		if (title.isTitleComplete() == value) return Result.error(title.getId() + " title-complete is already " + value);
		title.setTitleComplete(value);
		Change change = new Change();
		change.changed.add(title);
		change.lines.add("§aSet title-complete of §f" + title.getName() + " §7(" + title.getId() + ") §ato §f" + value
				+ (value ? " §7(forming needs every de jure part)" : " §7(forming uses the de jure percentage)"));
		return change.done();
	}

	public static Result addProvinces(String id, List<String> rawProvinces, IntPredicate provinceExists) {
		Title title = TitleLoader.getById(id);
		if (title == null) return Result.error("No title with the id " + id);
		if (title.isComposite()) return Result.error(title.getId() + " is made of titles, not provinces. Use addtitle");
		List<Integer> provinces = parseProvinces(rawProvinces, provinceExists);
		if (provinces == null) return Result.error(invalidProvinces(rawProvinces, provinceExists));

		// Validate the whole batch first so a rejected province leaves nothing half-applied.
		List<Integer> toAdd = new ArrayList<>();
		for (int province : provinces) {
			if (title.getProvinces().contains(province) || toAdd.contains(province)) continue;
			Title previous = TitleLoader.getByProvince(province);
			if (previous != null) {
				long left = previous.getProvinces().stream().filter(p -> !provinces.contains(p)).count();
				if (left == 0 && previous.getTitles().isEmpty()) {
					return Result.error("Moving province " + province + " would leave " + previous.getId() + " with no provinces");
				}
			}
			toAdd.add(province);
		}
		if (toAdd.isEmpty()) return Result.error(title.getId() + " already has " + (provinces.size() == 1 ? "that province" : "those provinces"));

		Change change = new Change();
		for (int province : toAdd) {
			Title previous = TitleLoader.getByProvince(province);
			if (previous != null) {
				previous.getProvinces().remove(Integer.valueOf(province));
				change.touch(previous);
				change.lines.add("§eMoved province §f" + province + " §efrom §f" + previous.getName() + " §7(" + previous.getId() + ")");
			} else {
				change.lines.add("§aAdded province §f" + province);
			}
			title.getProvinces().add(province);
		}
		change.touch(title);
		change.lines.add("§a" + title.getName() + " §7(" + title.getId() + ") §anow has provinces §f" + title.getProvinces());
		return change.done();
	}

	public static Result removeProvinces(String id, List<String> rawProvinces) {
		Title title = TitleLoader.getById(id);
		if (title == null) return Result.error("No title with the id " + id);
		List<Integer> provinces = parseProvinces(rawProvinces, p -> true);
		if (provinces == null) return Result.error(invalidProvinces(rawProvinces, p -> true));
		for (int province : provinces) {
			if (!title.getProvinces().contains(province)) {
				return Result.error("Province " + province + " is not part of " + title.getId());
			}
		}
		long left = title.getProvinces().stream().filter(p -> !provinces.contains(p)).count();
		if (left == 0 && title.getTitles().isEmpty()) {
			return Result.error("That would leave " + title.getId() + " with no provinces");
		}
		for (int province : provinces) {
			title.getProvinces().remove(Integer.valueOf(province));
		}
		Change change = new Change();
		change.touch(title);
		change.lines.add("§aRemoved provinces §f" + provinces + " §afrom §f" + title.getName() + " §7(" + title.getId() + ")"
				+ " §7- they are now untitled");
		return change.done();
	}

	public static Result addTitle(String id, String childId) {
		Title parent = TitleLoader.getById(id);
		if (parent == null) return Result.error("No title with the id " + id);
		Title child = TitleLoader.getById(childId);
		if (child == null) return Result.error("No title with the id " + childId);
		if (!parent.isComposite()) return Result.error(parent.getId() + " is made of provinces, not titles. Use addprovince");
		if (child.getTier().getTier() != parent.getTier().getTier() - 1) {
			return Result.error(child.getId() + " is a " + child.getTier().getId() + "; " + parent.getId()
					+ " can only contain titles one tier below it");
		}
		if (containsId(parent.getTitles(), child.getId())) return Result.error(parent.getId() + " already contains " + child.getId());
		Title previous = null;
		for (Title t : TitleLoader.getTitles()) {
			if (t != parent && containsId(t.getTitles(), child.getId())) {
				previous = t;
				break;
			}
		}
		if (previous != null && previous.getTitles().size() <= 1 && previous.getProvinces().isEmpty()) {
			return Result.error("Moving " + child.getId() + " would leave " + previous.getId() + " empty");
		}
		Change change = new Change();
		if (previous != null) {
			change.touch(previous);
			removeId(previous.getTitles(), child.getId());
			change.lines.add("§eMoved §f" + child.getName() + " §7(" + child.getId() + ") §efrom §f" + previous.getName()
					+ " §7(" + previous.getId() + ")");
		}
		parent.getTitles().add(child.getId());
		change.touch(parent);
		change.lines.add("§a" + parent.getName() + " §7(" + parent.getId() + ") §anow contains §f" + parent.getTitles());
		return change.done();
	}

	public static Result removeTitle(String id, String childId) {
		Title parent = TitleLoader.getById(id);
		if (parent == null) return Result.error("No title with the id " + id);
		if (!containsId(parent.getTitles(), childId)) return Result.error(childId + " is not part of " + parent.getId());
		if (parent.getTitles().size() <= 1 && parent.getProvinces().isEmpty()) {
			return Result.error("That would leave " + parent.getId() + " empty");
		}
		Change change = new Change();
		change.touch(parent);
		removeId(parent.getTitles(), childId);
		change.lines.add("§aRemoved §f" + childId + " §afrom §f" + parent.getName() + " §7(" + parent.getId() + ")");
		return change.done();
	}

	/** Normalises "R,G,B" (spaces allowed) to "r,g,b", or returns null. */
	static String parseRgb(String raw) {
		if (raw == null) return null;
		String[] parts = raw.split(",");
		if (parts.length != 3) return null;
		StringBuilder out = new StringBuilder();
		for (String part : parts) {
			int value;
			try {
				value = Integer.parseInt(part.trim());
			} catch (NumberFormatException e) {
				return null;
			}
			if (value < 0 || value > 255) return null;
			if (out.length() > 0) out.append(',');
			out.append(value);
		}
		return out.toString();
	}

	/** Accepts space- or comma-separated ids; null when any id is malformed or unknown. */
	static List<Integer> parseProvinces(List<String> raw, IntPredicate provinceExists) {
		List<Integer> out = new ArrayList<>();
		for (String arg : raw) {
			for (String part : arg.split(",")) {
				if (part.isBlank()) continue;
				int province;
				try {
					province = Integer.parseInt(part.trim());
				} catch (NumberFormatException e) {
					return null;
				}
				if (!provinceExists.test(province)) return null;
				if (!out.contains(province)) out.add(province);
			}
		}
		return out.isEmpty() ? null : out;
	}

	private static String invalidProvinces(List<String> raw, IntPredicate provinceExists) {
		for (String arg : raw) {
			for (String part : arg.split(",")) {
				if (part.isBlank()) continue;
				try {
					int province = Integer.parseInt(part.trim());
					if (!provinceExists.test(province)) return "No province with the id " + province;
				} catch (NumberFormatException e) {
					return "Province ids must be numbers: " + part.trim();
				}
			}
		}
		return "Give at least one province id";
	}

	private static boolean containsId(List<String> ids, String id) {
		return ids.stream().anyMatch(s -> s.equalsIgnoreCase(id));
	}

	private static void removeId(List<String> ids, String id) {
		ids.removeIf(s -> s.equalsIgnoreCase(id));
	}
}
