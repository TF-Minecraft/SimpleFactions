package me.Plugins.SimpleFactions.War.campaign.admin;

public record WarScheduleAdminResult(boolean success, String message) {
	public static WarScheduleAdminResult ok(String message) {
		return new WarScheduleAdminResult(true, message);
	}

	public static WarScheduleAdminResult error(String message) {
		return new WarScheduleAdminResult(false, message);
	}
}
