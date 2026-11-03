package net.tfminecraft.simplefactions.government.stability;

/**
 * One {@link BankruptcyMemo} and one {@link StabilityMemo} run around read-only code, such as a
 * menu render: each realm guild's treasury is asked once and each faction's stability report
 * built once while it runs, instead of reading SQLite every time an income or report is asked.
 * Only for code that writes no treasury, bank, guild or stability input while it runs.
 */
public final class ReadMemos {
	private ReadMemos() {}

	public static void during(Runnable reads) {
		BankruptcyMemo.Run bankruptcy = BankruptcyMemo.begin();
		StabilityMemo.Run stability = StabilityMemo.begin();
		try {
			reads.run();
		} finally {
			StabilityMemo.end(stability);
			BankruptcyMemo.end(bankruptcy);
		}
	}
}
