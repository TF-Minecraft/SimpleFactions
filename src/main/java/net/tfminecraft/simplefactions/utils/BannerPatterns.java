package net.tfminecraft.simplefactions.utils;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;

/**
 * Banners stored as text: the base colour as {@code WHITE.BASE}, then one
 * {@code COLOUR.PATTERN} entry per layer. Factions and coalitions share this form.
 */
public final class BannerPatterns {
	private BannerPatterns() {}

	/** The patterns of a held banner, base colour first. */
	public static List<String> fromItem(ItemStack banner) {
		List<String> patterns = new ArrayList<>();
		BannerMeta b = (BannerMeta) banner.getItemMeta();
		patterns.add(banner.getType().toString().replace("_BANNER", ".BASE"));
		for(Pattern p : b.getPatterns()) {
			NamespacedKey key = io.papermc.paper.registry.RegistryAccess.registryAccess()
				.getRegistry(io.papermc.paper.registry.RegistryKey.BANNER_PATTERN).getKey(p.getPattern());
			if (key == null) continue;
			patterns.add(p.getColor().name() + "." + key.getKey().toUpperCase());
		}
		return patterns;
	}

	// Preserve the existing additional-tooltip component selection and legacy item text; hiding the whole tooltip is different.
	@SuppressWarnings({"deprecation"})
	public static ItemStack toItem(List<String> patterns) {
		ItemStack item = new ItemStack(
			Material.valueOf(patterns.get(0).split("\\.")[0].toUpperCase() + "_BANNER"),
			1
		);

		BannerMeta meta = (BannerMeta) item.getItemMeta();
		meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
		for (int i = 1; i < patterns.size(); i++) {
			String p = patterns.get(i);
			String[] split = p.split("\\.");

			if (split.length != 2) continue;

			String colourName = split[0].toUpperCase();
			String patternName = split[1].toLowerCase();

			DyeColor dye;
			try {
				dye = DyeColor.valueOf(colourName);
			} catch (IllegalArgumentException e) {
				Bukkit.getLogger().warning("Invalid dye color: " + colourName);
				continue;
			}

			PatternType patternType = null;

			// 1️⃣ Try vanilla (minecraft namespace)
			NamespacedKey vanillaKey = NamespacedKey.minecraft(patternName);
			patternType = io.papermc.paper.registry.RegistryAccess.registryAccess().getRegistry(io.papermc.paper.registry.RegistryKey.BANNER_PATTERN).get(vanillaKey);

			// 2️⃣ Try custom namespace (tfmc)
			if (patternType == null) {
				NamespacedKey customKey = new NamespacedKey("tfmc", patternName);
				patternType = io.papermc.paper.registry.RegistryAccess.registryAccess().getRegistry(io.papermc.paper.registry.RegistryKey.BANNER_PATTERN).get(customKey);
			}

			if (patternType == null) {
				Bukkit.getLogger().warning("Invalid banner pattern: " + patternName);
				continue;
			}

			meta.addPattern(new Pattern(dye, patternType));
		}
		item.setItemMeta(meta);
		
		return item;
	}
}
