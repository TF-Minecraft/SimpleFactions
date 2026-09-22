package me.Plugins.SimpleFactions.mercenary.contract;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Utils.PostSettlementPayouts.PlayerUuidLookup;
import me.Plugins.SimpleFactions.War.battle.events.BattleEndedEvent;
import me.Plugins.SimpleFactions.War.battle.events.BattleStartedEvent;
import me.Plugins.SimpleFactions.War.core.War;

/**
 * Present at start and present at end, where end means on the roster at
 * resolution rather than alive or online. A restart that loses the start
 * snapshot records no absence.
 */
public final class AttendanceService {
    /**
     * Counts drive the refund and the lives, but the per-battle wage has to know
     * <em>which</em> soldier showed up, so the attending ids travel with them.
     */
    public record Result(int attended, int absent, boolean snapshotMissing, Set<UUID> attendedIds) {
        public Result(int attended, int absent, boolean snapshotMissing) {
            this(attended, absent, snapshotMissing, Set.of());
        }

        public static Result none() {
            return new Result(0, 0, true);
        }
    }

    private static final Map<String, Set<UUID>> startSnapshots = new HashMap<>();
    private static final Map<String, Map<String, Result>> results = new HashMap<>();

    private AttendanceService() {
    }

    public static void reset() {
        startSnapshots.clear();
        results.clear();
    }

    public static void onStarted(String battleId, Set<UUID> snapshot) {
        if (battleId == null) return;
        startSnapshots.put(battleId, snapshot == null ? Set.of() : Set.copyOf(snapshot));
    }

    public static void onEnded(String battleId, Integer warId, Set<UUID> endSnapshot) {
        if (battleId == null) return;
        Set<UUID> start = startSnapshots.remove(battleId);
        if (start == null) {
            return;
        }
        War war = warId == null ? null : WarManager.getById(warId);
        if (war == null) return;
        Set<UUID> end = endSnapshot == null ? Set.of() : endSnapshot;
        PlayerUuidLookup uuids = MercenaryEngagements.uuidLookup();
        recordSide(battleId, MercenaryEngagements.on(war, war.getAttackers()), start, end, uuids);
        recordSide(battleId, MercenaryEngagements.on(war, war.getDefenders()), start, end, uuids);
    }

    public static Result result(MercenaryContract contract, String battleId) {
        if (contract == null || battleId == null) return Result.none();
        Map<String, Result> byBattle = results.get(contract.getId());
        if (byBattle == null) return Result.none();
        return byBattle.getOrDefault(battleId, Result.none());
    }

    private static void recordSide(
            String battleId,
            java.util.List<MercenaryEngagements.Engagement> engagements,
            Set<UUID> start,
            Set<UUID> end,
            PlayerUuidLookup uuids) {
        for (MercenaryEngagements.Engagement engagement : engagements) {
            if (engagement.contract() == null || engagement.company() == null) continue;
            Set<UUID> enlisted = enlistedIds(engagement, uuids);
            Set<UUID> present = new HashSet<>();
            for (UUID id : enlisted) {
                if (start.contains(id) && end.contains(id)) present.add(id);
            }
            int attended = Math.min(present.size(), engagement.promisedSlots());
            int absent = Math.max(0, engagement.promisedSlots() - attended);
            if (absent > 0) {
                engagement.contract().markAttendanceFailure();
            }
            results.computeIfAbsent(engagement.contract().getId(), k -> new HashMap<>())
                    .put(battleId, new Result(attended, absent, false, Set.copyOf(present)));
        }
    }

    private static Set<UUID> enlistedIds(
            MercenaryEngagements.Engagement engagement, PlayerUuidLookup uuids) {
        Set<UUID> ids = new HashSet<>();
        for (String name : engagement.company().getEnlisted()) {
            UUID id = uuids == null ? null : uuids.uuidOf(name);
            if (id != null) ids.add(id);
        }
        return ids;
    }

    public static final class Hook implements Listener {
        @EventHandler
        public void onBattleStarted(BattleStartedEvent event) {
            onStarted(event.getBattleId(), event.getParticipantIds());
            ContractAccrualService.onBattleStarted(event.getBattleId(), event.getWarId());
        }

        @EventHandler
        public void onBattleEnded(BattleEndedEvent event) {
            onEnded(event.getBattleId(), event.getWarId(), event.getParticipantIds());
            ContractAccrualService.onBattleEnded(event.getBattleId(), event.getWarId());
        }
    }
}
