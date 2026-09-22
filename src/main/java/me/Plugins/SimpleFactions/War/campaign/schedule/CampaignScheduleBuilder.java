package me.Plugins.SimpleFactions.War.campaign.schedule;

import me.Plugins.SimpleFactions.Managers.LogManager;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleService.ScheduleLeg;
import me.Plugins.SimpleFactions.War.campaign.zoc.FortControlService;
import me.Plugins.SimpleFactions.War.campaign.zoc.FortZocIndex;
import me.Plugins.SimpleFactions.War.campaign.zoc.FortZocIndex.OperationalFort;
import me.Plugins.SimpleFactions.War.campaign.zoc.PortSeaZocIndex.OperationalPort;
import me.Plugins.SimpleFactions.War.campaign.zoc.PortSeaZocIndex;
import me.Plugins.SimpleFactions.enums.Terrain;

public final class CampaignScheduleBuilder {
	private static final Logger LOGGER = Logger.getLogger(CampaignScheduleBuilder.class.getName());

	public record BuiltSchedules(
			List<ScheduledCampaignBattle> invasion,
			List<ScheduledCampaignBattle> counter) {
	}

	private CampaignScheduleBuilder() {
	}

	public static List<ScheduledCampaignBattle> build(
			War war,
			List<Integer> axis,
			int borderStartIndex,
			int objectiveIndex,
			FortZocIndex fortIndex) {
		return build(war, axis, borderStartIndex, objectiveIndex, fortIndex, null);
	}

	public static List<ScheduledCampaignBattle> build(
			War war,
			List<Integer> axis,
			int borderStartIndex,
			int objectiveIndex,
			FortZocIndex fortIndex,
			PortSeaZocIndex portIndex) {
		return buildAll(war, axis, borderStartIndex, objectiveIndex, resolveCapitalIndex(axis, war), fortIndex, portIndex)
				.invasion();
	}

	public static List<ScheduledCampaignBattle> buildCounter(
			War war,
			List<Integer> axis,
			int borderStartIndex,
			int aggressorCapitalIndex,
			FortZocIndex fortIndex,
			PortSeaZocIndex portIndex) {
		return buildAll(war, axis, borderStartIndex, resolveObjectiveIndex(axis, war), aggressorCapitalIndex, fortIndex, portIndex)
				.counter();
	}

	public static BuiltSchedules buildAll(
			War war,
			List<Integer> axis,
			int borderStartIndex,
			int objectiveIndex,
			int aggressorCapitalIndex,
			FortZocIndex fortIndex,
			PortSeaZocIndex portIndex) {
		if (war == null || axis == null || axis.isEmpty() || fortIndex == null) {
			return new BuiltSchedules(List.of(), List.of());
		}
		if (borderStartIndex < 0 || borderStartIndex >= axis.size()) {
			return new BuiltSchedules(List.of(), List.of());
		}

		int borderProvinceId = axis.get(borderStartIndex);
		CampaignScheduleBuildContext ctx = new CampaignScheduleBuildContext(
				axis,
				borderProvinceId,
				borderStartIndex,
				objectiveIndex,
				fortIndex);
		int cadence = Math.max(1, Cache.warProvincesBetweenBattles);

		LogManager.section("Schedule build");
		LogManager.line("axis=%s", axis);
		LogManager.line(
				"borderIndex=%d borderProvince=%d objectiveIndex=%d objectiveProvince=%d capitalIndex=%d cadence=%d",
				borderStartIndex,
				borderProvinceId,
				objectiveIndex,
				objectiveIndex >= 0 && objectiveIndex < axis.size() ? axis.get(objectiveIndex) : -1,
				aggressorCapitalIndex,
				cadence);

		LogManager.section("Phase 1 border anchor");
		OperationalFort borderFort = fortIndex.fortForProvince(borderProvinceId).orElse(null);
		boolean enemyBorderFort = borderFort != null
				&& FortControlService.isEnemyControlled(war, borderFort.id(), CampaignCoalition.AGGRESSOR);
		boolean fortHomeOffAxis = enemyBorderFort && axis.indexOf(borderFort.province()) < 0;
		boolean borderIsObjective = objectiveIndex >= 0
				&& objectiveIndex < axis.size()
				&& axis.get(objectiveIndex) == borderProvinceId;
		if (enemyBorderFort) {
			LogManager.line(
					"Phase 1: border province %d covered by fort %s home=%d offAxis=%s objectiveBorder=%s; siege first",
					borderProvinceId,
					borderFort.id(),
					borderFort.province(),
					fortHomeOffAxis,
					borderIsObjective);
			CampaignBattlePlacer.placeBattle(
					ctx,
					war,
					ScheduleLeg.INVASION,
					borderProvinceId,
					BattleTrigger.FORT_ZOC,
					CampaignCoalition.AGGRESSOR,
					borderFort.id(),
					null);
		}
		if (!enemyBorderFort || borderIsObjective) {
			CampaignBattlePlacer.placeBattle(
					ctx,
					war,
					ScheduleLeg.INVASION,
					borderProvinceId,
					BattleTrigger.BORDER,
					CampaignCoalition.AGGRESSOR,
					null,
					null);
		}

		// Phase 2: invasion land walk
		if (objectiveIndex >= 0 && objectiveIndex < axis.size()) {
			LogManager.section("Phase 2 invasion land walk");
			walkLandLeg(
					ctx,
					war,
					axis,
					borderStartIndex,
					objectiveIndex,
					1,
					ScheduleLeg.INVASION,
					CampaignCoalition.AGGRESSOR,
					borderStartIndex,
					axis.get(objectiveIndex),
					cadence);
		}

		// Phase 3: counter land walk
		if (borderStartIndex > 0 && aggressorCapitalIndex >= 0 && aggressorCapitalIndex < borderStartIndex) {
			LogManager.section("Phase 3 counter land walk");
			walkLandLeg(
					ctx,
					war,
					axis,
					borderStartIndex - 1,
					aggressorCapitalIndex,
					-1,
					ScheduleLeg.COUNTER,
					CampaignCoalition.DEFENDER,
					borderStartIndex - 1,
					axis.get(aggressorCapitalIndex),
					cadence);
		} else if (aggressorCapitalIndex < 0) {
			LOGGER.warning("Counter leg: aggressor capital not on campaign axis; schedule walks to axis start only.");
		}

		// Phase 4: sea scans (after both land walks)
		if (objectiveIndex >= 0 && objectiveIndex < axis.size()) {
			LogManager.section("Phase 4 invasion sea scan");
			scanSeaLeg(
					ctx,
					war,
					axis,
					0,
					objectiveIndex,
					1,
					ScheduleLeg.INVASION,
					CampaignCoalition.AGGRESSOR,
					portIndex);
		}
		if (borderStartIndex > 0 && aggressorCapitalIndex >= 0 && aggressorCapitalIndex < borderStartIndex) {
			LogManager.section("Phase 4 counter sea scan");
			scanSeaLeg(
					ctx,
					war,
					axis,
					borderStartIndex - 1,
					aggressorCapitalIndex,
					-1,
					ScheduleLeg.COUNTER,
					CampaignCoalition.DEFENDER,
					portIndex);
		}

		return new BuiltSchedules(List.copyOf(ctx.invasion()), List.copyOf(ctx.counter()));
	}

	private static int resolveCapitalIndex(List<Integer> axis, War war) {
		if (war == null || war.getAttackers() == null || war.getAttackers().getLeader() == null) {
			return -1;
		}
		int capital = war.getAttackers().getLeader().getCapital();
		return capital > 0 ? axis.indexOf(capital) : -1;
	}

	private static int resolveObjectiveIndex(List<Integer> axis, War war) {
		if (war == null || war.getObjectiveProvinceId() == null) {
			return -1;
		}
		return axis.indexOf(war.getObjectiveProvinceId());
	}

	private static void walkLandLeg(
			CampaignScheduleBuildContext ctx,
			War war,
			List<Integer> axis,
			int fromIndex,
			int toIndex,
			int step,
			ScheduleLeg leg,
			CampaignCoalition advancing,
			int cadenceOriginIndex,
			int terminalProvinceId,
			int cadence) {
		for (int i = fromIndex; ; i += step) {
			int provinceId = axis.get(i);

			if (leg == ScheduleLeg.INVASION && i == ctx.cursorIndex()) {
				LogManager.line(
						"walk leg=%s axisIndex=%d province=%d (border already handled in phase 1)",
						leg,
						i,
						provinceId);
			} else if (leg == ScheduleLeg.COUNTER && i == ctx.cursorIndex()) {
				LogManager.line(
						"walk leg=%s axisIndex=%d province=%d (skip border on counter)",
						leg,
						i,
						provinceId);
			} else {
				boolean cadenceMatch = cadenceMatches(cadenceOriginIndex, i, cadence);
				var zocFort = ctx.fortIndex().fortForProvince(provinceId).orElse(null);
				LogManager.line(
						"walk leg=%s axisIndex=%d province=%d cadenceMatch=%s zocFort=%s",
						leg,
						i,
						provinceId,
						cadenceMatch,
						zocFort != null ? zocFort.id() : "-");
				if (cadenceMatch) {
					CampaignBattlePlacer.placeBattle(
							ctx, war, leg, provinceId, BattleTrigger.CADENCE, advancing, null, null);
				}
				if (zocFort != null) {
					CampaignBattlePlacer.placeBattle(
							ctx,
							war,
							leg,
							provinceId,
							BattleTrigger.FORT_ZOC,
							advancing,
							zocFort.id(),
							null);
				}
			}

			if (i == toIndex) {
				LogManager.line(
						"walk leg=%s terminal axisIndex=%d province=%d OBJECTIVE",
						leg,
						i,
						terminalProvinceId);
				CampaignBattlePlacer.placeBattle(
						ctx, war, leg, terminalProvinceId, BattleTrigger.OBJECTIVE, advancing, null, null);
				break;
			}
		}
	}

	private static boolean cadenceMatches(int cadenceOriginIndex, int axisIndex, int cadence) {
		int offset = Math.abs(axisIndex - cadenceOriginIndex);
		return offset % cadence == 0;
	}

	private static void scanSeaLeg(
			CampaignScheduleBuildContext ctx,
			War war,
			List<Integer> axis,
			int rangeStart,
			int rangeEnd,
			int step,
			ScheduleLeg leg,
			CampaignCoalition advancing,
			PortSeaZocIndex portIndex) {
		if (portIndex == null) {
			return;
		}
		ProvinceManager provinceManager = SimpleFactions.getInstance().getProvinceManager();

		for (int i = rangeStart; ; i += step) {
			if (!isSeaRunStart(axis, i, step, provinceManager)) {
				if (i == rangeEnd) {
					break;
				}
				continue;
			}

			List<Integer> seaRun = collectSeaRun(axis, i, rangeEnd, step, provinceManager);
			for (OperationalPort port : portIndex.portsCoveringSeaProvinces(seaRun)) {
				if (port == null || port.id() == null) {
					continue;
				}
				if (!isEnemyPort(war, port, advancing)) {
					continue;
				}
				if (seaRun.isEmpty()) {
					continue;
				}
				CampaignBattlePlacer.placeBattle(
						ctx,
						war,
						leg,
						seaRun.get(0),
						BattleTrigger.NAVAL,
						advancing,
						null,
						port.id());
			}

			if (i == rangeEnd) {
				break;
			}
		}
	}

	private static boolean isSeaRunStart(
			List<Integer> axis,
			int axisIndex,
			int step,
			ProvinceManager provinceManager) {
		if (axisIndex < 0 || axisIndex >= axis.size()) {
			return false;
		}
		Province province = provinceManager.get(axis.get(axisIndex));
		if (province == null || province.getTerrain() != Terrain.SEA) {
			return false;
		}
		int adjacentIndex = axisIndex - Integer.signum(step);
		if (adjacentIndex < 0 || adjacentIndex >= axis.size()) {
			return true;
		}
		Province adjacent = provinceManager.get(axis.get(adjacentIndex));
		return adjacent == null || adjacent.getTerrain() != Terrain.SEA;
	}

	private static List<Integer> collectSeaRun(
			List<Integer> axis,
			int startIndex,
			int rangeEnd,
			int step,
			ProvinceManager provinceManager) {
		List<Integer> seaRun = new ArrayList<>();
		for (int i = startIndex; ; i += step) {
			if (i < 0 || i >= axis.size()) {
				break;
			}
			Province province = provinceManager.get(axis.get(i));
			if (province == null || province.getTerrain() != Terrain.SEA) {
				break;
			}
			seaRun.add(axis.get(i));
			if (i == rangeEnd) {
				break;
			}
		}
		return seaRun;
	}

	private static boolean isEnemyPort(War war, OperationalPort port, CampaignCoalition advancing) {
		if (war == null || port == null || port.owner() == null) {
			return false;
		}
		Side ownerSide = war.getSide(port.owner());
		if (ownerSide == null) {
			return false;
		}
		CampaignCoalition coalition = CampaignCoalitionService.coalitionOf(war, ownerSide);
		return coalition != null && coalition != advancing;
	}
}
