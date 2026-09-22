package me.Plugins.SimpleFactions.government.movement.admin;

import java.util.ArrayList;
import java.util.List;

import me.Plugins.SimpleFactions.Database.Database;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.LogManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.civilwar.CivilWarStartService;
import me.Plugins.SimpleFactions.government.movement.Movement;
import me.Plugins.SimpleFactions.government.movement.MovementOutcomeService;
import me.Plugins.SimpleFactions.government.movement.MovementOutcomeSource;
import me.Plugins.SimpleFactions.government.movement.Pool;
import me.Plugins.SimpleFactions.government.movement.cause.Cause;

public final class MovementAdminService {
	public static final String USAGE = "§cUsage: /movement admin list|join|leave|demands|target ...";
	public static final String JOIN_USAGE =
			"§cUsage: /movement admin join <id> supporter citizen|guild|vassal <target>"
					+ " | join <id> cause <index> citizen|guild|vassal <target>"
					+ " | join <id> backer <faction>";
	public static final String LEAVE_USAGE =
			"§cUsage: /movement admin leave <id> supporter citizen|guild|vassal <target>"
					+ " | leave <id> cause <index> citizen|guild|vassal <target>"
					+ " | leave <id> backer <faction>";
	public static final String DEMANDS_USAGE =
			"§cUsage: /movement admin demands <id> accept|reject";
	public static final String TARGET_USAGE =
			"§cUsage: /movement admin target <id> <causeIndex> <player>";

	private MovementAdminService() {}

	public record Result(boolean ok, String message) {}

	public static List<String> listLines() {
		List<String> lines = new ArrayList<>();
		if (FactionManager.factions == null || FactionManager.factions.isEmpty()) {
			lines.add("§7No movements.");
			return lines;
		}
		for (Faction faction : FactionManager.factions) {
			if (faction == null || faction.getGovernment() == null) {
				continue;
			}
			for (Movement movement : faction.getGovernment().getMovements()) {
				if (movement == null) {
					continue;
				}
				lines.add(formatListLine(faction, movement));
			}
		}
		if (lines.isEmpty()) {
			lines.add("§7No movements.");
		}
		return lines;
	}

	public static Result joinSupporter(String movementId, String memberType, String targetId) {
		return mutateMember(true, movementId, null, memberType, targetId);
	}

	public static Result leaveSupporter(String movementId, String memberType, String targetId) {
		return mutateMember(false, movementId, null, memberType, targetId);
	}

	public static Result joinCause(String movementId, String causeIndex, String memberType, String targetId) {
		return mutateMember(true, movementId, causeIndex, memberType, targetId);
	}

	public static Result leaveCause(String movementId, String causeIndex, String memberType, String targetId) {
		return mutateMember(false, movementId, causeIndex, memberType, targetId);
	}

	public static Result joinBacker(String movementId, String factionId) {
		return mutateBacker(true, movementId, factionId);
	}

	public static Result leaveBacker(String movementId, String factionId) {
		return mutateBacker(false, movementId, factionId);
	}

	public static Result demands(String movementId, String outcome) {
		Movement movement = resolveMovement(movementId);
		if (movement == null) {
			return new Result(false, "§cUnknown movement: " + movementId);
		}
		if (outcome == null) {
			return new Result(false, DEMANDS_USAGE);
		}
		String missingTarget = missingTargetReason(movement);
		if (missingTarget != null) {
			return new Result(false, missingTarget);
		}
		if (outcome.equalsIgnoreCase("accept")) {
			LogManager.movement(
					"ADMIN_DEMANDS_ACCEPT movementId=%s faction=%s power=%.1f",
					movement.getId(),
					movement.getFaction() != null ? movement.getFaction().getId() : "-",
					movement.getPower());
			MovementOutcomeService.apply(movement, MovementOutcomeSource.ACCEPTED);
			persist(movement);
			return new Result(true, "§aAccepted demands for " + movement.getId() + " (organization bypassed).");
		}
		if (outcome.equalsIgnoreCase("reject") || outcome.equalsIgnoreCase("decline")) {
			LogManager.movement(
					"ADMIN_DEMANDS_REJECT movementId=%s faction=%s power=%.1f",
					movement.getId(),
					movement.getFaction() != null ? movement.getFaction().getId() : "-",
					movement.getPower());
			String error = CivilWarStartService.start(movement);
			if (error != null) {
				return new Result(false, error);
			}
			persist(movement);
			return new Result(true, "§aRejected demands for " + movement.getId() + " (organization bypassed). Civil war started.");
		}
		return new Result(false, DEMANDS_USAGE);
	}

	public static Result target(String movementId, String causeIndex, String player) {
		Movement movement = resolveMovement(movementId);
		if (movement == null) {
			return new Result(false, "§cUnknown movement: " + movementId);
		}
		if (movement.isFrozen()) {
			return new Result(false, "§cMovement is frozen.");
		}
		Cause cause = resolveCause(movement, causeIndex);
		if (cause == null) {
			return new Result(false, "§cUnknown cause index: " + causeIndex);
		}
		if (cause.getProposal() == null || !cause.getProposal().needsTarget()) {
			return new Result(false, "§cThat cause does not take a wanted leader.");
		}
		if (player == null || player.isBlank()) {
			return new Result(false, TARGET_USAGE);
		}
		Faction host = movement.getFaction();
		if (host == null || !host.canBecomeLeader(player)) {
			return new Result(false, "§c" + player + " cannot be the wanted leader.");
		}
		cause.getProposal().setTarget(player);
		persist(movement);
		return new Result(true, "§aSet wanted leader of " + movement.getId() + " cause " + causeIndex + " to " + player + ".");
	}

	private static String missingTargetReason(Movement movement) {
		if (movement.getCauses() == null) {
			return null;
		}
		for (Cause cause : movement.getCauses()) {
			if (cause == null || cause.getProposal() == null) {
				continue;
			}
			if (cause.getProposal().isPoliticalActionProposal() && cause.getProposal().needsTarget()
					&& !cause.getProposal().hasTarget()) {
				return "§cOne or more causes lack a target.";
			}
		}
		return null;
	}

	private static Result mutateMember(
			boolean joining,
			String movementId,
			String causeIndex,
			String memberType,
			String targetId) {
		Movement movement = resolveMovement(movementId);
		if (movement == null) {
			return new Result(false, "§cUnknown movement: " + movementId);
		}
		Cause cause = null;
		if (causeIndex != null) {
			cause = resolveCause(movement, causeIndex);
			if (cause == null) {
				return new Result(false, "§cUnknown cause index: " + causeIndex);
			}
		}
		Object obj = resolveMember(memberType, targetId);
		if (obj == null) {
			return new Result(false, "§cUnknown " + memberType + ": " + targetId);
		}
		if (joining) {
			return joinMember(movement, cause, obj, memberType, targetId);
		}
		return leaveMember(movement, cause, obj, memberType, targetId);
	}

	private static Result mutateBacker(boolean joining, String movementId, String factionId) {
		Movement movement = resolveMovement(movementId);
		if (movement == null) {
			return new Result(false, "§cUnknown movement: " + movementId);
		}
		if (factionId == null || factionId.isBlank()) {
			return new Result(false, joining ? JOIN_USAGE : LEAVE_USAGE);
		}
		Faction backer = FactionManager.getByString(factionId);
		if (backer == null) {
			return new Result(false, "§cUnknown faction: " + factionId);
		}
		if (joining) {
			if (movement.isFrozen()) {
				return new Result(false, "§cMovement is frozen.");
			}
			if (movement.getForeignBackers().contains(backer)) {
				return new Result(false, "§cAlready a foreign backer.");
			}
			String block = movement.foreignBackerBlockReason(backer, true);
			if (block != null) {
				return new Result(false, block);
			}
			movement.joinAsForeignBacker(backer);
			persist(movement);
			return new Result(true, "§aAdded foreign backer " + backer.getId() + " to " + movement.getId() + ".");
		}
		if (!movement.getForeignBackers().contains(backer)) {
			return new Result(false, "§cNot a foreign backer.");
		}
		movement.leaveAsForeignBacker(backer);
		persist(movement);
		return new Result(true, "§aRemoved foreign backer " + backer.getId() + " from " + movement.getId() + ".");
	}

	private static Result joinMember(Movement movement, Cause cause, Object obj, String type, String target) {
		if (movement.isFrozen()) {
			return new Result(false, "§cMovement is frozen.");
		}
		if (isPresent(movement, cause, obj)) {
			return new Result(false, "§cAlready in this slot.");
		}
		String block = movement.joinBlockReason(obj, cause, true);
		if (block != null) {
			return new Result(false, block);
		}
		movement.join(obj, cause);
		if (!isPresent(movement, cause, obj)) {
			return new Result(false, "§cCould not add: join did not stick.");
		}
		persist(movement);
		String where = cause == null ? "supporters" : "cause " + cause.getIndex();
		return new Result(true, "§aAdded " + type + " " + target + " to " + movement.getId() + " " + where + ".");
	}

	private static Result leaveMember(Movement movement, Cause cause, Object obj, String type, String target) {
		if (!isPresent(movement, cause, obj)) {
			return new Result(false, "§cNot in this slot.");
		}
		movement.leave(obj, cause);
		persist(movement);
		String where = cause == null ? "supporters" : "cause " + cause.getIndex();
		return new Result(true, "§aRemoved " + type + " " + target + " from " + movement.getId() + " " + where + ".");
	}

	private static Movement resolveMovement(String movementId) {
		if (movementId == null || movementId.isBlank()) {
			return null;
		}
		return FactionManager.getMovementById(movementId);
	}

	private static Cause resolveCause(Movement movement, String indexArg) {
		try {
			int index = Integer.parseInt(indexArg);
			if (index < 0 || index >= movement.getCauses().size()) {
				return null;
			}
			return movement.getCauses().get(index);
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static Object resolveMember(String type, String target) {
		if (type == null || target == null || target.isBlank()) {
			return null;
		}
		return switch (type.toLowerCase()) {
			case "citizen" -> target;
			case "guild" -> FactionManager.getGuildByString(target);
			case "vassal" -> FactionManager.getByString(target);
			default -> null;
		};
	}

	private static boolean isPresent(Movement movement, Cause cause, Object obj) {
		Pool pool = cause == null ? movement.getSupporters() : cause.getPool();
		if (obj instanceof String citizen) {
			return pool.getCitizens().contains(citizen);
		}
		if (obj instanceof Guild guild) {
			return pool.getGuilds().contains(guild);
		}
		if (obj instanceof Faction faction) {
			return pool.getFactions().contains(faction);
		}
		return false;
	}

	private static String formatListLine(Faction host, Movement movement) {
		String leader = movement.getLeader() == null ? "None" : movement.getLeader();
		String phase = movement.getPhase() == null ? "-" : movement.getPhase().name();
		int supporters = movement.getSupporters() == null ? 0 : movement.getSupporters().getAllMembers().size();
		int backers = movement.getForeignBackers() == null ? 0 : movement.getForeignBackers().size();
		return "§e"
				+ movement.getId()
				+ " §7host: §f"
				+ host.getId()
				+ " §7leader: §f"
				+ leader
				+ " §7phase: §f"
				+ phase
				+ " §7frozen: §f"
				+ movement.isFrozen()
				+ " §7causes: §f"
				+ movement.getCauses().size()
				+ " §7supporters: §f"
				+ supporters
				+ " §7backers: §f"
				+ backers;
	}

	private static void persist(Movement movement) {
		Faction host = movement.getFaction();
		if (host != null) {
			new Database().saveFaction(host);
		}
	}

	public static List<String> allMovementIds() {
		List<String> ids = new ArrayList<>();
		if (FactionManager.factions == null) {
			return ids;
		}
		for (Faction faction : FactionManager.factions) {
			if (faction == null || faction.getGovernment() == null) {
				continue;
			}
			for (Movement movement : faction.getGovernment().getMovements()) {
				if (movement != null && movement.getId() != null) {
					ids.add(movement.getId());
				}
			}
		}
		return ids;
	}

	public static List<String> hostCitizenNames(Movement movement) {
		Faction host = movement.getFaction();
		if (host == null || host.getOrCreateMainGuild() == null || host.getOrCreateMainGuild().getMembers() == null) {
			return List.of();
		}
		return new ArrayList<>(host.getOrCreateMainGuild().getMembers());
	}

	public static List<String> hostGuildIds(Movement movement) {
		List<String> ids = new ArrayList<>();
		Faction host = movement.getFaction();
		if (host == null || host.getGuildHandler() == null) {
			return ids;
		}
		for (Guild guild : host.getGuildHandler().getGuilds()) {
			if (guild == null || guild.isBase() || guild.getId() == null) {
				continue;
			}
			ids.add(guild.getId());
		}
		return ids;
	}

	public static List<String> hostSubjectIds(Movement movement) {
		List<String> ids = new ArrayList<>();
		Faction host = movement.getFaction();
		if (host == null || host.getSubjects() == null) {
			return ids;
		}
		for (Faction subject : host.getSubjects()) {
			if (subject != null && subject.getId() != null) {
				ids.add(subject.getId());
			}
		}
		return ids;
	}

	public static List<String> otherFactionIds(Movement movement) {
		List<String> ids = new ArrayList<>();
		Faction host = movement.getFaction();
		String hostId = host == null ? null : host.getId();
		if (FactionManager.factions == null) {
			return ids;
		}
		for (Faction faction : FactionManager.factions) {
			if (faction == null || faction.getId() == null) {
				continue;
			}
			if (hostId != null && faction.getId().equalsIgnoreCase(hostId)) {
				continue;
			}
			ids.add(faction.getId());
		}
		return ids;
	}

	public static List<String> wantedLeaderNames(Movement movement) {
		List<String> names = new ArrayList<>();
		Faction host = movement == null ? null : movement.getFaction();
		if (host == null || host.getMembers() == null) {
			return names;
		}
		for (String member : host.getMembers()) {
			if (host.canBecomeLeader(member)) {
				names.add(member);
			}
		}
		return names;
	}

	public static List<String> causeIndices(Movement movement) {
		List<String> indices = new ArrayList<>();
		for (int i = 0; i < movement.getCauses().size(); i++) {
			indices.add(String.valueOf(i));
		}
		return indices;
	}
}
