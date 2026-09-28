package net.tfminecraft.simplefactions.managers;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.loans.Loan;
import net.tfminecraft.simplefactions.guild.loans.LoanBook;
import net.tfminecraft.simplefactions.guild.loans.LoanHandler;
import net.tfminecraft.simplefactions.keys.Keys;
import net.tfminecraft.simplefactions.objects.Bank;

/** Loan books commit a guild's bank, so each stage checks who signs and that it happens once. */
class PlayerManagerLoanBookTest {

	private static final String TERMS = "[LOAN TERMS]\nIssuer: Lenders\nAmount (d): 500.0\nDuration(days): 30\n"
			+ "Interest (%): 6.0\nDaily Payments: \nYes\nOverdue Fee (%): 2.0\n";

	private MockedStatic<FactionManager> factionManager;
	private final PlayerManager listener = new PlayerManager();
	private final Guild lender = mock(Guild.class);
	private final Guild borrower = mock(Guild.class);
	private final Bank lenderBank = mock(Bank.class);
	private final Bank borrowerBank = mock(Bank.class);
	private final Player player = mock(Player.class);

	private static SimpleFactions previousPlugin;

	@BeforeAll
	static void keys() {
		// Keys builds its NamespacedKeys from the plugin the first time it is used.
		previousPlugin = SimpleFactions.plugin;
		SimpleFactions plugin = mock(SimpleFactions.class);
		when(plugin.getName()).thenReturn("SimpleFactions");
		when(plugin.namespace()).thenReturn("simplefactions");
		SimpleFactions.plugin = plugin;
	}

	@AfterAll
	static void restorePlugin() {
		SimpleFactions.plugin = previousPlugin;
	}

	@BeforeEach
	void setUp() {
		factionManager = mockStatic(FactionManager.class);
		factionManager.when(() -> FactionManager.getGuildByString("lender")).thenReturn(lender);
		factionManager.when(() -> FactionManager.getGuildByLeader("Borrower")).thenReturn(borrower);
		when(lender.getId()).thenReturn("lender");
		when(lender.getName()).thenReturn("Lenders");
		when(lender.getBank()).thenReturn(lenderBank);
		when(lenderBank.getWealth()).thenReturn(10_000.0);
		when(borrower.getId()).thenReturn("borrower");
		when(borrower.getBank()).thenReturn(borrowerBank);
		when(player.getName()).thenReturn("Borrower");
	}

	@AfterEach
	void tearDown() {
		factionManager.close();
	}

	private static BookMeta book(Integer stage, String offerId) {
		BookMeta meta = mock(BookMeta.class);
		PersistentDataContainer data = mock(PersistentDataContainer.class);
		when(meta.getPersistentDataContainer()).thenReturn(data);
		when(data.get(Keys.STRING_KEY, PersistentDataType.STRING)).thenReturn("lender");
		when(data.get(Keys.INT, PersistentDataType.INTEGER)).thenReturn(stage);
		when(data.get(Keys.SECONDARY_STRING_KEY, PersistentDataType.STRING)).thenReturn(TERMS);
		when(data.get(Keys.LONG, PersistentDataType.LONG)).thenReturn(System.currentTimeMillis() + 60_000L);
		when(data.get(Keys.LOAN_OFFER, PersistentDataType.STRING)).thenReturn(offerId);
		when(meta.getPageCount()).thenReturn(2);
		when(meta.getPage(2)).thenReturn(TERMS);
		return meta;
	}

	private PlayerEditBookEvent sign(BookMeta meta) {
		PlayerEditBookEvent event = mock(PlayerEditBookEvent.class);
		when(event.isSigning()).thenReturn(true);
		when(event.getPlayer()).thenReturn(player);
		when(event.getPreviousBookMeta()).thenReturn(meta);
		when(event.getNewBookMeta()).thenReturn(meta);
		listener.signBook(event);
		return event;
	}

	@Test
	void onlyTheLenderLeaderCanDraftOrReviewALoan() {
		when(lender.isLeader(player)).thenReturn(false);

		for (int stage : new int[] { 1, 2 }) {
			PlayerEditBookEvent event = sign(book(stage, null));
			verify(event).setCancelled(true);
		}
		verify(player, org.mockito.Mockito.times(2)).sendMessage(contains("Only the leader of Lenders"));
	}

	@Test
	void anAgreementThatWasAlreadyAcceptedCannotBeSignedAgain() {
		LoanHandler loans = new LoanHandler(lender);
		loans.issueLoan(new Loan("offer-accepted", 500.0, lender, borrower, System.currentTimeMillis(), 30, 6.0, 2.0, true));
		when(lender.getLoanHandler()).thenReturn(loans);

		sign(book(3, "offer-accepted"));

		verify(player).sendMessage(contains("already been signed"));
		verify(borrowerBank, never()).deposit(anyDouble());
		verify(lenderBank, never()).withdraw(anyDouble());
	}

	@Test
	void anAgreementClaimedSinceStartupCannotBeSignedAgain() {
		when(lender.getLoanHandler()).thenReturn(new LoanHandler(lender));
		LoanBook.claimOffer("offer-claimed");

		sign(book(3, "offer-claimed"));

		verify(player).sendMessage(contains("already been signed"));
		verify(borrowerBank, never()).deposit(anyDouble());
	}

	@Test
	void anAgreementFromBeforeTheUpdateIsRefused() {
		when(lender.getLoanHandler()).thenReturn(new LoanHandler(lender));

		sign(book(3, null));

		verify(player).sendMessage(contains("out of date"));
		verify(borrowerBank, never()).deposit(anyDouble());
	}
}
