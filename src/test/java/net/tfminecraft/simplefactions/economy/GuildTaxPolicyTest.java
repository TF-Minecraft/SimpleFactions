package net.tfminecraft.simplefactions.economy;

import java.util.UUID;
import net.tfminecraft.simplefactions.api.SimpleFactionsApi;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.tlibs.character.CharacterRef;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GuildTaxPolicyTest {
    @Test
    void taxUsesTheEarningCharacterAndItsRealmHoldingAccount() {
        CharacterRef earner = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
        GuildTaxPolicy policy = new GuildTaxPolicy();
        try (var api = mockStatic(SimpleFactionsApi.class); var factions = mockStatic(FactionManager.class)) {
            assertThrows(IllegalStateException.class, () -> policy.tax(earner, 100));
            api.when(SimpleFactionsApi::ready).thenReturn(true);
            assertTrue(policy.tax(earner, 100).isEmpty());
            Faction realm = mock(Faction.class);
            factions.when(() -> FactionManager.getByMember(earner.characterId())).thenReturn(realm);
            var handler = mock(net.tfminecraft.simplefactions.objects.handler.GuildHandler.class);
            when(realm.getId()).thenReturn("realm");
            when(realm.getGuildHandler()).thenReturn(handler);
            assertThrows(IllegalStateException.class, () -> policy.tax(earner, 100));
            assertTrue(policy.tax(earner, 0).isEmpty());
            Guild capital = mock(Guild.class);
            UUID incarnation = UUID.randomUUID();
            when(handler.getGuild("realm")).thenReturn(capital);
            assertTrue(policy.tax(earner, 100).isEmpty());
            when(capital.getBank()).thenReturn(mock(Bank.class));
            when(capital.getIncarnationId()).thenReturn(incarnation);
            when(realm.getTaxRate(TaxTarget.CITIZENS, null, true)).thenReturn(12.5);
            var tax = policy.tax(earner, 101).orElseThrow();
            assertEquals("guildtax:" + incarnation, tax.account());
            assertEquals(13, tax.cents());
            for (double rate : new double[]{-1, 101, Double.NaN}) {
                when(realm.getTaxRate(TaxTarget.CITIZENS, null, true)).thenReturn(rate);
                assertThrows(IllegalStateException.class, () -> policy.tax(earner, 100));
            }
        }
    }
}
