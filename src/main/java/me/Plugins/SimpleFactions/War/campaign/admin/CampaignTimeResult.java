package me.Plugins.SimpleFactions.War.campaign.admin;

public record CampaignTimeResult(boolean success, String message) {
	public static CampaignTimeResult ok(String message) {
		return new CampaignTimeResult(true, message);
	}

	public static CampaignTimeResult error(String message) {
		return new CampaignTimeResult(false, message);
	}
}
