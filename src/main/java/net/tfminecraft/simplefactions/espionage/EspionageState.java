package net.tfminecraft.simplefactions.espionage;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/** Stored inside faction JSON; no scheduler or player-specific roll cache is needed. */
public class EspionageState {
    private Map<SpecialPosition, SpecialPositionAssignment> positions = new EnumMap<>(SpecialPosition.class);
    private Map<String, Integer> appointmentAptitudes = new HashMap<>();
    private DailyRolls dailyRolls;
    private Map<String, IntelligenceReport> reports = new HashMap<>();
    private Map<SpecialPosition, Integer> appointments = new EnumMap<>(SpecialPosition.class);
    private java.util.List<OfficeUnrest> unrest = new java.util.ArrayList<>();
    private java.util.Set<SpecialPosition> pendingFounders = java.util.EnumSet.noneOf(SpecialPosition.class);
    private Map<SpecialPosition, String> pendingFounderCharacters = new EnumMap<>(SpecialPosition.class);
    // Last deliberate appointment per office; starts the wait before the next one.
    private Map<SpecialPosition, Long> lastAppointments = new EnumMap<>(SpecialPosition.class);

    // Chosen by the Spymaster. Unknown shares nothing; older saves have no entry.
    private Map<SharingPartner, IntelligenceTier> sharing = new EnumMap<>(SharingPartner.class);

    public IntelligenceTier sharing(SharingPartner partner) {
        var tier = sharing == null ? null : sharing.get(partner);
        return tier == null ? IntelligenceTier.UNKNOWN : tier;
    }

    void share(SharingPartner partner, IntelligenceTier tier) {
        if (sharing == null) sharing = new EnumMap<>(SharingPartner.class);
        if (tier == null || tier == IntelligenceTier.UNKNOWN) sharing.remove(partner);
        else sharing.put(partner, tier);
    }

    void pendingFounder(SpecialPosition office) { pendingFounders.add(office); }
    void pendingFounder(SpecialPosition office, String characterId) {
        pendingFounder(office);
        if (characterId != null) pendingFounderCharacters.put(office, characterId);
    }
    String pendingFounderCharacter(SpecialPosition office) { return pendingFounderCharacters.get(office); }
    boolean hasPendingFounder() { return !pendingFounders.isEmpty(); }
    boolean isPendingFounder(SpecialPosition office) { return pendingFounders.contains(office); }

    record OfficeSnapshot(Map<SpecialPosition, SpecialPositionAssignment> positions,
            Map<SpecialPosition, Integer> appointments, java.util.List<OfficeUnrest> unrest,
            java.util.Set<SpecialPosition> pendingFounders, Map<SpecialPosition, String> pendingFounderCharacters,
            Map<SpecialPosition, Long> lastAppointments) {}

    OfficeSnapshot snapshotOffices() {
        var savedPositions = new EnumMap<SpecialPosition, SpecialPositionAssignment>(SpecialPosition.class);
        savedPositions.putAll(positions);
        var savedAppointments = new EnumMap<SpecialPosition, Integer>(SpecialPosition.class);
        savedAppointments.putAll(appointments);
        return new OfficeSnapshot(savedPositions, savedAppointments,
                new java.util.ArrayList<>(unrest), new java.util.HashSet<>(pendingFounders), new HashMap<>(pendingFounderCharacters),
                new HashMap<>(lastAppointments));
    }

    void restoreOffices(OfficeSnapshot snapshot) {
        positions.clear(); positions.putAll(snapshot.positions());
        appointments.clear(); appointments.putAll(snapshot.appointments());
        unrest.clear(); unrest.addAll(snapshot.unrest());
        pendingFounders.clear(); pendingFounders.addAll(snapshot.pendingFounders());
        pendingFounderCharacters.clear(); pendingFounderCharacters.putAll(snapshot.pendingFounderCharacters());
        lastAppointments.clear(); lastAppointments.putAll(snapshot.lastAppointments());
    }

    public SpecialPositionAssignment holder(SpecialPosition office) { return positions.get(office); }

    public int appointmentCount(SpecialPosition office) {
        if (appointments.containsKey(office)) return appointments.get(office);
        var holder = positions.get(office);
        // Existing dev offices already used their first appointment. Old removed holders leave a legacy roll.
        return holder != null && !holder.automatic || office == SpecialPosition.SPYMASTER && !appointmentAptitudes.isEmpty() ? 1 : 0;
    }

    public void assignFounder(SpecialPosition office, SpecialPositionAssignment assignment, int aptitude) {
        if (positions.containsKey(office)) return;
        assignment.automatic = true;
        assignment.aptitude = EspionageMath.clamp(aptitude, 0, 100);
        positions.put(office, assignment);
        pendingFounders.remove(office);
        pendingFounderCharacters.remove(office);
    }

    public void addUnrest(SpecialPosition office, double points, double days, long now) {
        unrest.removeIf(entry -> entry.endsAt <= now);
        if (points > 0 && days > 0) unrest.add(new OfficeUnrest(office, points, now, now + Math.max(1, Math.round(days * 86_400_000))));
    }

    public java.util.List<net.tfminecraft.simplefactions.government.StabilityModifier> unrestModifiers(long now) {
        return unrest.stream().filter(entry -> entry.valueAt(now) < 0)
                .map(entry -> new net.tfminecraft.simplefactions.government.StabilityModifier(
                        "Replaced " + entry.office.label(), entry.valueAt(now), 0)).toList();
    }

    public record OfficeUnrest(SpecialPosition office, double points, long startedAt, long endsAt) {
        public double valueAt(long now) {
            if (endsAt <= startedAt || now >= endsAt) return 0;
            return -points * Math.min(1, (endsAt - (double) now) / (endsAt - (double) startedAt));
        }
    }

    public SpecialPositionAssignment getSpymaster() {
        return positions.get(SpecialPosition.SPYMASTER);
    }

    public void removeSpymaster() {
        pendingFounders.remove(SpecialPosition.SPYMASTER);
        pendingFounderCharacters.remove(SpecialPosition.SPYMASTER);
        appointments.put(SpecialPosition.SPYMASTER, appointmentCount(SpecialPosition.SPYMASTER));
        positions.remove(SpecialPosition.SPYMASTER);
    }

    /** Legacy format helper retained for migration tests. Runtime appointments use the global registry. */
    @Deprecated
    void appoint(SpecialPositionAssignment assignment, Supplier<Integer> aptitudeRoll) {
        int previousAppointments = appointmentCount(SpecialPosition.SPYMASTER);
        String key = assignment.playerId + ":" + assignment.characterId;
        assignment.aptitude = appointmentAptitudes.computeIfAbsent(key,
                ignored -> EspionageMath.clamp(aptitudeRoll.get(), 0, 100));
        appointments.put(SpecialPosition.SPYMASTER, previousAppointments);
        appoint(assignment, assignment.aptitude);
    }

    /** Appoints without a build-up or change wait, as for offices saved before either existed. */
    public void appoint(SpecialPositionAssignment assignment, int permanentAptitude) {
        appoint(assignment, permanentAptitude, 0);
    }

    /** A positive time starts the holder's build-up and the wait before the next appointment. */
    public void appoint(SpecialPositionAssignment assignment, int permanentAptitude, long now) {
        if (now > 0) lastAppointments.put(SpecialPosition.SPYMASTER, now);
        assignment.appointedAt = Math.max(0, now);
        pendingFounders.remove(SpecialPosition.SPYMASTER);
        pendingFounderCharacters.remove(SpecialPosition.SPYMASTER);
        appointments.put(SpecialPosition.SPYMASTER, appointmentCount(SpecialPosition.SPYMASTER) + 1);
        assignment.automatic = false;
        assignment.aptitude = EspionageMath.clamp(permanentAptitude, 0, 100);
        positions.put(SpecialPosition.SPYMASTER, assignment);
    }

    public long lastAppointedAt(SpecialPosition office) {
        return lastAppointments.getOrDefault(office, 0L);
    }

    /** Losing a holder to character death is not a leader's choice, so the next appointment need not wait. */
    void waiveAppointmentWait(SpecialPosition office) { lastAppointments.remove(office); }

    public Map<String, Integer> legacyAptitudes() { return Map.copyOf(appointmentAptitudes); }

    public IntelligenceReport cachedReport(String targetId, long foundedAt, long day) {
        IntelligenceReport report = reports.get(targetId);
        return report != null && report.day == day && report.targetFoundedAt == foundedAt ? report : null;
    }

    /** Drops today's report on one target so the next menu rebuilds it under the same daily rolls. */
    boolean forgetReport(String targetId) { return reports.remove(targetId) != null; }

    public void resetReportsAndRolls() {
        reports.clear();
        dailyRolls = null;
    }

    public DailyRolls rolls(long day, RandomGenerator random) {
        SpecialPositionAssignment holder = getSpymaster();
        return rolls(day, holder == null ? 0 : holder.aptitude, random);
    }

    public DailyRolls rolls(long day, int aptitude, RandomGenerator random) {
        if (dailyRolls == null || dailyRolls.day != day) {
            SpecialPositionAssignment holder = getSpymaster();
            int offense = holder == null ? 0 : holder.offenseReduction;
            int defense = holder == null ? 0 : holder.defenseReduction;
            dailyRolls = new DailyRolls(day, EspionageMath.dailyRoll(aptitude, offense, random),
                    EspionageMath.dailyRoll(aptitude, defense, random));
        }
        return dailyRolls;
    }

    public IntelligenceReport report(String targetId, long foundedAt, long day,
                                     Supplier<IntelligenceReport> create) {
        reports.entrySet().removeIf(entry -> entry.getValue().day != day);
        IntelligenceReport existing = reports.get(targetId);
        if (existing != null && existing.targetFoundedAt == foundedAt) return existing;
        IntelligenceReport report = create.get();
        report.day = day;
        report.targetFoundedAt = foundedAt;
        reports.put(targetId, report);
        return report;
    }

    public record DailyRolls(long day, int offense, int defense) {}
}
