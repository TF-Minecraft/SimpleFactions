package net.tfminecraft.simplefactions.managers.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.installation.InstallationConstruction;
import net.tfminecraft.simplefactions.installation.InstallationKind;

class InstallationViewPendingTest {
	@Test
	void pendingUpgradeKeepsItsInstallationInNormalDetailView() {
		InstallationConstruction pendingUpgrade = pending(true);

		assertFalse(InstallationView.isPendingConstruction(pendingUpgrade, "station"));
		assertTrue(InstallationView.isPendingUpgrade(pendingUpgrade, "station"));
	}

	@Test
	void pendingNewConstructionKeepsConstructionDetailBehavior() {
		InstallationConstruction pendingBuild = pending(false);

		assertTrue(InstallationView.isPendingConstruction(pendingBuild, "station"));
		assertFalse(InstallationView.isPendingUpgrade(pendingBuild, "station"));
	}

	private static InstallationConstruction pending(boolean upgrade) {
		return new InstallationConstruction(
				"station",
				"Central Station",
				InstallationKind.TRAIN_STATION,
				42,
				10,
				20,
				60,
				1L,
				upgrade);
	}
}
