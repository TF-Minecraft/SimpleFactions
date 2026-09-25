package net.tfminecraft.simplefactions.mercenary.company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.army.Military;
import net.tfminecraft.simplefactions.loaders.RegimentLoader;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.GuildHandler;

class MercenaryHomeManpowerTest {
    private int oldSeconds;
    private Faction faction;
    private GuildHandler handler;
    private CompanyFixture fixture;

    @BeforeEach
    void setUp() {
        oldSeconds = Cache.mercenaryFormationSeconds;
        Cache.mercenaryFormationSeconds = 0;
        RegimentLoader.oList.clear();
        RegimentLoader.oList.add(CompanyFixture.regularPrototype("militia", 6, 2.0));
        RegimentLoader.oList.add(CompanyFixture.prototype());

        fixture = new CompanyFixture(500.0);
        faction = mock(Faction.class);
        handler = mock(GuildHandler.class);
        when(faction.getId()).thenReturn("hired_blades");
        when(faction.getGuildHandler()).thenReturn(handler);
    }

    @AfterEach
    void tearDown() {
        Cache.mercenaryFormationSeconds = oldSeconds;
        CompanyFixture.clearRegiments();
    }

    private MercenaryCompany found() {
        MercenaryCompanyService.requestFormation(fixture.guild, "Ivar", "Hired Blades");
        return fixture.company();
    }

    @Test
    void mainGuildCompanyAddsFilledSlotsOnly() {
        when(handler.getGuild("hired_blades")).thenReturn(fixture.guild);
        MercenaryCompany company = found();
        company.adminAdjustSlots(2);
        Military military = new Military(faction);

        assertSame(company, military.getHomeCompany());
        assertEquals(1, military.getMercenaryManpower());
        assertEquals(7, military.getManpower(true));
        assertEquals(7, military.getManpower(false));

        company.enlist("Sigrun");

        assertEquals(8, military.getManpower(true));
        assertEquals(6, military.getTotalSlots());
    }

    @Test
    void companyOfAnotherGuildDoesNotCount() {
        when(handler.getGuild("hired_blades")).thenReturn(mock(net.tfminecraft.simplefactions.guild.Guild.class));
        found();
        Military military = new Military(faction);

        assertNull(military.getHomeCompany());
        assertEquals(6, military.getManpower(true));
    }

    @Test
    void foundingCompanyDoesNotCount() {
        Cache.mercenaryFormationSeconds = 10;
        when(handler.getGuild("hired_blades")).thenReturn(fixture.guild);
        found();
        Military military = new Military(faction);

        assertNull(military.getHomeCompany());
        assertEquals(6, military.getManpower(true));
    }

    @Test
    void companyWithoutARegimentDoesNotCount() {
        MercenaryCompany company = new MercenaryCompany(fixture.guild, "Hired Blades", null, 0);
        fixture.guild.setCompany(company);
        when(handler.getGuild("hired_blades")).thenReturn(fixture.guild);
        Military military = new Military(faction);

        assertNull(military.getHomeCompany());
        assertEquals(6, military.getManpower(true));
    }
}
