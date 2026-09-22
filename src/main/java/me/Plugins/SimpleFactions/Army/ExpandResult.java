package me.Plugins.SimpleFactions.Army;

public record ExpandResult(boolean allowed, String reason) {

	public static ExpandResult ok() {
		return new ExpandResult(true, null);
	}

	public static ExpandResult deny(String reason) {
		return new ExpandResult(false, reason);
	}
}
