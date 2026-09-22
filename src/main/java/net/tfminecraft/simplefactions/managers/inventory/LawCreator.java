package net.tfminecraft.simplefactions.managers.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Bracket;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.FactionModifier;
import net.tfminecraft.simplefactions.utils.EconomicImpact;
import net.tfminecraft.simplefactions.utils.Formatter;
import net.tfminecraft.simplefactions.utils.LoreWriter;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.enums.Brackets;
import net.tfminecraft.simplefactions.enums.Region;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.enums.Scope;
import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.government.proposal.Proposal;
import net.tfminecraft.simplefactions.keys.Keys;
import net.tfminecraft.simplefactions.laws.CanHaveLaw;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawEffect;
import net.tfminecraft.simplefactions.laws.LawGroup;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public class LawCreator {

	private static final String CHECK = "✔";
	private static final String CROSS = "✖";

	private static final String GREEN = "#87d65c";
	private static final String RED   = "#d65c5c";
	private static final String GRAY  = "#6f776a";
	private static final String LIGHT_GRAY  = "#9cb68c";

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public ItemStack createLawGroupItem(Player p, Faction f, LawGroup group) {
        Law current = group.getCurrent();
		ItemStack i = current.getIcon();
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName(group.getName());

		List<String> lore = new ArrayList<>();

		// ---- Group description (top) ----
		if (group.hasDescription()) {
			lore.addAll(group.getDescription());
			lore.add("");
		}

		// ---- Current law highlight ----
		lore.add(StringFormatter.formatHex("#9cb68cCurrent Policy"));
		lore.add(StringFormatter.formatHex("  #87d65c" + current.getName()));
		lore.add("");

		// ---- Effects header ----
		if (current.hasEffects()) {
			for (Map.Entry<Scope, LawEffect> entry : current.getScopedEffects().entrySet()) {

				Scope scope = entry.getKey();
				LawEffect effect = entry.getValue();
				LoreWriter.writeEffect(scope, effect, lore);
			}
			lore.add("");
		}
		// ---- Law description ----
		if (current.hasDescription()) {
			lore.addAll(current.getDescription());
		}

		meta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, group.getId());
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public ItemStack createLawItem(Player p, Faction f, LawGroup group, Law law, boolean forProposal) {
		ItemStack i = law.getIcon();
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName(law.getName());

		boolean isCurrent = group.getCurrent().equals(law);

		List<String> lore = new ArrayList<>();

		// ---- Law description (top) ----
		if (law.hasDescription()) {
			lore.addAll(law.getDescription());
			lore.add("");
		}
		if(!isCurrent) {
			double cost = law.getCost()*law.getCompatibility(group.getCurrent().getId());
			double upkeep = law.getUpkeep();
			if(forProposal) {
				lore.add(StringFormatter.formatHex("#d4c9aeCost: §e"+Formatter.formatDouble(cost)+" Administrative Power"));
				if(upkeep > 0) {
					lore.add(StringFormatter.formatHex("#d4c9aeUpkeep: §e"+Formatter.formatDouble(upkeep)+" Administrative Power"));
				} else {
					lore.add(StringFormatter.formatHex("#d4c9aeUpkeep: §eNone"));
				}
				lore.add("");
			}
		}

		String unavailable = CanHaveLaw.blockReason(f, law);
		if (unavailable != null) {
			lore.add(unavailable);
			lore.add("");
		}

		// ---- Effects ----
		boolean affectsEconomy = false;

		if (law.hasEffects()) {
			for (Map.Entry<Scope, LawEffect> entry : law.getScopedEffects().entrySet()) {

				Scope scope = entry.getKey();
				LawEffect effect = entry.getValue();
				boolean factionScope = scope == Scope.FACTION;

				if (effect.affectsEconomy()) {
					affectsEconomy = true;
				}

				String indent = factionScope ? "  " : "    ";

				// Scope header
				if (!factionScope) {
					lore.add(StringFormatter.formatHex("  " + GRAY + scope.getDisplay() + ":"));
				}

				if(effect.affectsCouncilSize()) {
					lore.add(StringFormatter.formatHex(
						indent + LIGHT_GRAY + "Council Size§7: " + GREEN + effect.getCouncilSize()
					));
				}

				// ---- Rules ----
				if (effect.hasRules()) {
					for (Map.Entry<Rules, Boolean> ruleEntry : effect.getRules().entrySet()) {

						Rules rule = ruleEntry.getKey();
						boolean value = ruleEntry.getValue();

						String symbol = value ? GREEN + CHECK : RED + CROSS;

						lore.add(StringFormatter.formatHex(
								indent + symbol + " #d4c9ae" + rule.getDisplay()
						));
					}
				}

				// ---- Brackets ----
				if (effect.hasBrackets()) {
					for (Map.Entry<Brackets, Bracket> bracketEntry
							: effect.getBrackets().entrySet()) {

						Brackets type = bracketEntry.getKey();
						Bracket bracket = bracketEntry.getValue();

						lore.add(StringFormatter.formatHex(
								indent + LIGHT_GRAY + type.getDisplay() + " §7Range: "
						) + bracket.getString());
					}
				}

				// ---- Regiments ----
				if (effect.hasRegiments()) {
					for (Map.Entry<Regiment, Integer> regimentEntry
							: effect.getRegiments().entrySet()) {

						Regiment reg = regimentEntry.getKey();
						int amount = regimentEntry.getValue();

						lore.add(StringFormatter.formatHex(
								indent + LIGHT_GRAY + "Free " + reg.getName()
										+ LIGHT_GRAY + " Regiments§7: " + GREEN
						) + amount);
					}
				}

				// ---- Global modifiers ----
				if (effect.hasGlobalModifiers()) {
					for (FactionModifier mod : effect.getGlobalModifiers()) {
						lore.add(indent + mod.getString());
					}
				}

				// ---- Region modifiers ----
				if (effect.hasRegionModifiers()) {
					for (Map.Entry<Region, List<FactionModifier>> regionEntry
							: effect.getRegionModifiers().entrySet()) {

						Region region = regionEntry.getKey();

						lore.add(StringFormatter.formatHex(
								indent + LIGHT_GRAY + region.getDisplay() + ":"
						));

						for (FactionModifier mod : regionEntry.getValue()) {
							lore.add(indent + "  " + mod.getString());
						}
					}
				}

				lore.add("");
			}
		}

		// ---- Economic preview ----
		if (!isCurrent && affectsEconomy) {
			EconomicImpact.applyEconomicChange(lore, p, f, group, law);
		}
		Government gov = f.getGovernment();
		Proposal proposal = new Proposal("console", gov);
		proposal.setLawProposal(law);
		if(!gov.canBeProposed(proposal) && gov.canPropose(p)) {
			lore.add(StringFormatter.formatHex("#d65c5cThere is already a proposal in this law group."));
		}

		meta.setLore(lore);
		meta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, law.getId());
		meta.getPersistentDataContainer().set(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING, law.getGroup());
		i.setItemMeta(meta);
		return i;
	}
}
