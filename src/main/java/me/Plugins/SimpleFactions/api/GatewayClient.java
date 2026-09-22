package me.Plugins.SimpleFactions.api;

/**
 * Reflective bridge to TFMCWeb {@code ProvinceSystemGateway}.
 * SimpleFactions soft-depends on TFMCWeb at runtime ({@code plugin.yml softdepend}).
 */
public final class GatewayClient {

	private GatewayClient() {}

	public static final class Result {
		public final boolean ok;
		public final String body;
		public final String error;

		private Result(boolean ok, String body, String error) {
			this.ok = ok;
			this.body = body;
			this.error = error;
		}

		public static Result success(String body) {
			return new Result(true, body == null ? "" : body, null);
		}

		public static Result fail(String error) {
			return new Result(false, null, error);
		}
	}

	public static Result request(String method, String path, String jsonBody) {
		try {
			Class<?> cls = Class.forName(
				"net.tfminecraft.TFMCWeb.api.ProvinceSystemGateway"
			);
			Object raw = cls.getMethod(
				"request",
				String.class,
				String.class,
				String.class
			).invoke(null, method, path, jsonBody);
			return fromGatewayResult(raw);
		} catch (Throwable t) {
			return Result.fail(failMessage(t));
		}
	}

	private static Result fromGatewayResult(Object raw) throws Exception {
		boolean ok = Boolean.TRUE.equals(
			raw.getClass().getField("ok").get(raw)
		);
		if (ok) {
			Object body = raw.getClass().getField("body").get(raw);
			return Result.success(body == null ? "" : String.valueOf(body));
		}
		Object err = raw.getClass().getField("error").get(raw);
		return Result.fail(err == null ? "request failed" : String.valueOf(err));
	}

	private static String failMessage(Throwable t) {
		Throwable c = t.getCause() != null ? t.getCause() : t;
		String msg = c.getMessage();
		if (msg == null || msg.isBlank()) {
			msg = c.getClass().getSimpleName();
		}
		return "TFMCWeb gateway unavailable: " + msg;
	}
}
