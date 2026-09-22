package me.Plugins.SimpleFactions.installation;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

public final class InstallationNavyQueries {
	private InstallationNavyQueries() {}

	public static boolean hasOperationalPort(Faction faction) {
		if (faction == null) {
			return false;
		}
		InstallationHandler handler = faction.getInstallationHandler();
		if (handler == null) {
			return false;
		}
		for (Installation installation : handler.getAll()) {
			if (installation != null && installation.getKind() == InstallationKind.PORT) {
				return true;
			}
		}
		return false;
	}
}
