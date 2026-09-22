package me.Plugins.SimpleFactions.War.campaign.runtime;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses admin duration tokens for {@code /war admin time add}.
 * Accepts compound forms such as {@code 1h 31m} or {@code 1h31m}.
 */
public final class CampaignDurationParser {
	private static final Pattern TOKEN_PATTERN = Pattern.compile("(\\d+)(s|m|h|d)");

	private CampaignDurationParser() {}

	public static Duration parse(String... tokens) {
		if (tokens == null || tokens.length == 0) {
			throw invalidDuration("no duration tokens");
		}

		Duration total = Duration.ZERO;

		for (String token : tokens) {
			if (token == null || token.isBlank()) {
				throw invalidDuration("empty token");
			}
			String trimmed = token.trim();
			Matcher matcher = TOKEN_PATTERN.matcher(trimmed);
			int end = 0;
			boolean tokenMatched = false;
			while (matcher.find()) {
				if (matcher.start() != end) {
					throw invalidDuration("unknown unit in '" + trimmed + "'");
				}
				total = total.plus(toDuration(matcher.group(1), matcher.group(2)));
				tokenMatched = true;
				end = matcher.end();
			}
			if (!tokenMatched || end != trimmed.length()) {
				throw invalidDuration("unknown unit in '" + trimmed + "'");
			}
		}

		if (total.isZero()) {
			throw invalidDuration("zero duration");
		}
		return total;
	}

	private static Duration toDuration(String valueText, String unit) {
		long value = Long.parseLong(valueText);
		return switch (unit) {
			case "s" -> Duration.ofSeconds(value);
			case "m" -> Duration.ofMinutes(value);
			case "h" -> Duration.ofHours(value);
			case "d" -> Duration.ofDays(value);
			default -> throw invalidDuration("unknown unit '" + unit + "'");
		};
	}

	private static IllegalArgumentException invalidDuration(String detail) {
		return new IllegalArgumentException("Invalid duration: " + detail + ". Example: 1h 31m");
	}
}
