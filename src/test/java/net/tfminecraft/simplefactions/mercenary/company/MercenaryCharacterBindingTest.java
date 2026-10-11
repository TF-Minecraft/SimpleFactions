package net.tfminecraft.simplefactions.mercenary.company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.database.MercenaryCompanyData;
import net.tfminecraft.simplefactions.mercenary.contract.ContractFixture;

/** Companies follow the character someone signed up as, not their active one. */
class MercenaryCharacterBindingTest {
    private Characters characters;
    private CompanyFixture fixture;

    @BeforeEach
    void setUp() {
        CompanyFixture.installCompanyUpgrades();
        CompanyFixture.installMercenaryPrototype();
        characters = new Characters();
        MercenaryEligibility.setProbe(characters);
        fixture = new CompanyFixture(500.0);
    }

    @AfterEach
    void tearDown() {
        MercenaryEligibility.reset();
        CompanyFixture.clearRegiments();
        CompanyFixture.clearCompanyUpgrades();
        ContractFixture.tearDown();
    }

    @Test
    void theFounderSwitchingCharacterKeepsTheCompany() {
        characters.give("Ivar", "sweyn", true);
        characters.give("Ivar", "warrior", false);
        characters.play("Ivar", "sweyn");
        assertTrue(MercenaryCompanyService.requestFormation(fixture.guild, "Ivar", "Varangians").ok());
        MercenaryCompany company = fixture.company();
        assertEquals("sweyn", company.getCharacter("Ivar"));

        characters.play("Ivar", "warrior");
        company.tick();

        assertSame(company, fixture.company());
        assertEquals("sweyn", company.getCharacter("Ivar"));
    }

    @Test
    void theFoundingCharacterLosingTheTraitDisbands() {
        characters.give("Ivar", "sweyn", true);
        characters.give("Ivar", "warrior", false);
        characters.play("Ivar", "sweyn");
        MercenaryCompany company = formed();
        company.enlistLeader();
        characters.play("Ivar", "warrior");

        characters.mercenaries.remove("sweyn");
        company.tick();

        assertNull(fixture.company());
        assertNull(company.getCharacter("Ivar"));
    }

    @Test
    void aMercenarySwitchingCharacterStaysUntilThatCharacterFails() {
        characters.give("Ivar", "sweyn", true);
        characters.play("Ivar", "sweyn");
        characters.give("Sigrun", "astrid", true);
        characters.give("Sigrun", "baker", false);
        characters.play("Sigrun", "astrid");
        MercenaryCompany company = formed();
        company.getRegiment().setCurrentSlots(2);
        assertTrue(company.enlist("Sigrun"));

        characters.play("Sigrun", "baker");
        company.tick();
        assertTrue(company.isEnlisted("Sigrun"));
        assertEquals("astrid", company.getCharacter("Sigrun"));

        characters.mercenaries.remove("astrid");
        company.tick();
        assertFalse(company.isEnlisted("Sigrun"));
        assertNull(company.getCharacter("Sigrun"));
        assertSame(company, fixture.company());
    }

    @Test
    void aCompanyWithNothingRememberedPicksUpTheMercenaryCharacter() {
        characters.give("Ivar", "warrior", false);
        characters.give("Ivar", "sweyn", true);
        characters.play("Ivar", "warrior");
        MercenaryCompany company = formed();

        company.tick();

        assertSame(company, fixture.company());
        assertEquals("sweyn", company.getCharacter("Ivar"));
    }

    @Test
    void aLeaderWithNoMercenaryCharacterStillDisbands() {
        characters.give("Ivar", "warrior", false);
        characters.play("Ivar", "warrior");
        MercenaryCompany company = formed();

        company.tick();

        assertNull(fixture.company());
    }

    @Test
    void offlinePlayersAreRememberedOnceTheyLogIn() {
        characters.give("Ivar", "sweyn", true);
        MercenaryCompany company = formed();

        company.tick();
        assertNull(company.getCharacter("Ivar"));

        characters.play("Ivar", "sweyn");
        company.tick();
        assertEquals("sweyn", company.getCharacter("Ivar"));
    }

    @Test
    void aFormerLeaderWhoDoesNotServeIsForgotten() {
        characters.give("Ivar", "sweyn", true);
        characters.play("Ivar", "sweyn");
        characters.give("Harald", "hardrada", true);
        characters.play("Harald", "hardrada");
        MercenaryCompany company = formed();
        company.tick();
        assertEquals("sweyn", company.getCharacter("Ivar"));

        fixture.setLeader("Harald");
        company.tick();

        assertNull(company.getCharacter("Ivar"));
        assertEquals("hardrada", company.getCharacter("Harald"));
    }

    @Test
    void kickingTheLeaderKeepsTheirCharacter() {
        characters.give("Ivar", "sweyn", true);
        characters.play("Ivar", "sweyn");
        MercenaryCompany company = formed();
        company.enlistLeader();

        assertTrue(company.kick("Ivar"));

        assertEquals("sweyn", company.getCharacter("Ivar"));
    }

    @Test
    void rememberedCharactersSurviveASave() {
        characters.give("Ivar", "sweyn", true);
        characters.play("Ivar", "sweyn");
        MercenaryCompany company = formed();
        company.enlistLeader();

        MercenaryCompanyData data = company.serialize();
        assertEquals(Map.of("Ivar", "sweyn"), data.characters);
        Map<String, String> saved = new LinkedHashMap<>(data.characters);
        saved.put("Ghost", null);
        saved.put(null, "nobody");
        data.characters = saved;
        MercenaryCompany loaded = new MercenaryCompany(fixture.guild, data, CompanyFixture.companyRegiment());
        assertEquals("sweyn", loaded.getCharacter("ivar"));
        assertNull(loaded.getCharacter("Ghost"));
        assertNull(loaded.getCharacter(null));

        data.characters = null;
        assertNull(new MercenaryCompany(fixture.guild, data, CompanyFixture.companyRegiment())
                .getCharacter("Ivar"));
    }

    @Test
    void probesWithoutCharactersJudgeTheActiveOne() {
        MercenaryEligibility.Probe active = player -> MercenaryEligibility.Status.INELIGIBLE;
        MercenaryEligibility.setProbe(active);

        assertEquals(MercenaryEligibility.Status.INELIGIBLE, MercenaryEligibility.check("Ivar", "sweyn"));
        assertNull(MercenaryEligibility.mercenaryCharacter("Ivar"));
        assertNull(MercenaryEligibility.mercenaryCharacter(null));
        assertNull(MercenaryEligibility.mercenaryCharacter(" "));
        assertEquals(MercenaryEligibility.Status.INELIGIBLE, MercenaryEligibility.check(" ", "sweyn"));
    }

    private MercenaryCompany formed() {
        MercenaryCompany company = new MercenaryCompany(
                fixture.guild, "Varangians", CompanyFixture.companyRegiment(), 0);
        fixture.guild.setCompany(company);
        return company;
    }

    /** Online players, the characters each owns, and which of those are mercenaries. */
    private static final class Characters implements MercenaryEligibility.Probe {
        final Map<String, String> active = new HashMap<>();
        final Map<String, List<String>> owned = new HashMap<>();
        final Set<String> mercenaries = new HashSet<>();

        void give(String player, String character, boolean mercenary) {
            owned.computeIfAbsent(player, p -> new ArrayList<>()).add(character);
            if (mercenary) mercenaries.add(character);
        }

        void play(String player, String character) {
            active.put(player, character);
        }

        @Override
        public MercenaryEligibility.Status check(String player) {
            return checkCharacter(player, active.get(player));
        }

        @Override
        public MercenaryEligibility.Status checkCharacter(String player, String character) {
            if (!active.containsKey(player)) return MercenaryEligibility.Status.UNKNOWN;
            return mercenaries.contains(character)
                    ? MercenaryEligibility.Status.ELIGIBLE
                    : MercenaryEligibility.Status.INELIGIBLE;
        }

        @Override
        public String mercenaryCharacter(String player) {
            if (!active.containsKey(player)) return null;
            if (mercenaries.contains(active.get(player))) return active.get(player);
            for (String character : owned.getOrDefault(player, List.of())) {
                if (mercenaries.contains(character)) return character;
            }
            return null;
        }
    }
}
