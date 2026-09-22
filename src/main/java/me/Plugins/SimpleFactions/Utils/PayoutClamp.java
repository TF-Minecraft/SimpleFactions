package me.Plugins.SimpleFactions.Utils;

public final class PayoutClamp {

	private PayoutClamp() {}

	public static double scaleFactor(double available, double needed) {
		if (needed <= 0.0 || available <= 0.0) {
			return 0.0;
		}
		return Math.min(1.0, available / needed);
	}
}
