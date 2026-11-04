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
        TaxExemptions.setPermissionCheck(owner -> false);
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
            TaxExemptions.reset();
            try {
                TaxExemptions.setPermissionCheck(earner.ownerId()::equals);
                assertTrue(policy.tax(earner, 101).isEmpty(), "an account holding the permission owes no citizen tax");
                TaxExemptions.setPermissionCheck(owner -> false);
                assertEquals(13, policy.tax(earner, 101).orElseThrow().cents(), "without the permission it is taxed");
                TaxExemptions.setPermissionCheck(earner.ownerId()::equals);
                api.when(SimpleFactionsApi::ready).thenReturn(false);
                assertThrows(IllegalStateException.class, () -> policy.tax(earner, 100), "still waits for startup");
                api.when(SimpleFactionsApi::ready).thenReturn(true);
                when(realm.getTaxRate(TaxTarget.CITIZENS, null, true)).thenReturn(0.0);
                assertEquals(0, policy.tax(earner, 101).orElseThrow().cents(), "a zero tax is unchanged");
                when(realm.getTaxRate(TaxTarget.CITIZENS, null, true)).thenReturn(12.5);
            } finally {
                TaxExemptions.reset();
                TaxExemptions.setPermissionCheck(owner -> false);
            }
            for (double rate : new double[]{-1, 101, Double.NaN}) {
                when(realm.getTaxRate(TaxTarget.CITIZENS, null, true)).thenReturn(rate);
                assertThrows(IllegalStateException.class, () -> policy.tax(earner, 100));
            }
        } finally {
            TaxExemptions.reset();
        }
    }
}
