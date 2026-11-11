package net.tfminecraft.simplefactions.war.stats;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.invocation.Invocation;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.bukkit.events.MythicAuraStartEvent;
import io.lumine.mythic.bukkit.events.MythicHealMechanicEvent;
import io.lumine.mythic.core.skills.auras.Aura;
import io.lumine.mythic.core.skills.mechanics.OnJumpMechanic;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.profess.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.tfminecraft.simplefactions.database.BattleData;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.enums.BattleEndReason;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.events.BattleEndedEvent;
import net.tfminecraft.simplefactions.war.battle.persistence.BattleMapper;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterSessionEndEvent;
import net.tfminecraft.vehicleframework.events.VFEntityDamageEvent;

class BattleStatsCoverageTest {
	@TempDir
	Path dir;
	private FactionDomainFixture fixture;
	private BattleStatsListener listener;
	private MythicBattleStats mythic;
	private VehicleBattleStats vehicles;
	private BattleStatsMenu menu;
	private Player alice;
	private Player carol;
	private Player bob;
	private Player outsider;
	private Battle battle;

	@BeforeEach
	void setup() {
		fixture = new FactionDomainFixture();
		BattleManager.resetForTests();
		BattleStatsArchive.resetForTests();
		when(fixture.ui.plugin.getDataFolder()).thenReturn(dir.toFile());
		when(Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
				.thenAnswer(call -> mock(BossBar.class));
		alice = fighter("Alice");
		carol = fighter("Carol");
		bob = fighter("Bob");
		outsider = fighter("Outsider");
		listener = new BattleStatsListener();
		mythic = new MythicBattleStats(listener);
		vehicles = new VehicleBattleStats(listener);
		menu = new BattleStatsMenu();
		battle = BattleFactory.createBlank(BattleType.RAID, "Test Battle");
		Warband attackers = new Warband("atk", alice);
		attackers.addPlayer(carol);
		battle.getSideById(BattleTemplate.ATTACKER_SIDE).addBand(attackers);
		battle.getSideById(BattleTemplate.DEFENDER_SIDE).addBand(new Warband("def", bob));
		BattleManager.addBattle(battle);
		assertNull(battle.start());
	}

	@AfterEach
	void close() {
		BattleManager.resetForTests();
		BattleStatsArchive.resetForTests();
		fixture.close();
	}

	private Player fighter(String name) {
		Player player = fixture.player(name);
		when(player.getHealth()).thenReturn(20.0);
		when(player.getMaxHealth()).thenReturn(20.0);
		when(player.getMaximumNoDamageTicks()).thenReturn(20);
		return player;
	}

	private EntityDamageByEntityEvent hitEvent(Entity victim, Object damager, double damage, boolean cancelled) {
		EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
		when(event.getEntity()).thenReturn(victim);
		when(event.getDamager()).thenReturn((Entity) damager);
		when(event.getFinalDamage()).thenReturn(damage);
		when(event.isCancelled()).thenReturn(cancelled);
		return event;
	}

	private void hit(Entity victim, Object damager, double damage, boolean cancelled) {
		listener.onDamage(hitEvent(victim, damager, damage, cancelled));
	}

	private void sourceless(Entity victim, double damage) {
		sourceless(victim, damage, EntityDamageEvent.DamageCause.FIRE_TICK);
	}

	private void sourceless(Entity victim, double damage, EntityDamageEvent.DamageCause cause) {
		EntityDamageEvent event = mock(EntityDamageEvent.class);
		when(event.getEntity()).thenReturn(victim);
		when(event.getFinalDamage()).thenReturn(damage);
		when(event.getCause()).thenReturn(cause);
		listener.onSourcelessDamage(event);
	}

	private void die(Player victim, Player killer) {
		when(victim.getKiller()).thenReturn(killer);
		PlayerDeathEvent event = mock(PlayerDeathEvent.class);
		when(event.getEntity()).thenReturn(victim);
		listener.onDeath(event);
	}

	private void quit(Player player) {
		PlayerQuitEvent event = mock(PlayerQuitEvent.class);
		when(event.getPlayer()).thenReturn(player);
		listener.onQuit(event);
	}

	private void regain(Player target, double amount, EntityRegainHealthEvent.RegainReason reason) {
		EntityRegainHealthEvent event = mock(EntityRegainHealthEvent.class);
		when(event.getEntity()).thenReturn(target);
		when(event.getAmount()).thenReturn(amount);
		when(event.getRegainReason()).thenReturn(reason);
		listener.onRegain(event);
	}

	private void healEvent(Entity healer, Entity target) {
		MythicHealMechanicEvent event = mock(MythicHealMechanicEvent.class);
		when(event.getEntity()).thenReturn(healer);
		when(event.getTarget()).thenReturn(target);
		mythic.onHeal(event);
	}

	private void aura(Aura aura, String name, Entity caster, Entity target, int ticks) {
		MythicAuraStartEvent event = mock(MythicAuraStartEvent.class);
		when(event.getAura()).thenReturn(aura);
		when(event.getAuraName()).thenReturn(name);
		if (caster != null) {
			SkillCaster skillCaster = mock(SkillCaster.class);
			AbstractEntity casterEntity = mock(AbstractEntity.class);
			when(casterEntity.getBukkitEntity()).thenReturn(caster);
			when(skillCaster.getEntity()).thenReturn(casterEntity);
			when(event.getCaster()).thenReturn(skillCaster);
		}
		AbstractEntity targetEntity = mock(AbstractEntity.class);
		when(targetEntity.getBukkitEntity()).thenReturn(target);
		when(event.getTargetEntity()).thenReturn(Optional.of(targetEntity));
		when(event.getDuration()).thenReturn(ticks);
		mythic.onAura(event);
	}

	private FighterStats live(String key) {
		return battle.getStats().fighters.get(key);
	}

	private List<String> said(Player player) {
		List<String> lines = new ArrayList<>();
		for (Invocation call : mockingDetails(player).getInvocations()) {
			if (!call.getMethod().getName().equals("sendMessage") || call.getArguments().length != 1) continue;
			Object line = call.getArgument(0);
			lines.add(line instanceof Component component
					? LegacyComponentSerializer.legacySection().serialize(component)
					: String.valueOf(line));
		}
		clearInvocations(player);
		return lines;
	}

	private static boolean has(List<String> lines, String part) {
		return lines.stream().anyMatch(line -> line.contains(part));
	}

	private Inventory open(Player player) {
		return player.getOpenInventory().getTopInventory();
	}

	private String title(Player player) {
		return player.getOpenInventory().getTitle();
	}

	// The test fixture stores legacy names and lore.
	@SuppressWarnings("deprecation")
	private String text(Player player, int slot) {
		ItemStack item = open(player).getItem(slot);
		if (item == null) return null;
		return item.getItemMeta().getDisplayName() + " | " + String.join(" | ", item.getItemMeta().getLore());
	}

	private void click(Player player, int slot) {
		menu.onClick(fixture.ui.click(player, slot));
	}

	private BattleRecord end(String winner) {
		listener.onEnded(new BattleEndedEvent(battle.getId(), BattleType.RAID, null, winner, Map.of(), Set.of(),
				BattleEndReason.SIDE_WIN, false, false));
		List<BattleStatsArchive.Summary> all = BattleStatsArchive.list(null);
		return all.isEmpty() ? null : BattleStatsArchive.get(all.get(0).id());
	}

	@Test
	void hitsSplitEnemyAndFriendlyDamageAndCountBlockedHits() {
		Zombie zombie = mock(Zombie.class);
		hit(alice, zombie, 5, false);
		hit(alice, alice, 5, false);
		hit(mock(Zombie.class), alice, 5, false);
		hit(bob, outsider, 5, false);
		Arrow arrow = mock(Arrow.class);
		when(arrow.getShooter()).thenReturn(alice);
		hit(bob, arrow, 4, false);
		Arrow wild = mock(Arrow.class);
		when(wild.getShooter()).thenReturn(zombie);
		hit(bob, wild, 4, false);
		TNTPrimed tnt = mock(TNTPrimed.class);
		EntityDamageByEntityEvent blast = hitEvent(bob, tnt, 2, false);
		DamageSource source = mock(DamageSource.class);
		when(source.getCausingEntity()).thenReturn(alice);
		when(blast.getDamageSource()).thenReturn(source);
		listener.onDamage(blast);
		EntityDamageByEntityEvent unowned = hitEvent(bob, tnt, 2, false);
		when(unowned.getDamageSource()).thenReturn(mock(DamageSource.class));
		listener.onDamage(unowned);
		hit(bob, alice, 50, false);
		hit(carol, alice, 3, false);
		hit(bob, alice, 6, true);
		hit(carol, alice, 6, true);
		when(bob.getNoDamageTicks()).thenReturn(15);
		hit(bob, alice, 6, true);
		when(bob.getNoDamageTicks()).thenReturn(0);
		// VehicleFramework named the attacker but another plugin stopped the hit: still a blocked hit.
		vehicles.onVehicleDamage(new VFEntityDamageEvent(bob, alice, "ENTITY_ATTACK", 6));
		hit(bob, alice, 6, true);
		// VehicleFramework dealt the hit again: its cancelled original is not a blocked hit.
		vehicles.onVehicleDamage(new VFEntityDamageEvent(bob, alice, "ENTITY_ATTACK", 0));
		sourceless(bob, 0);
		hit(bob, alice, 6, true);
		// The same victim a tick later is a real block again.
		when(Bukkit.getCurrentTick()).thenReturn(4);
		hit(bob, alice, 6, true);
		hit(bob, alice, Double.NaN, false);
		hit(bob, alice, 0, false);
		when(bob.getHealth()).thenReturn(Double.POSITIVE_INFINITY);
		hit(bob, alice, Double.POSITIVE_INFINITY, false);

		FighterStats a = live("Alice");
		assertEquals(26, a.damage, 1e-9);
		assertEquals(3, a.friendlyDamage, 1e-9);
		assertEquals(26, live("Bob").damageTaken, 1e-9);
		assertEquals(3, live("Bob").blockedHits);
		assertEquals(0, live("Carol").blockedHits);
		assertEquals(0, live("Carol").damageTaken, 1e-9);
		assertEquals("attacker", a.side);
	}

	@Test
	void seatedAndVehicleHitsAreCreditedWhenTheyComeBackWithoutAnAttacker() {
		// A seated defender is hit; VehicleFramework cancels it and deals it again with no source.
		when(bob.isInsideVehicle()).thenReturn(true);
		listener.onSeatedHit(hitEvent(bob, alice, 4, false));
		sourceless(bob, 4);
		// A vehicle gun names its gunner first.
		vehicles.onVehicleDamage(new VFEntityDamageEvent(bob, alice, "BULLET", 3));
		sourceless(bob, 3);
		assertEquals(7, live("Alice").damage, 1e-9);
		// Friendly vehicle fire.
		vehicles.onVehicleDamage(new VFEntityDamageEvent(carol, alice, "BULLET", 2));
		sourceless(carol, 2);
		assertEquals(2, live("Alice").friendlyDamage, 1e-9);
		// The pending attacker only holds for the same tick, then the last enemy hitter takes it.
		vehicles.onVehicleDamage(new VFEntityDamageEvent(bob, alice, "BULLET", 3));
		when(Bukkit.getCurrentTick()).thenReturn(7);
		sourceless(bob, 5);
		assertEquals(12, live("Alice").damage, 1e-9);
		// Hunger, drowning, void and the like are not part of the fight.
		sourceless(bob, 5, EntityDamageEvent.DamageCause.STARVATION);
		assertEquals(12, live("Alice").damage, 1e-9);
		// Fire or a fall with nobody hitting recently goes to nobody.
		sourceless(carol, 5);
		assertEquals(0, live("Carol").damageTaken, 1e-9);
		// An outsider gunner, a dead source, non-players and battle-less states are ignored.
		vehicles.onVehicleDamage(new VFEntityDamageEvent(bob, outsider, "BULLET", 1));
		sourceless(bob, 1);
		vehicles.onVehicleDamage(new VFEntityDamageEvent(bob, null, "BULLET", 1));
		vehicles.onVehicleDamage(new VFEntityDamageEvent(bob, bob, "BULLET", 1));
		vehicles.onVehicleDamage(new VFEntityDamageEvent(mock(Zombie.class), alice, "BULLET", 1));
		listener.onSeatedHit(hitEvent(bob, mock(Zombie.class), 1, false));
		listener.onSeatedHit(hitEvent(carol, alice, 1, false));
		listener.onSeatedHit(hitEvent(mock(Zombie.class), alice, 1, false));
		listener.onSourcelessDamage(hitEvent(bob, alice, 1, false));
		sourceless(mock(Zombie.class), 1);
		sourceless(outsider, 1);
		assertEquals(12, live("Alice").damage, 1e-9);
		BattleManager.resetForTests();
		vehicles.onVehicleDamage(new VFEntityDamageEvent(bob, alice, "BULLET", 1));
		listener.onSeatedHit(hitEvent(bob, alice, 1, false));
		sourceless(bob, 1);
		regain(bob, 1, EntityRegainHealthEvent.RegainReason.MAGIC);
		healEvent(alice, carol);
		aura(mock(Aura.class), "Slowed", alice, bob, 20);
	}

	@Test
	void deathsCreditTheKillerOrTheLastEnemyHitWithAssistsAndTeamKills() {
		die(outsider, null);
		hit(bob, carol, 2, false);
		die(bob, alice);
		hit(carol, bob, 1, false);
		die(carol, alice);
		hit(bob, alice, 2, false);
		die(bob, null);
		die(alice, null);
		die(bob, outsider);
		hit(carol, bob, 1, false);
		battle.getStats().takeHitters(carol.getUniqueId(), System.currentTimeMillis() + 60_000);
		die(carol, null);
		die(bob, bob);
		Battle idle = BattleFactory.createBlank(BattleType.RAID, "idle");
		BattleManager.get().add(0, idle);
		quit(outsider);
		quit(carol);
		Player nameless = fixture.player("Nameless");
		when(nameless.getName()).thenReturn(null);
		quit(nameless);
		CharacterSession session = net.tfminecraft.simplefactions.identity.Members.session(carol).orElseThrow();
		listener.onSessionEnd(new CharacterSessionEndEvent(carol, session, CharacterSessionEndEvent.Reason.SWITCH));
		listener.onSessionEnd(new CharacterSessionEndEvent(carol, session, CharacterSessionEndEvent.Reason.QUIT));
		CharacterRef stranger = new CharacterRef(carol.getUniqueId(), java.util.UUID.randomUUID());
		listener.onSessionEnd(new CharacterSessionEndEvent(carol, new CharacterSession(stranger, 9L),
				CharacterSessionEndEvent.Reason.SWITCH));

		assertEquals(2, live("Alice").kills);
		assertEquals(1, live("Alice").teamKills);
		assertEquals(1, live("Carol").assists);
		assertEquals(0, live("Alice").assists);
		assertEquals(0, live("Bob").assists);
		assertEquals(4, live("Bob").deaths);
		assertEquals(2, live("Carol").deaths);
		assertEquals(2, live("Carol").logouts);
		assertNull(battle.getStats().fighter(alice, null));
		assertNull(battle.getStats().lastHitter(bob.getUniqueId(), System.currentTimeMillis()));
	}

	@Test
	void everyoneInTheBattleAtTheStartIsAFighterAndASwitchForgetsTheOldCharactersHits() {
		listener.onStarted(new net.tfminecraft.simplefactions.war.battle.events.BattleStartedEvent(
				battle.getId(), BattleType.RAID, null, Set.of(), Set.of()));
		assertEquals(Set.of("Alice", "Carol", "Bob"), battle.getStats().fighters.keySet());
		listener.onStarted(new net.tfminecraft.simplefactions.war.battle.events.BattleStartedEvent(
				"missing", BattleType.RAID, null, Set.of(), Set.of()));
		hit(bob, alice, 2, false);
		aura(mock(Aura.class), "Slowed", alice, bob, 60);
		CharacterSession session = net.tfminecraft.simplefactions.identity.Members.session(bob).orElseThrow();
		Battle idle = new Battle("idle");
		BattleManager.get().add(0, idle);
		listener.onSessionEnd(new CharacterSessionEndEvent(bob, session, CharacterSessionEndEvent.Reason.SWITCH));
		die(bob, null);
		assertEquals(0, live("Alice").kills);
		// The old Slowed was forgotten, so a fresh second counts in full.
		assertEquals(1.0, battle.getStats().creditControl(bob.getUniqueId(), "slow", System.currentTimeMillis(), 1000), 0.01);
		battle.takeStats();
		listener.onStarted(new net.tfminecraft.simplefactions.war.battle.events.BattleStartedEvent(
				battle.getId(), BattleType.RAID, null, Set.of(), Set.of()));
		assertNull(battle.getStats());
	}

	@Test
	void skillHealsGoToTheHealerAndOtherMagicHealsAreSelfHealing() {
		when(carol.getHealth()).thenReturn(12.0);
		healEvent(alice, carol);
		regain(carol, 5, EntityRegainHealthEvent.RegainReason.CUSTOM);
		assertEquals(5, live("Alice").healing, 1e-9);
		// Capped at what the target was missing.
		healEvent(alice, carol);
		regain(carol, 50, EntityRegainHealthEvent.RegainReason.CUSTOM);
		assertEquals(13, live("Alice").healing, 1e-9);
		// Lifesteal and potions: self-healing.
		regain(carol, 2, EntityRegainHealthEvent.RegainReason.MAGIC);
		assertEquals(2, live("Carol").selfHealing, 1e-9);
		healEvent(carol, carol);
		regain(carol, 1, EntityRegainHealthEvent.RegainReason.CUSTOM);
		assertEquals(3, live("Carol").selfHealing, 1e-9);
		// Healing an enemy counts for nobody; a stale heal from another tick is self-healing.
		when(bob.getHealth()).thenReturn(10.0);
		healEvent(alice, bob);
		regain(bob, 4, EntityRegainHealthEvent.RegainReason.CUSTOM);
		assertEquals(13, live("Alice").healing, 1e-9);
		healEvent(alice, carol);
		when(Bukkit.getCurrentTick()).thenReturn(3);
		regain(carol, 1, EntityRegainHealthEvent.RegainReason.CUSTOM);
		assertEquals(4, live("Carol").selfHealing, 1e-9);
		// Food, full health, outsiders, non-player heals and non-player healers are ignored.
		regain(carol, 1, EntityRegainHealthEvent.RegainReason.SATIATED);
		when(carol.getHealth()).thenReturn(20.0);
		regain(carol, 1, EntityRegainHealthEvent.RegainReason.MAGIC);
		regain(outsider, 1, EntityRegainHealthEvent.RegainReason.MAGIC);
		EntityRegainHealthEvent mob = mock(EntityRegainHealthEvent.class);
		when(mob.getRegainReason()).thenReturn(EntityRegainHealthEvent.RegainReason.MAGIC);
		when(mob.getEntity()).thenReturn(mock(Zombie.class));
		listener.onRegain(mob);
		healEvent(mock(Zombie.class), carol);
		healEvent(alice, mock(Zombie.class));
		assertEquals(4, live("Carol").selfHealing, 1e-9);
	}

	@Test
	void crowdControlCountsOnlyNewControlTimePerKind() {
		Aura slow = mock(Aura.class);
		aura(slow, "Slowed", alice, bob, 60);
		aura(slow, "Slowed", alice, bob, 60);
		aura(mock(OnJumpMechanic.class), "3f2a-uuid", alice, bob, 40);
		aura(slow, "Frozen", alice, bob, 40);
		aura(slow, "Silenced", carol, alice, 20);
		aura(slow, "Slowed", alice, alice, 60);
		aura(slow, "SomeBuff", alice, bob, 60);
		aura(slow, "Slowed", null, bob, 60);
		aura(slow, "Slowed", alice, mock(Zombie.class), 60);
		aura(slow, "Slowed", outsider, bob, 60);
		aura(slow, "Slowed", mock(Zombie.class), bob, 60);
		aura(slow, null, alice, bob, 60);
		// A support spell's jump lock on an ally is not crowd control.
		aura(mock(OnJumpMechanic.class), "3f2a-uuid", alice, carol, 40);
		// One application earns at most 30 seconds, however long the aura.
		aura(slow, "Weakened", alice, bob, 20 * 600);
		assertEquals(35, live("Alice").cc, 0.05);
		assertEquals(35, live("Bob").ccTaken, 0.05);
		assertEquals(0, live("Alice").friendlyCc, 0.05);
		assertEquals(1, live("Carol").friendlyCc, 0.05);
		assertEquals(0, battle.getStats().creditControl(bob.getUniqueId(), "slow", 0, -5), 1e-9);
		listener.control(alice, alice, "slow", 1000);
	}

	@Test
	void finishArchivesOnceAndPointsFightersToTheirStats() {
		hit(bob, alice, 9, false);
		die(bob, alice);
		hit(carol, bob, 4, false);
		quit(carol);
		listener.onEnded(new BattleEndedEvent("missing", BattleType.RAID, null, null, Map.of(), Set.of(),
				BattleEndReason.TIMER, false, false));
		BattleRecord record = end("attacker");
		assertNotNull(record);
		assertNull(battle.getStats());
		assertNull(end("attacker").fighter("Nobody"));
		assertEquals(1, BattleStatsArchive.list(null).size());
		assertTrue(record.id.startsWith("test_battle-"));
		assertEquals(3, record.fighters.size());
		List<String> lines = said(alice);
		assertTrue(has(lines, "Test Battle §6is over"), lines.toString());
		assertTrue(has(said(bob), "/battle stats"));
		assertTrue(Files.exists(dir.resolve(BattleStatsArchive.FOLDER).resolve(record.id + ".json")));
	}

	@Test
	void liveStatsSurviveTheBattleSave() {
		hit(bob, alice, 7, false);
		BattleData data = BattleMapper.toData(battle);
		String json = JsonUtil.GSON.toJson(data);
		Battle loaded = BattleMapper.fromData(JsonUtil.GSON.fromJson(json, BattleData.class));
		assertEquals(7, loaded.getStats().fighters.get("Alice").damage, 1e-9);
		assertTrue(loaded.getStats().takeHitters(bob.getUniqueId(), 0).isEmpty());
		Battle fresh = new Battle("fresh");
		assertNull(fresh.getStats());
		assertNotNull(fresh.stats());
		assertSame(fresh.stats(), fresh.takeStats());
		assertNull(BattleStatsListener.finish(fresh, null, "TIMER"));
	}

	@Test
	void recordsCarryTheWarAndFallBackWhenItsLeaderIsMissing() {
		War war = mock(War.class);
		when(war.getId()).thenReturn(4);
		when(war.getStartedAt()).thenReturn(Instant.ofEpochSecond(1000));
		when(war.getName()).thenReturn("North vs. South");
		WarManager.get().add(war);
		battle.setWarId(4);
		battle.setNavalVariant(true);
		BattleRecord record = BattleRecord.of(battle, new BattleStats(), " ", "TIMER", Instant.ofEpochMilli(5));
		assertEquals("w4-1000", record.warKey);
		assertEquals("North vs. South", record.warName);
		assertNull(record.winner);

		when(war.getStartedAt()).thenReturn(null);
		when(war.getName()).thenThrow(new NullPointerException("no leader"));
		battle.setStartedAt(null);
		battle.setBattleType(null);
		record = BattleRecord.of(battle, new BattleStats(), null, null, Instant.ofEpochMilli(5));
		assertEquals("w4-0", record.warKey);
		assertEquals("War 4", record.warName);
		assertEquals(5, record.startedAt);
		battle.setWarId(99);
		assertNull(BattleRecord.of(battle, new BattleStats(), null, null, Instant.now()).warKey);
		assertEquals("battle", BattleRecord.safeId(null));
		assertEquals("a_b-1", BattleRecord.safeId("A b-1"));
	}

	@Test
	void fighterClassComesFromMmoCoreWithoutColourCodes() {
		assertNull(FighterClass.of(alice));
		when(Bukkit.getPluginManager().isPluginEnabled("MMOCore")).thenReturn(true);
		try (MockedStatic<PlayerData> mmo = mockStatic(PlayerData.class)) {
			PlayerData data = mock(PlayerData.class);
			PlayerClass mage = mock(PlayerClass.class);
			when(mage.getName()).thenReturn("§x§a§a§5§5§5§5§lMa§Lge");
			when(data.getProfess()).thenReturn(mage);
			mmo.when(() -> PlayerData.get(alice)).thenReturn(data);
			mmo.when(() -> PlayerData.get(bob)).thenReturn(null);
			mmo.when(() -> PlayerData.get(carol)).thenThrow(new IllegalStateException("not loaded"));
			assertEquals("Mage", FighterClass.of(alice));
			assertNull(FighterClass.of(bob));
			assertNull(FighterClass.of(carol));
		}
	}

	@Test
	void archiveLoadsSkipsBadFilesAndReadsFromDisk() throws Exception {
		Logger logger = mock(Logger.class);
		when(fixture.ui.plugin.getLogger()).thenReturn(logger);
		BattleStatsArchive.loadNow();
		hit(bob, alice, 1, false);
		BattleRecord record = end(null);
		File folder = dir.resolve(BattleStatsArchive.FOLDER).toFile();
		Files.writeString(folder.toPath().resolve("broken.json"), "{not json");
		Files.writeString(folder.toPath().resolve("empty.json"), "null");
		Files.writeString(folder.toPath().resolve("notes.txt"), "ignored");

		BattleStatsArchive.resetForTests();
		BattleStatsArchive.loadAsync();
		fixture.ui.runAsyncTasks();
		BattleStatsArchive.loadNow();
		assertEquals(1, BattleStatsArchive.list(null).size());
		assertEquals(1, BattleStatsArchive.list("Alice").size());
		assertTrue(BattleStatsArchive.list("Nobody").isEmpty());
		assertEquals(record.id, BattleStatsArchive.get(record.id).id);
		assertSame(BattleStatsArchive.get(record.id), BattleStatsArchive.get(record.id));
		assertNull(BattleStatsArchive.get(null));
		assertNull(BattleStatsArchive.get("../escape"));
		assertNull(BattleStatsArchive.get("unknown"));

		BattleStatsArchive.resetForTests();
		BattleStatsArchive.loadNow();
		Files.writeString(folder.toPath().resolve(record.id + ".json"), "null");
		assertNull(BattleStatsArchive.get(record.id));
		Files.delete(folder.toPath().resolve(record.id + ".json"));
		assertNull(BattleStatsArchive.get(record.id));

		Files.delete(folder.toPath().resolve("broken.json"));
		Files.delete(folder.toPath().resolve("empty.json"));
		Files.delete(folder.toPath().resolve("notes.txt"));
		Files.delete(folder.toPath());
		Files.writeString(folder.toPath(), "a file where the folder should be");
		BattleStatsArchive.save(record);
		when(fixture.ui.plugin.getDataFolder()).thenReturn(null);
		BattleStatsArchive.save(record);
		BattleStatsArchive.loadNow();
		verify(logger, atLeast(3)).log(eq(java.util.logging.Level.WARNING), anyString(), any(Exception.class));
	}

	@Test
	void menusShowYourRecordBattlesWarsAndReportsWithRanks() {
		War war = mock(War.class);
		when(war.getId()).thenReturn(2);
		when(war.getName()).thenReturn("Red vs. Blue");
		WarManager.get().add(war);
		battle.setWarId(2);
		hit(bob, alice, 9, false);
		when(carol.getHealth()).thenReturn(10.0);
		healEvent(alice, carol);
		regain(carol, 4, EntityRegainHealthEvent.RegainReason.CUSTOM);
		aura(mock(Aura.class), "Slowed", alice, bob, 60);
		die(bob, alice);
		BattleRecord first = end("attacker");
		try {
			Thread.sleep(5);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
		assertNull(battle.start());
		battle.setWarId(null);
		hit(alice, bob, 2, false);
		BattleRecord second = BattleStatsListener.finish(battle, null, "WAR_ENDED");
		said(alice);

		Player nameless = fixture.player("Nameless");
		when(nameless.getName()).thenReturn(null);
		BattleStatsView.command(nameless, new String[] {"stats"});
		assertTrue(has(said(nameless), "Choose a character"));
		// A menu left open across a character switch: no character, no battles.
		BattleStatsMenu.home(nameless, 1);
		BattleStatsMenu.wars(nameless, 1);
		BattleStatsMenu.war(nameless, first.warKey);
		assertEquals(3, said(nameless).stream().filter(line -> line.contains("Choose a character")).count());

		org.bukkit.command.Command battleCommand = mock(org.bukkit.command.Command.class);
		when(battleCommand.getName()).thenReturn("battle");
		assertTrue(new net.tfminecraft.simplefactions.war.battle.ui.BattleCommandManager()
				.onCommand(outsider, battleCommand, "battle", new String[] {"stats"}));
		assertEquals("§8Your battle stats", title(outsider));
		assertTrue(text(outsider, 22).contains("No battles yet"));
		assertTrue(text(outsider, 4).contains("Battles: §f0"));
		BattleStatsView.command(outsider, new String[] {"stats", first.id});
		assertTrue(has(said(outsider), "No battle stats with that ID"));
		BattleStatsView.command(outsider, new String[] {"stats", "war"});
		assertTrue(text(outsider, 22).contains("No war battles yet"));
		BattleStatsView.command(outsider, new String[] {"stats", "war", first.warKey});
		assertTrue(has(said(outsider), "No battle stats for that war"));

		BattleStatsView.command(alice, new String[] {"stats"});
		assertEquals("§8Your battle stats", title(alice));
		String record = text(alice, 4);
		assertTrue(record.contains("Battles: §f2 §8(§a1 won§8, §c0 lost§8, §71 no result§8)"), record);
		assertTrue(record.contains("Kills §e1"), record);
		assertTrue(record.contains("Healing §a4"), record);
		assertTrue(text(alice, 9).contains("No winner") && text(alice, 10).contains("Won by §eattacker"));
		assertEquals(org.bukkit.Material.GOLDEN_SWORD, open(alice).getItem(10).getType());
		assertNull(open(alice).getItem(BattleStatsMenu.NEXT_SLOT));

		click(alice, 10);
		assertEquals("§8Battle report", title(alice));
		assertTrue(text(alice, 4).contains("War: §fRed vs. Blue"));
		assertTrue(text(alice, 20).contains("fighters"), text(alice, 20));
		String you = text(alice, 31);
		assertTrue(you.contains("Kills §e1 §7(#1 of 3)"), you);
		assertTrue(you.contains("Healing §a4 §7(#1 of 3)"), you);
		assertTrue(you.contains("Crowd control §b3s §7(#1 of 3)"), you);
		assertNull(open(alice).getItem(40));
		click(alice, BattleStatsMenu.BACK_SLOT);
		assertEquals("§8Your battle stats", title(alice));
		click(alice, 8);
		assertEquals("§8Wars", title(alice));
		assertTrue(text(alice, 9).contains("Red vs. Blue"));
		click(alice, 9);
		assertEquals("§8War report", title(alice));
		assertTrue(text(alice, 4).contains("Won by §eattacker§7: §f1"));
		assertTrue(text(alice, 13).contains("Kills §e1 §7(#1 of 3)"));
		click(alice, 18);
		assertEquals("§8Battle report", title(alice));
		click(alice, 0);
		menu.onClick(fixture.ui.click(alice, -1));
		BattleStatsView.command(alice, new String[] {"stats", "war"});
		click(alice, BattleStatsMenu.BACK_SLOT);
		assertEquals("§8Your battle stats", title(alice));
		BattleStatsView.command(alice, new String[] {"stats", second.id.toUpperCase()});
		assertTrue(text(alice, 4).contains("No winner §8(the war ended)"));
		BattleStatsView.command(alice, new String[] {"stats", "5"});
		assertEquals("§8Your battle stats", title(alice));

		// A battle of the same war that Bob did not fight still counts in the war's totals, but stays closed to him.
		BattleRecord other = new BattleRecord();
		other.id = "other-war-battle";
		other.name = "Other";
		other.warKey = first.warKey;
		other.warName = first.warName;
		other.winner = "defender";
		other.endedAt = first.endedAt - 1;
		FighterStats stranger = new FighterStats();
		stranger.key = "Stranger";
		stranger.name = "Stranger";
		stranger.side = "defender";
		stranger.kills = 5;
		other.fighters.add(stranger);
		BattleStatsArchive.save(other);
		BattleStatsView.command(bob, new String[] {"stats", "war", first.warKey});
		assertTrue(text(bob, 4).contains("§72 battles"), text(bob, 4));
		assertTrue(text(bob, 13).contains("(#3 of 4)"), text(bob, 13));
		assertTrue(text(bob, 19).contains("You did not fight here"), text(bob, 19));
		said(bob);
		click(bob, 19);
		assertTrue(said(bob).isEmpty());

		when(outsider.hasPermission("warbands.admin")).thenReturn(true);
		BattleStatsView.command(outsider, new String[] {"stats", first.id});
		String staff = text(outsider, 40);
		assertTrue(staff.contains("No class §73 §e100% §a100%"), staff);
		assertNull(open(outsider).getItem(31));
		click(outsider, 40);
		List<String> table = said(outsider);
		assertTrue(has(table, "§fAlice §7attacker §e1/0/0/0 §f9 §a4 §b3"), table.toString());
		BattleStatsView.command(outsider, new String[] {"stats", first.id, "table"});
		assertTrue(has(said(outsider), "Classes"));
		BattleStatsView.command(outsider, new String[] {"stats"});
		assertEquals("§8Battle stats (all battles)", title(outsider));
		BattleStatsView.command(outsider, new String[] {"stats", "war", first.warKey});
		assertNull(open(outsider).getItem(13));

		// The staff button only reaches a readable record; a vanished file says so.
		BattleStatsView.command(alice, new String[] {"stats"});
		BattleStatsArchive.resetForTests();
		click(alice, 9);
		assertTrue(has(said(alice), "could not be read"));
	}

	@Test
	void menusPageAndIgnoreDragsAndOtherInventories() {
		when(alice.hasPermission("warbands.admin")).thenReturn(true);
		for (int i = 0; i < BattleStatsMenu.PER_PAGE + 1; i++) {
			BattleRecord extra = new BattleRecord();
			extra.id = "extra-" + i;
			extra.name = "Extra " + i;
			extra.warKey = "w" + i + "-0";
			extra.warName = "War " + i;
			extra.endedAt = i;
			BattleStatsArchive.save(extra);
		}
		BattleRecord roled = new BattleRecord();
		roled.id = "roled";
		roled.name = "Roled";
		roled.warKey = "wr-1";
		roled.warName = "Role war";
		roled.winner = "attacker";
		BattleRecord.SideRecord roleSide = new BattleRecord.SideRecord();
		roleSide.id = "attacker";
		roleSide.warRole = "DEFENDER";
		roled.sides.add(roleSide);
		BattleRecord.SideRecord unnamed = new BattleRecord.SideRecord();
		roled.sides.add(0, unnamed);
		BattleStatsArchive.save(roled);
		BattleStatsView.command(alice, new String[] {"stats", "war", "wr-1"});
		assertTrue(text(alice, 4).contains("Won by §ethe war's defenders§7: §f1"), text(alice, 4));
		roleSide.warRole = "ATTACKER";
		assertEquals("the war's attackers", BattleStatsMenu.winnerLabel(new BattleStatsArchive.Summary("x", "X", 0, null, null,
				"attacker", roled.winnerRole(), Map.of())));
		BattleRecord twin = new BattleRecord();
		twin.id = "extra-0";
		BattleStatsArchive.save(twin);
		assertEquals("extra-0-2", twin.id);
		BattleStatsView.command(alice, new String[] {"stats"});
		assertNotNull(open(alice).getItem(BattleStatsMenu.NEXT_SLOT));
		click(alice, BattleStatsMenu.NEXT_SLOT);
		assertNotNull(open(alice).getItem(BattleStatsMenu.BACK_SLOT));
		click(alice, BattleStatsMenu.BACK_SLOT);
		assertNull(open(alice).getItem(BattleStatsMenu.BACK_SLOT));
		BattleStatsView.command(alice, new String[] {"stats", "war"});
		click(alice, BattleStatsMenu.NEXT_SLOT);
		assertNotNull(open(alice).getItem(9));

		var menuView = alice.getOpenInventory();
		assertSame(open(alice), ((BattleStatsMenu.Holder) open(alice).getHolder()).getInventory());
		InventoryDragEvent drag = mock(InventoryDragEvent.class);
		when(drag.getView()).thenReturn(menuView);
		menu.onDrag(drag);
		verify(drag).setCancelled(true);
		alice.closeInventory();
		var plainView = alice.getOpenInventory();
		InventoryDragEvent other = mock(InventoryDragEvent.class);
		when(other.getView()).thenReturn(plainView);
		menu.onDrag(other);
		verify(other, never()).setCancelled(true);
		var outside = fixture.ui.click(alice, 3);
		menu.onClick(outside);
		assertFalse(outside.isCancelled());

		BattleRecord many = new BattleRecord();
		many.id = "many";
		many.name = "Many";
		many.type = "FIELD";
		many.naval = true;
		many.raid = true;
		many.endReason = "RETREAT";
		for (String id : List.of("attacker", "defender", "third")) {
			BattleRecord.SideRecord side = new BattleRecord.SideRecord();
			side.id = id;
			many.sides.add(side);
		}
		FighterStats f = new FighterStats();
		f.key = "Alice";
		f.name = "Alice";
		f.side = "third";
		f.faction = "Aldmark";
		f.className = "Mage";
		f.blockedHits = 2;
		f.logouts = 1;
		many.fighters.add(f);
		BattleStatsArchive.save(many);
		BattleStatsView.command(alice, new String[] {"stats", "many"});
		assertTrue(text(alice, 4).contains("Naval field raid"));
		assertTrue(text(alice, 4).contains("retreat"));
		assertTrue(text(alice, 21).contains("(Aldmark"), text(alice, 21));
		assertEquals(org.bukkit.Material.WHITE_BANNER, open(alice).getItem(21).getType());
		BattleStatsView.fighterTable(alice, many);
		List<String> lines = said(alice);
		assertTrue(has(lines, "blocked 2") && has(lines, "logouts 1") && has(lines, "Mage §71 §e0% §a0%"), lines.toString());
		assertArrayEquals(new int[] {19, 20, 21, 22, 23, 24, 25}, BattleStatsMenu.sideSlots(9));
	}

	@Test
	void unfinishedBattlesAreArchivedWithoutEverBlockingTheirRemoval() {
		BattleStatsListener.archiveUnfinished(null, "WAR_ENDED");
		BattleStatsListener.archiveUnfinished(new Battle("idle"), "WAR_ENDED");
		assertTrue(BattleStatsArchive.list(null).isEmpty());
		hit(bob, alice, 3, false);
		BattleStatsListener.archiveUnfinished(battle, "RAID_CLEARED");
		assertEquals(1, BattleStatsArchive.list(null).size());
		assertTrue(has(said(alice), "is over"));
		Battle broken = mock(Battle.class);
		when(broken.hasStarted()).thenReturn(true);
		when(broken.getId()).thenReturn("broken");
		when(broken.takeStats()).thenThrow(new IllegalStateException("boom"));
		assertDoesNotThrow(() -> BattleStatsListener.archiveUnfinished(broken, "WAR_ENDED"));
	}

	@Test
	void formatsDurationsReasonsAndRanks() {
		assertEquals("0m", BattleStatsView.duration(-5));
		assertEquals("59m", BattleStatsView.duration(59 * 60_000L));
		assertEquals("1h 57m", BattleStatsView.duration(117 * 60_000L));
		assertEquals("side won", BattleStatsView.reason("SIDE_WIN"));
		assertEquals("time ran out", BattleStatsView.reason("TIMER"));
		assertEquals("control of the field", BattleStatsView.reason("CONTROL"));
		assertEquals("the raid was called off", BattleStatsView.reason("RAID_CLEARED"));
		assertEquals("ended", BattleStatsView.reason(null));
		assertEquals("Battle", BattleStatsView.kind(new BattleRecord()));
		FighterStats low = new FighterStats();
		FighterStats high = new FighterStats();
		high.kills = 3;
		FighterStats tie = new FighterStats();
		tie.kills = 3;
		List<FighterStats> all = List.of(low, high, tie);
		assertEquals(3, BattleStatsView.rank(all, low, f -> f.kills));
		assertEquals(1, BattleStatsView.rank(all, tie, f -> f.kills));
		assertEquals("0%", BattleStatsView.percent(1, 0));
	}
}
