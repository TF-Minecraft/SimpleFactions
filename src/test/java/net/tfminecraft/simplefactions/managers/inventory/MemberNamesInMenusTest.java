package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.UUID;
import net.tfminecraft.simplefactions.government.election.Candidate;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.GuiTestFixture;
import net.tfminecraft.simplefactions.utils.Represents;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Menus print character names for member keys and show the playing account's head. */
@SuppressWarnings("deprecation")
class MemberNamesInMenusTest {
  private final UUID owner = UUID.randomUUID();
  private GuiTestFixture gui;
  private CharacterKeyFixture characters;
  private String aldric;

  @BeforeEach
  void setUp() {
    gui = new GuiTestFixture();
    characters = CharacterKeyFixture.install();
    aldric = characters.offline(owner, "Aldric Vane");
  }

  @AfterEach
  void close() {
    gui.close();
  }

  @Test
  void skullsTakeTheHeadOfTheAccountThatPlaysTheCharacter() {
    OfflinePlayer account = mock(OfflinePlayer.class);
    when(Bukkit.getOfflinePlayer(owner)).thenReturn(account);
    SkullMeta skull = mock(SkullMeta.class);

    MemberHeads.apply(skull, aldric);
    MemberHeads.apply(skull, "not-a-character");
    MemberHeads.apply(mock(ItemMeta.class), aldric);

    verify(skull, times(1)).setOwningPlayer(account);
  }

  @Test
  void companyRosterAndSlotsNameTheCharacter() {
    MercenaryCompany company = mock(MercenaryCompany.class);
    when(company.getName()).thenReturn("Blades");
    CompanyCreator creator = new CompanyCreator();

    ItemStack roster = creator.createRosterItem(company, aldric, true);

    assertEquals("§eAldric Vane", roster.getItemMeta().getDisplayName());
    assertTrue(creator.buildSlotLore(aldric).contains("§7Mercenary: §eAldric Vane"));
  }

  @Test
  void leadershipTargetsAndCandidatesNameTheCharacter() {
    Faction faction = mock(Faction.class);
    ItemStack target =
        new MovementCreator().createPotentialTargetItem(gui.player("Ryan"), faction, aldric, 0);
    assertTrue(target.getItemMeta().getDisplayName().contains("Aldric Vane"));

    try (MockedStatic<Represents> represents = mockStatic(Represents.class)) {
      represents.when(() -> Represents.represents(any(), any())).thenReturn("Artisans");
      ItemStack candidate = new ElectionCreator().createCandidateItem(faction, Candidate.LEADER, aldric);
      assertTrue(candidate.getItemMeta().getDisplayName().contains("Aldric Vane"));
    }
  }
}
