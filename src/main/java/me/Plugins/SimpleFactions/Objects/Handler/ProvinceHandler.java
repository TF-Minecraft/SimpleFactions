package me.Plugins.SimpleFactions.Objects.Handler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.Plugins.SimpleFactions.War.civilwar.wartime.CivilWarBorderLock;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Loaders.TitleLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Tiers.Title;
import me.Plugins.SimpleFactions.enums.Terrain;

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
				return;
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
		if (provinces.size() == 0) return true;
		if (provinces.contains(provinceId)) return false;

		ProvinceManager pm = SimpleFactions.getInstance().getProvinceManager();
		Province target = pm.get(provinceId);
		if (target == null || !target.isValid()) return false;

		// 1️⃣ Effective adjacency (land + water + capital sea fan-out)
		for (int ownedId : provinces) {
			if (isEffectivelyAdjacent(pm, ownedId, provinceId)) {
				return true;
			}
		}

		// 2️⃣ Sea adjacency mode (existing flood logic)
		if (sea) {
			return isSeaAdjacent(pm, target);
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
		if(provinces.size() == 0) return true;
		if(provinces.contains(provinceId)) return false;

		ProvinceManager pm = SimpleFactions.getInstance().getProvinceManager();
		Province target = pm.get(provinceId);
		if (target == null || !target.isValid()) return false;

		if(guild.getCapital() == guild.getFaction().getCapital()) {
			// Guild capital is the faction capital - no need to exclude it
			return canClaim(provinceId, sea);
		}

		// Calculate which provinces would still be legal without this guild's capital
		Set<Integer> legalWithoutGuildCapital = calculateLegalProvincesExcludingGuildCapital(pm, guild);

		// 1) Normal land adjacency (only check against legal provinces)
		for (int ownedId : legalWithoutGuildCapital) {
			Province owned = pm.get(ownedId);
			if (owned != null && owned.getNeighbours().contains(provinceId)) {
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

	private boolean isLandConnected(ProvinceManager pm, int startId, int targetId) {
		Set<Integer> visited = new HashSet<>();
		ArrayDeque<Integer> queue = new ArrayDeque<>();

		queue.add(startId);
		visited.add(startId);

		while (!queue.isEmpty()) {
			int currentId = queue.poll();
			if (currentId == targetId) return true;

			Province current = pm.get(currentId);
			if (current == null) continue;

			for (int nId : current.getNeighbours()) {
				if (visited.contains(nId)) continue;
				if (!provinces.contains(nId)) continue;

				Province n = pm.get(nId);
				if (n == null || n.isSea()) continue;

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

		for (Guild g : f.getGuildHandler().getGuilds()) {
			if (!g.hasCapital()) continue;

			int cap = g.getCapital();
			if (!isCapitalStillLegal(pm, cap, capital)) {
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

	private Set<Integer> computeLegalProvinces(ProvinceManager pm, int factionCapital) {
		Set<Integer> legal = new HashSet<>();

		for (Guild g : f.getGuildHandler().getGuilds()) {
			if (!g.hasCapital()) continue;

			int cap = g.getCapital();
			if (!isCapitalStillLegal(pm, cap, factionCapital)) {
				continue;
			}
			legal.add(cap);
			floodLand(pm, cap, legal);
		}

		if (factionCapital != -1) {
			legal.add(factionCapital);
			floodLand(pm, factionCapital, legal);
		}

		return legal;
	}

	private boolean isCapitalStillLegal(ProvinceManager pm, int capitalId) {
		return isCapitalStillLegal(pm, capitalId, capital);
	}

	private boolean isCapitalStillLegal(ProvinceManager pm, int guildCapitalId, int factionCapitalId) {
		if (factionCapitalId != -1 && isLandConnected(pm, factionCapitalId, guildCapitalId)) {
			return true;
		}

		return isSeaAdjacent(pm, pm.get(guildCapitalId));
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

		boolean adjacent = false;
		for (int ownedId : provinces) {
			if (isEffectivelyAdjacent(pm, ownedId, provinceId)) {
				adjacent = true;
				break;
			}
		}

		if (!sea) {
			return adjacent
				? "Success"
				: "§cThis province does not border your current realm.";
		}

		if (adjacent || isSeaAdjacent(pm, target)) {
			return "Success";
		}

		return "§cThis province must be connected to your realm by land or by sea.";
	}

	private boolean isEffectivelyAdjacent(ProvinceManager pm, int fromId, int targetId) {
		Province from = pm.get(fromId);
		Province target = pm.get(targetId);
		if (from == null || target == null) return false;

		// 1️⃣ Direct adjacency
		if (from.getNeighbours().contains(targetId)) {
			return true;
		}

		// 2️⃣ Single WATER bridge
		for (int nId : from.getNeighbours()) {
			Province mid = pm.get(nId);
			if (mid == null || mid.getTerrain() != Terrain.WATER) continue;

			if (mid.getNeighbours().contains(targetId)) {
				return true;
			}
		}

		// 3️⃣ Capital SEA fan-out
		if (fromId == capital) {
			for (int nId : from.getNeighbours()) {
				Province sea = pm.get(nId);
				if (sea == null || !sea.isSea()) continue;

				if (sea.getNeighbours().contains(targetId)) {
					return true;
				}
			}
		}

		return false;
	}
}
