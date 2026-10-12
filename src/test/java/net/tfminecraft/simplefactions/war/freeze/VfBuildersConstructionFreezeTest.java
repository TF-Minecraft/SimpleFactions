package net.tfminecraft.simplefactions.war.freeze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.tlibs.character.CharacterIdentity;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterStatus;

class VfBuildersConstructionFreezeTest {
	private final UUID account = UUID.randomUUID();
	private final ServicesManager services = mock(ServicesManager.class);
	private MockedStatic<Bukkit> bukkit;

	@BeforeEach
	void setUp() {
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
		bukkit.when(Bukkit::getServicesManager).thenReturn(services);
		PreparationFreeze.setActiveWars(List::of);
	}

	@AfterEach
	void tearDown() {
		PreparationFreeze.setActiveWars(null);
		bukkit.close();
	}

	@Test
	void anOnlineBuilderIsJudgedByTheCharacterPlayingNow() {
		Player builder = mock(Player.class);
		when(builder.getName()).thenReturn("Builder");
		bukkit.when(() -> Bukkit.getPlayer(account)).thenReturn(builder);

		assertEquals("Builder", VfBuildersConstructionFreeze.constructorKey(account));
		assertNull(new VfBuildersConstructionFreeze().freezeReason(account));
	}

	@Test
	void anOfflineBuilderIsJudgedByTheCharacterPlayedLast() {
		UUID characterId = UUID.randomUUID();
		CharacterIdentity identity = mock(CharacterIdentity.class);
		when(services.load(CharacterIdentity.class)).thenReturn(identity);
		when(identity.lastPlayed(any())).thenReturn(Optional.empty());
		when(identity.lastPlayed(account)).thenReturn(Optional.of(new CharacterInfo(
				new CharacterRef(account, characterId), "Hilde", CharacterStatus.ALIVE, null, Set.of(), 0,
				Instant.EPOCH)));

		assertEquals(Members.key(characterId), VfBuildersConstructionFreeze.constructorKey(account));
		assertNull(VfBuildersConstructionFreeze.constructorKey(UUID.randomUUID()));
	}

	@Test
	void anOfflineBuilderWithoutTheCharacterProviderHasNoCharacter() {
		assertNull(VfBuildersConstructionFreeze.constructorKey(account));
	}
}
