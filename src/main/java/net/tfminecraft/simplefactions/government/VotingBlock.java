package net.tfminecraft.simplefactions.government;

public final class VotingBlock {
	private VotingBlock() {
	}

	public static String furnitureId(String configPath) {
		String path = typedPath(configPath, "iaf");
		return path == null || path.isBlank() ? null : path;
	}

	public static boolean matches(String configPath, String namespacedId) {
		if (namespacedId == null || namespacedId.isBlank()) {
			return false;
		}
		String configured = furnitureId(configPath);
		return configured != null && configured.equalsIgnoreCase(namespacedId);
	}

	private static String typedPath(String configPath, String expectedType) {
		if (configPath == null || configPath.isBlank()) {
			return null;
		}
		String trimmed = configPath.trim();
		int openParen = trimmed.indexOf('(');
		int closeParen = trimmed.indexOf(')', openParen + 1);
		String type;
		String path;
		if (openParen >= 0 && closeParen > openParen) {
			type = trimmed.substring(0, openParen);
			path = trimmed.substring(openParen + 1, closeParen);
		} else {
			int dot = trimmed.indexOf('.');
			if (dot <= 0) {
				return null;
			}
			type = trimmed.substring(0, dot);
			path = trimmed.substring(dot + 1);
		}
		if (!type.equalsIgnoreCase(expectedType)) {
			return null;
		}
		return path.trim();
	}
}
