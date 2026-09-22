package me.Plugins.SimpleFactions.War.declare;

public final class WarValidationResult {
	private static final WarValidationResult OK = new WarValidationResult(true, null);

	private final boolean valid;
	private final String message;

	private WarValidationResult(boolean valid, String message) {
		this.valid = valid;
		this.message = message;
	}

	public static WarValidationResult ok() {
		return OK;
	}

	public static WarValidationResult fail(String message) {
		return new WarValidationResult(false, message);
	}

	public boolean isValid() {
		return valid;
	}

	public String getMessage() {
		return message;
	}
}
