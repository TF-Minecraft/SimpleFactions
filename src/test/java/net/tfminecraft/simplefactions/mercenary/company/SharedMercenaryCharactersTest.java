package net.tfminecraft.simplefactions.mercenary.company;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.tlibs.character.CharacterIdentity;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterStatus;

/** The company gate reads the enrolled character's own trait through the shared provider. */
class SharedMercenaryCharactersTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID characterId = UUID.randomUUID();
    private final String key = Members.key(characterId);
    private final Map<UUID, CharacterInfo> infos = new HashMap<>();
    private final CharacterIdentity provider = mock(CharacterIdentity.class);
    private CharacterIdentity registered;
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        ServicesManager services = mock(ServicesManager.class);
        bukkit.when(Bukkit::getServicesManager).thenReturn(services);
        when(services.load(CharacterIdentity.class)).thenAnswer(call -> registered);
        when(provider.info(any())).thenAnswer(call -> Optional.ofNullable(infos.get(call.getArgument(0))));
        registered = provider;
        Members.useSharedIdentity();
        MercenaryEligibility.useSharedIdentity();
    }

    @AfterEach
    void close() {
        MercenaryEligibility.reset();
        bukkit.close();
    }

    @Test
    void providerLossLeavesOptionalGameplayOpenButANoCharacterPlayerClosed() {
        registered = null;
        infos.put(characterId, character(owner, characterId, CharacterStatus.ALIVE, "mercenary"));
        assertEquals(MercenaryEligibility.Status.UNKNOWN, MercenaryEligibility.check(key));
        assertEquals(MercenaryEligibility.Status.UNKNOWN, MercenaryEligibility.livingMercenary(key));
        assertTrue(MercenaryEligibility.canCreate(key));
        assertTrue(MercenaryEligibility.canJoin(key));

        registered = provider;
        assertEquals(MercenaryEligibility.Status.ELIGIBLE, MercenaryEligibility.check(key));
        assertFalse(MercenaryEligibility.canCreate(null), "A player without a character is not a mercenary");
        assertFalse(MercenaryEligibility.canJoin(" "));
        assertEquals(MercenaryEligibility.Status.INELIGIBLE, MercenaryEligibility.livingMercenary(null));
    }

    @ParameterizedTest
    @EnumSource(CharacterStatus.class)
    void onlyAnAliveMercenaryCanFoundJoinOrStay(CharacterStatus status) {
        infos.put(characterId, character(owner, characterId, status, "MERCENARY"));
        MercenaryEligibility.Status expected = status == CharacterStatus.ALIVE
                ? MercenaryEligibility.Status.ELIGIBLE : MercenaryEligibility.Status.INELIGIBLE;
        assertEquals(expected, MercenaryEligibility.check(key));
        assertEquals(expected, MercenaryEligibility.livingMercenary(key));
        assertEquals(status == CharacterStatus.ALIVE, MercenaryEligibility.canJoin(key));
    }

    @Test
    void twoCharactersOnOneAccountAreJudgedSeparately() {
        UUID merchantId = UUID.randomUUID();
        infos.put(characterId, character(owner, characterId, CharacterStatus.ALIVE, "mercenary"));
        infos.put(merchantId, character(owner, merchantId, CharacterStatus.ALIVE, "merchant"));
        String merchant = Members.key(merchantId);

        assertEquals(MercenaryEligibility.Status.ELIGIBLE, MercenaryEligibility.livingMercenary(key));
        assertEquals(MercenaryEligibility.Status.INELIGIBLE, MercenaryEligibility.livingMercenary(merchant));
        assertFalse(MercenaryEligibility.canJoin(merchant), "A mercenary sibling does not qualify the merchant");

        CompanyFixture fixture = new CompanyFixture(0);
        fixture.setLeader(key);
        MercenaryCompany company = new MercenaryCompany(fixture.guild, "Hired Blades", CompanyFixture.companyRegiment(), 0);
        fixture.guild.setCompany(company);
        company.getRegiment().setCurrentSlots(3);
        assertTrue(company.enlist(key));
        assertTrue(company.enlist(merchant));

        assertFalse(company.enforceCharacterGate());
        assertTrue(company.isEnlisted(key));
        assertFalse(company.isEnlisted(merchant), "Only the character without the trait is dismissed");
        assertSame(company, fixture.company());
    }

    @Test
    void unknownCharactersAndResultsAreNeverCached() {
        assertEquals(MercenaryEligibility.Status.UNKNOWN, MercenaryEligibility.livingMercenary(key));
        assertEquals(MercenaryEligibility.Status.UNKNOWN, MercenaryEligibility.check("Sigrun"),
                "A string that is not a member key cannot be read");
        infos.put(characterId, character(owner, characterId, CharacterStatus.ALIVE, "mercenary"));
        assertEquals(MercenaryEligibility.Status.ELIGIBLE, MercenaryEligibility.livingMercenary(key));
        infos.put(characterId, character(owner, characterId, CharacterStatus.ALIVE, "merchant"));
        assertEquals(MercenaryEligibility.Status.INELIGIBLE, MercenaryEligibility.livingMercenary(key));
        infos.remove(characterId);
        assertEquals(MercenaryEligibility.Status.UNKNOWN, MercenaryEligibility.livingMercenary(key));
    }

    @ParameterizedTest
    @ValueSource(strings = {"foreign-character", "missing-status", "missing-traits", "null-trait"})
    void corruptRecordsAreUnknown(String corruption) {
        CharacterInfo broken = mock(CharacterInfo.class);
        when(broken.ref()).thenReturn(new CharacterRef(owner, characterId));
        when(broken.status()).thenReturn(CharacterStatus.ALIVE);
        when(broken.traits()).thenReturn(Set.of("mercenary"));
        switch (corruption) {
            case "foreign-character" -> when(broken.ref()).thenReturn(new CharacterRef(owner, UUID.randomUUID()));
            case "missing-status" -> when(broken.status()).thenReturn(null);
            case "missing-traits" -> when(broken.traits()).thenReturn(null);
            case "null-trait" -> when(broken.traits()).thenReturn(new HashSet<>(Arrays.asList("mercenary", null)));
            default -> fail("Unknown damaged record");
        }
        infos.put(characterId, broken);
        assertEquals(MercenaryEligibility.Status.UNKNOWN, MercenaryEligibility.livingMercenary(key));
    }

    @Test
    void unknownCharactersAndProviderLossCannotDismissTheMemberOrDisbandTheCompany() {
        UUID leaderId = UUID.randomUUID();
        String leader = Members.key(leaderId);
        infos.put(leaderId, character(UUID.randomUUID(), leaderId, CharacterStatus.ALIVE, "merchant"));
        CompanyFixture fixture = new CompanyFixture(0);
        fixture.setLeader(leader);
        MercenaryCompany company = new MercenaryCompany(fixture.guild, "Hired Blades", CompanyFixture.companyRegiment(), 0);
        fixture.guild.setCompany(company);
        company.getRegiment().setCurrentSlots(2);
        assertTrue(company.enlist(key));

        assertFalse(company.enforceCharacterGate());
        assertSame(company, fixture.company());
        assertTrue(company.isEnlisted(key));
        infos.put(characterId, character(owner, characterId, CharacterStatus.DEAD, "mercenary"));
        registered = null;
        assertFalse(company.enforceCharacterGate());
        assertSame(company, fixture.company());
        assertTrue(company.isEnlisted(key));

        registered = provider;
        assertTrue(company.enforceCharacterGate(), "Only characters known to fail permit disbanding");
        assertNull(fixture.company());
    }

    private static CharacterInfo character(UUID ownerId, UUID id, CharacterStatus status, String... traits) {
        return new CharacterInfo(new CharacterRef(ownerId, id), "Canonical Mercenary", status, null, Set.of(traits), 0, null);
    }
}
