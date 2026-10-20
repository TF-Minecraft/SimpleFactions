package net.tfminecraft.simplefactions.objects.handler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.tfminecraft.simplefactions.war.civilwar.wartime.CivilWarBorderLock;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.tiers.Title;
import net.tfminecraft.simplefactions.enums.Terrain;

public class ProvinceHandler {
    private Faction f;
    private List<Integer> provinces = new ArrayList<>();

	private int capital = -1;

    public ProvinceHandler(Faction f, int capital, List<Integer> provinces) {
		this.f = f;
        this.capital = capital;
		for(int i : provinces) {
			if(TitleManager.getByProvince(i) != null) continue;
			this.provinces.add(i);
		}
    }

    public ProvinceHandler(Faction f) {
        this.f = f;
        this.capital = -1;
        this.provinces = new ArrayList<>();
    }

    public boolean hasCapital() {
		return capital != -1;
	}

	public int getCapital() {
		return capital;
	}

	public void setCapital(int i, boolean force) {
		setCapital(i, force, true);
	}

	public void setCapital(int i, boolean force, boolean notifySettlement) {
		if (i != -1 && !provinces.contains(i) && !force) {
			return;
		}
		int old = capital;
		if (old == i) {
			return;
		}
		capital = i;
		SimpleFactions.getInstance().getProvinceManager().recalculateForSingleGuild(f.getOrCreateMainGuild(), true);
		if (notifySettlement && old != -1) {
			f.getSettlementHandler().onGuildDepartedCapital(old);
		}
	}

    public boolean hasProvince(int i) {
		return provinces.contains(i);
	}
	
	public void addProvince(int i) {
		if(provinces.contains(i)) return;
		provinces.add(i);
		FactionManager.getMap().enqueue("nation", f.getRGB());
		f.updateTier();
	}

    public void removeProvince(int i, boolean destroyTitles) {
		for(int x = 0; x<provinces.size(); x++) {
			int p = provinces.get(x);
			if(p == i) {
				provinces.remove(x);
                if(destroyTitles) {
                    Title t = TitleLoader.getByProvince(p);
                    if(t != null) {
                        List<Integer> provinces = TitleManager.getProvinces(f);
                        List<Title> titles = TitleManager.getTitles(f);
                        t.destroy(f, provinces, titles);
                    }
                }
				f.getSettlementHandler().onProvinceLost(i);
				f.getInstallationHandler().onProvinceLost(i);
				break;
			}
		}
		FactionManager.getMap().enqueue("nation", f.getRGB());
		f.updateTier();
	}
	public List<Integer> getProvinces(){
		return provinces;
	}

    public List<Integer> getUntitledProvinces() {
		List<Integer> p = new ArrayList<>();
		for(int i : provinces) {
			if(TitleLoader.getByProvince(i) == null) p.add(i);
		}
		for(Faction subject : RelationManager.getSubjects(f)) {
			for(int i : subject.getProvinces()) {
				if(TitleLoader.getByProvince(i) == null) p.add(i);
			}
		}
		return p;
	}

    public void provinceCap() {
		if (CivilWarBorderLock.isLocked(f)) {
			return;
		}
		if(TitleManager.overProvinceCap(f) && provinces.size() > 0) {
			int toRemove = provinces.get(provinces.size() - 1);
			if(toRemove == capital && provinces.size() > 1) toRemove = provinces.get(provinces.size() - 2);
			if(toRemove == capital) return;
			removeProvince(toRemove, true);
		}
	}

    //claims

	public boolean canClaim(int provinceId, boolean sea) {
		if (provinces.contains(provinceId)) return false;

		ProvinceManager pm = SimpleFactions.getInstance().getProvinceManager();
		Province target = pm.get(provinceId);
		if (target == null || !target.isValid()) return false;
		if (provinces.isEmpty()) return true;

		// Effective adjacency: land, one water tile, or one sea tile from the capital's land blob
		Set<Integer> capitalBlob = capitalLandBlob(pm);
		for (int ownedId : provinces) {
			if (isEffectivelyAdjacent(pm, ownedId, provinceId, capitalBlob)) {
				return true;
			}
		}

		// 2️⃣ Sea adjacency mode, sailing only from land that traces back to the capital
		if (sea) {
			return isSeaAdjacentFromSet(pm, target, seaClaimSources(pm));
		}

		return false;
	}

	/**
	 * Check if a province can be claimed while excluding a guild's capital from legality calculations.
	 * Used for guild relocation logic - checks if a province is claimable without considering
	 * the specified guild's current capital as a valid claim source.
	 * 
	 * @param provinceId The province to check
	 * @param sea Whether sea adjacency is allowed
	 * @param guild The guild whose capital should be excluded from legality checks
	 * @return true if the province can be claimed without relying on the guild's capital
	 */
	public boolean canClaim(int provinceId, boolean sea, Guild guild) {
		if(provinces.contains(provinceId)) return false;

		ProvinceManager pm = SimpleFactions.getInstance().getProvinceManager();
		Province target = pm.get(provinceId);
		if (target == null || !target.isValid()) return false;
		if (provinces.isEmpty()) return true;

		if(guild.getCapital() == guild.getFaction().getCapital()) {
			// Guild capital is the faction capital - no need to exclude it
			return canClaim(provinceId, sea);
		}

		// Calculate which provinces would still be legal without this guild's capital
		Set<Integer> legalWithoutGuildCapital = calculateLegalProvincesExcludingGuildCapital(pm, guild);

		// Apply the same river/coastal adjacency as normal claims, without the old guild capital.
		Set<Integer> capitalBlob = new HashSet<>();
		if (hasCapital() && capital != guild.getCapital()) {
			capitalBlob.add(capital);
			floodLandExcluding(pm, capital, capitalBlob, guild.getCapital());
		}
		for (int ownedId : legalWithoutGuildCapital) {
			if (isEffectivelyAdjacent(pm, ownedId, provinceId, capitalBlob)) {
				return true;
			}
		}

		// 2) Sea-adjacency mode (only from legal provinces)
		if (sea) {
			return isSeaAdjacentFromSet(pm, target, legalWithoutGuildCapital);
		}

		return false;
	}

	/**
	 * Calculate which provinces would still be legal if a specific guild's capital was removed.
	 * This determines the "legal claim base" for guild relocation.
	 */
	private Set<Integer> calculateLegalProvincesExcludingGuildCapital(ProvinceManager pm, Guild guild) {
		Set<Integer> legal = new HashSet<>();

		int excludedCapital = guild.hasCapital() ? guild.getCapital() : -1;

		// 1) Flood from faction capital (if exists and is not the excluded capital)
		if (hasCapital() && capital != excludedCapital) {
			legal.add(capital);
			floodLandExcluding(pm, capital, legal, excludedCapital);
		}

		// 2) Flood from other guild capitals (excluding the specified guild's capital)
		for (Guild g : f.getGuildHandler().getGuilds()) {
			if (!g.hasCapital()) continue;
			int guildCap = g.getCapital();
			if (guildCap == excludedCapital) continue;

			legal.add(guildCap);
			floodLandExcluding(pm, guildCap, legal, excludedCapital);
		}

		return legal;
	}

	/**
	 * Flood-fill land provinces while excluding a specific province from traversal.
	 */
	private void floodLandExcluding(ProvinceManager pm, int start, Set<Integer> out, int excludeProvince) {
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		Set<Integer> visited = new HashSet<>();

		queue.add(start);
		visited.add(start);

		while (!queue.isEmpty()) {
			int current = queue.poll();
			out.add(current);

			Province p = pm.get(current);
			if (p == null) continue;

			for (int n : p.getNeighbours()) {
				if (visited.contains(n)) continue;
				if (!provinces.contains(n)) continue;
				if (n == excludeProvince) continue; // Skip the excluded province

				Province np = pm.get(n);
				if (np == null || np.isSea()) continue;

				visited.add(n);
				queue.add(n);
			}
		}
	}

	/**
	 * Check sea adjacency from a specific set of provinces (used for guild relocation checks).
	 */
	private boolean isSeaAdjacentFromSet(ProvinceManager pm, Province target, Set<Integer> fromProvinces) {
		Set<Integer> visited = new HashSet<>();
		ArrayDeque<Integer> queue = new ArrayDeque<>();

		// Seed the queue with sea provinces adjacent to the legal province set
		for (int ownedId : fromProvinces) {
			Province owned = pm.get(ownedId);
			if (owned == null) continue;

			for (int nId : owned.getNeighbours()) {
				Province n = pm.get(nId);
				if (n == null) continue;

				if (n.isSea()) {
					queue.add(nId);
					visited.add(nId);
				}
			}
		}

		// Flood-fill sea region
		while (!queue.isEmpty()) {
			int currentId = queue.poll();
			Province current = pm.get(currentId);
			if (current == null) continue;

			// If target touches this sea province
			if (current.getNeighbours().contains(target.getId())) {
				return true;
			}

			for (int nId : current.getNeighbours()) {
				if (visited.contains(nId)) continue;

				Province n = pm.get(nId);
				if (n == null || !n.isSea()) continue;

				visited.add(nId);
				queue.add(nId);
			}
		}

		return false;
	}

	private boolean isSeaAdjacent(ProvinceManager pm, Province target) {
		Set<Integer> visited = new HashSet<>();
		ArrayDeque<Integer> queue = new ArrayDeque<>();

		// Seed the queue with sea/water provinces adjacent to owned territory
		for (int ownedId : provinces) {
			Province owned = pm.get(ownedId);
			if (owned == null) continue;

			for (int nId : owned.getNeighbours()) {
				Province n = pm.get(nId);
				if (n == null) continue;

				if (n.isSea()) {
					queue.add(nId);
					visited.add(nId);
				}
			}
		}

		// Flood-fill sea region
		while (!queue.isEmpty()) {
			int currentId = queue.poll();
			Province current = pm.get(currentId);
			if (current == null) continue;

			// If target touches this sea province
			if (current.getNeighbours().contains(target.getId())) {
				return true;
			}

			for (int nId : current.getNeighbours()) {
				if (visited.contains(nId)) continue;

				Province n = pm.get(nId);
				if (n == null || !n.isSea()) continue;

				visited.add(nId);
				queue.add(nId);
			}
		}

		return false;
	}

	public void revalidateClaims() {
		ProvinceManager pm = SimpleFactions.getInstance().getProvinceManager();

		Set<Integer> legalCapitals = legalGuildCapitals(pm, capital, guildCapitals());
		for (Guild g : f.getGuildHandler().getGuilds()) {
			if (!g.hasCapital()) continue;

			if (!legalCapitals.contains(g.getCapital())) {
				g.setCapital(-1);
			}
		}

		Set<Integer> legal = computeLegalProvinces(pm, capital);

		for (int p : new ArrayList<>(provinces)) {
			if (!legal.contains(p)) {
				removeProvince(p, true);
			}
		}
	}

	public List<Integer> previewProvincesLostIfCapitalMoved(int newCapital) {
		if (!hasCapital() || newCapital == capital) {
			return List.of();
		}
		ProvinceManager pm = SimpleFactions.getInstance().getProvinceManager();
		Set<Integer> legal = computeLegalProvinces(pm, newCapital);
		List<Integer> lost = new ArrayList<>();
		for (int p : provinces) {
			if (!legal.contains(p)) {
				lost.add(p);
			}
		}
		lost.sort(Comparator.naturalOrder());
		return lost;
	}

	private List<Integer> guildCapitals() {
		if (f.getGuildHandler() == null) return List.of();
		return f.getGuildHandler().getGuilds().stream()
				.filter(Guild::hasCapital).map(Guild::getCapital).toList();
	}

	private Set<Integer> computeLegalProvinces(ProvinceManager pm, int factionCapital) {
		return computeLegalProvinces(pm, factionCapital, guildCapitals());
	}

	private Set<Integer> computeLegalProvinces(ProvinceManager pm, int factionCapital, java.util.Collection<Integer> capitals) {
		Set<Integer> capitalBlob = capitalLandBlob(pm, factionCapital);
		Set<Integer> seeds = new HashSet<>(legalGuildCapitals(pm, factionCapital, capitals, capitalBlob));
		if (provinces.contains(factionCapital)) {
			seeds.add(factionCapital);
		}
		return expandLegal(pm, seeds, capitalBlob);
	}

	/**
	 * Where a sea-mode claim may set sail from: land that traces back to the faction capital.
	 * A realm without a seated capital keeps sailing from anything it owns.
	 */
	private Set<Integer> seaClaimSources(ProvinceManager pm) {
		if (!provinces.contains(capital)) {
			return new HashSet<>(provinces);
		}
		return computeLegalProvinces(pm, capital);
	}

	private Set<Integer> legalGuildCapitals(ProvinceManager pm, int factionCapital, java.util.Collection<Integer> candidates) {
		return legalGuildCapitals(pm, factionCapital, candidates, capitalLandBlob(pm, factionCapital));
	}

	/**
	 * Guild capitals that trace back to the faction capital: over owned land, or by water from
	 * land already traced, so one colony can anchor the next. An enclave cut off from the realm
	 * cannot vouch for itself by touching its own lake.
	 */
	private Set<Integer> legalGuildCapitals(ProvinceManager pm, int factionCapital,
			java.util.Collection<Integer> candidates, Set<Integer> capitalBlob) {
		Set<Integer> legal = new HashSet<>();
		List<Integer> pending = new ArrayList<>();
		for (int cap : candidates) {
			Province site = pm.get(cap);
			if (provinces.contains(cap) && site != null && site.isValid() && !site.isSea()) {
				pending.add(cap);
			}
		}
		if (!provinces.contains(factionCapital)) {
			// Without a seated faction capital there is nothing to trace back to.
			for (int cap : pending) {
				if (isSeaAdjacent(pm, pm.get(cap))) legal.add(cap);
			}
			return legal;
		}

		Set<Integer> seeds = new HashSet<>(Set.of(factionCapital));
		boolean changed = true;
		while (changed) {
			changed = false;
			Set<Integer> reached = expandLegal(pm, seeds, capitalBlob);
			for (java.util.Iterator<Integer> it = pending.iterator(); it.hasNext();) {
				int cap = it.next();
				if (capitalBlob.contains(cap) || isSeaAdjacentFromSet(pm, pm.get(cap), reached)) {
					legal.add(cap);
					seeds.add(cap);
					it.remove();
					changed = true;
				}
			}
		}
		return legal;
	}

	private Set<Integer> expandLegal(ProvinceManager pm, Set<Integer> seeds, Set<Integer> capitalBlob) {
		Set<Integer> legal = new HashSet<>(seeds);

		// Follow the same edges that allowed these claims: owned land, WATER
		// bridges, and one SEA tile from the proposed capital's land blob.
		// Reached overseas holdings may extend inland or over WATER, but do not
		// become new sources for SEA crossings.
		ArrayDeque<Integer> queue = new ArrayDeque<>(legal);
		while (!queue.isEmpty()) {
			int from = queue.poll();
			for (int target : provinces) {
				if (legal.contains(target)) continue;
				Province province = pm.get(target);
				if (province == null || !province.isValid() || province.isSea()) continue;
				if (isEffectivelyAdjacent(pm, from, target, capitalBlob)) {
					legal.add(target);
					queue.add(target);
				}
			}
		}

		return legal;
	}

	/** Detached claim calculation for a persisted capital/relocation intent. */
	public record TopologyClaims(List<Integer> provinces, java.util.Map<String, Integer> capitals) {}
	public TopologyClaims previewTopology(int proposedCapital, List<Integer> owned,
			java.util.Map<String, Integer> capitals) {
		ProvinceHandler copy = new ProvinceHandler(f);
		copy.capital = proposedCapital;
		copy.provinces = new ArrayList<>(owned);
		ProvinceManager pm = SimpleFactions.getInstance().getProvinceManager();
		java.util.Map<String, Integer> after = new java.util.LinkedHashMap<>(capitals);
		Set<Integer> legalCapitals = copy.legalGuildCapitals(pm, proposedCapital,
				after.values().stream().filter(cap -> cap != -1).toList());
		after.replaceAll((id, cap) -> cap != -1 && !legalCapitals.contains(cap) ? -1 : cap);
		int effectiveCapital = after.getOrDefault(f.getId(), proposedCapital);
		copy.capital = effectiveCapital;
		Set<Integer> legal = copy.computeLegalProvinces(pm, effectiveCapital,
				after.values().stream().filter(cap -> cap != -1).toList());
		return new TopologyClaims(owned.stream().filter(legal::contains).toList(), java.util.Map.copyOf(after));
	}

	/**
	 * Provinces {@link #revalidateClaims()} would keep if this realm held exactly {@code owned},
	 * with its current faction and guild capitals. A guild capital that would be unseated is left
	 * out even when the province itself stays claimed, since unseating can dissolve its settlement.
	 */
	public Set<Integer> previewLegalProvinces(java.util.Collection<Integer> owned) {
		ProvinceHandler copy = new ProvinceHandler(f);
		copy.capital = capital;
		copy.provinces = new ArrayList<>(owned);
		ProvinceManager pm = SimpleFactions.getInstance().getProvinceManager();
		Set<Integer> legal = copy.computeLegalProvinces(pm, capital, guildCapitals());
		Set<Integer> legalCapitals = copy.legalGuildCapitals(pm, capital, guildCapitals());
		for (int cap : guildCapitals()) {
			if (!legalCapitals.contains(cap)) {
				legal.remove(cap);
			}
		}
		return legal;
	}

	/** Apply the exact saved topology; callers rebuild derived province data after all realms are restored. */
	public void restoreTopology(int restoredCapital, List<Integer> restoredProvinces) {
		capital = restoredCapital;
		provinces.clear();
		provinces.addAll(restoredProvinces);
	}

	private void floodLand(ProvinceManager pm, int start, Set<Integer> out) {
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		Set<Integer> visited = new HashSet<>();

		queue.add(start);
		visited.add(start);

		while (!queue.isEmpty()) {
			int current = queue.poll();
			out.add(current);

			Province p = pm.get(current);
			if (p == null) continue;

			for (int n : p.getNeighbours()) {
				if (visited.contains(n)) continue;
				if (!provinces.contains(n)) continue;

				Province np = pm.get(n);
				if (np == null || np.isSea()) continue; // 🔴 important

				visited.add(n);
				queue.add(n);
			}
		}
	}

    public String getClaimDeniedReason(int provinceId, boolean sea) {
		ProvinceManager pm = SimpleFactions.getInstance().getProvinceManager();
		Province target = pm.get(provinceId);

		if (target == null || !target.isValid()) {
			return "§cThis location has no province.";
		}

		if (provinces.contains(provinceId)) {
			return "§cThis province is already part of your realm.";
		}
		if (provinces.isEmpty()) return "Success";

		boolean adjacent = false;
		Set<Integer> capitalBlob = capitalLandBlob(pm);
		for (int ownedId : provinces) {
			if (isEffectivelyAdjacent(pm, ownedId, provinceId, capitalBlob)) {
				adjacent = true;
				break;
			}
		}

		if (!sea) {
			return adjacent
				? "Success"
				: "§cThis province does not border your current realm.";
		}

		if (adjacent || isSeaAdjacentFromSet(pm, target, seaClaimSources(pm))) {
			return "Success";
		}

		return "§cThis province must be connected to your realm by land or by sea.";
	}

	/**
	 * Owned land provinces reachable from the faction capital without crossing sea.
	 * A sea crossing is allowed from any province in this blob, not only the capital itself.
	 */
	private Set<Integer> capitalLandBlob(ProvinceManager pm) {
		return capitalLandBlob(pm, capital);
	}

	private Set<Integer> capitalLandBlob(ProvinceManager pm, int factionCapital) {
		Set<Integer> blob = new HashSet<>();
		if (factionCapital == -1 || !provinces.contains(factionCapital)) {
			return blob;
		}
		floodLand(pm, factionCapital, blob);
		return blob;
	}

	private boolean isEffectivelyAdjacent(
			ProvinceManager pm,
			int fromId,
			int targetId,
			Set<Integer> capitalBlob) {
		Province from = pm.get(fromId);
		Province target = pm.get(targetId);
		if (from == null || target == null) return false;

		if (from.getNeighbours().contains(targetId)) {
			return true;
		}

		for (int nId : from.getNeighbours()) {
			Province mid = pm.get(nId);
			if (mid == null || mid.getTerrain() != Terrain.WATER) continue;

			if (mid.getNeighbours().contains(targetId)) {
				return true;
			}
		}

		if (capitalBlob.contains(fromId)) {
			for (int nId : from.getNeighbours()) {
				Province sea = pm.get(nId);
				if (sea == null || sea.getTerrain() != Terrain.SEA) continue;

				if (sea.getNeighbours().contains(targetId)) {
					return true;
				}
			}
		}

		return false;
	}
}
