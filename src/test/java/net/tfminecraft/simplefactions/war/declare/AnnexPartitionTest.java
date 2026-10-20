package net.tfminecraft.simplefactions.war.declare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.war.declare.AnnexPartition.Holder;
import net.tfminecraft.simplefactions.war.declare.AnnexPartition.Result;

class AnnexPartitionTest {
	/** Grid id for row {@code r}, column {@code c}. */
	private static int id(int r, int c) {
		return 100 + r * 10 + c;
	}

	private static Set<Integer> rect(int r0, int r1, int c0, int c1) {
		Set<Integer> out = new TreeSet<>();
		for (int r = r0; r <= r1; r++) {
			for (int c = c0; c <= c1; c++) {
				out.add(id(r, c));
			}
		}
		return out;
	}

	@Test
	void settlementInTheMiddleGetsARoadAlongTheTitleEdge() {
		// Rows 1-3, columns 1-5 form the title; the holder holds column 0, the attacker column 6.
		Board board = Board.grid(5, 7);
		Set<Integer> title = rect(1, 3, 1, 5);
		Set<Integer> outside = rect(1, 3, 0, 0);
		Set<Integer> settlement = Set.of(id(2, 5));

		Result result = board.partition(title, rect(0, 4, 6, 6),
				board.holder("holder", id(2, 0), outside, title, settlement));

		assertEquals(Set.of(id(1, 1), id(1, 2), id(1, 3), id(1, 4), id(1, 5)), result.road());
		for (int c = 1; c <= 4; c++) {
			assertTrue(result.taken().contains(id(2, c)), "middle row is taken");
		}
		assertEquals(Set.of(id(2, 5)), result.protectedProvinces());
		assertLegal(board, id(2, 0), outside, result, settlement);
	}

	@Test
	void twoSettlementsShareOneRoad() {
		Board board = Board.grid(5, 7);
		Set<Integer> title = rect(1, 3, 1, 5);
		Set<Integer> outside = rect(1, 3, 0, 0);
		Set<Integer> settlements = Set.of(id(1, 5), id(3, 5));

		Result result = board.partition(title, rect(0, 4, 6, 6),
				board.holder("holder", id(2, 0), outside, title, settlements));

		// One road to the first settlement plus the single province linking the second.
		assertEquals(5, result.road().size(), result.road().toString());
		assertEquals(title.size() - 7, result.taken().size());
		assertLegal(board, id(2, 0), outside, result, settlements);
	}

	@Test
	void capitalInsideTheTitleIsKeptAndItsSurroundingsTaken() {
		Board board = Board.grid(5, 7);
		Set<Integer> title = rect(1, 3, 1, 5);
		int capital = id(2, 3);

		Result result = board.partition(title, rect(0, 4, 6, 6),
				board.holder("holder", capital, Set.of(), title, Set.of(capital)));

		assertEquals(Set.of(capital), result.kept());
		assertEquals(Set.of(capital), result.protectedProvinces());
		assertTrue(result.road().isEmpty());
		assertEquals(title.size() - 1, result.taken().size());
	}

	@Test
	void coastalSettlementReachedByOneSeaTileNeedsNoRoad() {
		Board board = Board.grid(5, 7);
		Set<Integer> title = rect(1, 3, 1, 5);
		Set<Integer> outside = rect(1, 3, 0, 0);
		int port = id(3, 5);
		board.sea(id(3, 0), port);

		Result result = board.partition(title, rect(0, 4, 6, 6),
				board.holder("holder", id(2, 0), outside, title, Set.of(port)));

		assertTrue(result.road().isEmpty(), result.road().toString());
		assertEquals(Set.of(port), result.kept());
		assertLegal(board, id(2, 0), outside, result, Set.of(port));
	}

	@Test
	void outsideHoldingsThatWouldBeCutOffGetARoad() {
		// The holder holds land on both sides of the title; the attacker borders it from below.
		Board board = Board.grid(6, 7);
		Set<Integer> title = rect(1, 3, 1, 5);
		Set<Integer> outside = new TreeSet<>(rect(1, 3, 0, 0));
		outside.addAll(rect(1, 3, 6, 6));

		Result result = board.partition(title, rect(5, 5, 0, 6),
				board.holder("holder", id(2, 0), outside, title, Set.of()));

		assertEquals(5, result.road().size(), result.road().toString());
		assertTrue(result.protectedProvinces().isEmpty());
		assertFalse(result.taken().isEmpty());
		assertLegal(board, id(2, 0), outside, result, Set.of());
	}

	@Test
	void sliverMostlySurroundedByTheHolderGoesBack() {
		// capital(1)-2 are the holder's; 10 borders both and the title interior 11.
		Board board = new Board();
		board.link(1, 2).link(1, 10).link(2, 10).link(10, 11).link(11, 12).link(12, 99);
		Set<Integer> title = Set.of(10, 11, 12);

		Result result = board.partition(title, Set.of(99),
				board.holder("holder", 1, Set.of(1, 2), title, Set.of()));

		assertEquals(Set.of(10), result.kept());
		assertTrue(result.road().isEmpty());
		assertEquals(Set.of(11, 12), result.taken());
	}

	@Test
	void enclaveNotTouchingTheAttackerStaysWithTheHolder() {
		// 20 is title land bordering only the holder's capital and neutral 50.
		Board board = new Board();
		board.link(1, 20).link(20, 50).link(1, 11).link(11, 12).link(12, 99).link(11, 13).link(13, 51);
		Set<Integer> title = Set.of(11, 12, 13, 20);

		Result result = board.partition(title, Set.of(99),
				board.holder("holder", 1, Set.of(1), title, Set.of()));

		assertTrue(result.kept().contains(20));
		assertTrue(result.taken().containsAll(Set.of(11, 12, 13)));
	}

	@Test
	void keptProvinceMostlySurroundedByTheAttackerIsTrimmed() {
		// Without routes the holder first keeps the whole title; 11 then falls to the attacker
		// it mostly borders, while 10 is still needed to reach the outside holding 3.
		// 10 also borders the attacker heavily, but holding 3 still needs it.
		Board board = new Board();
		board.link(1, 10).link(10, 3).link(10, 11).link(11, 98).link(11, 99).link(10, 96).link(10, 97);
		AnnexPartition.Graph roadless = roadless(board);
		Set<Integer> title = Set.of(10, 11);

		Result result = AnnexPartition.compute(roadless, title, Set.of(96, 97, 98, 99),
				List.of(board.holder("holder", 1, Set.of(1, 3), title, Set.of())));

		assertEquals(Set.of(11), result.taken());
		assertEquals(Set.of(10), result.kept());
		assertTrue(result.road().isEmpty());
	}

	@Test
	void keptProvinceIsNotTakenIntoLandTheAttackerCannotReach() {
		// The vassal's 20 and 21 are cut off from its capital and from the attacker, so they stay taken;
		// the liege's 12 borders mostly them, but taking it would only grow that unreachable pocket.
		Board board = new Board();
		board.link(1, 10).link(10, 3).link(1, 12).link(12, 20).link(12, 21).link(20, 21).link(30, 99);
		AnnexPartition.Graph roadless = roadless(board);
		Holder liege = board.holder("liege", 1, Set.of(1, 3), Set.of(10, 12), Set.of());
		Holder vassal = board.holder("vassal", 2, Set.of(2), Set.of(20, 21, 30), Set.of());

		Result result = AnnexPartition.compute(roadless, Set.of(10, 12, 20, 21, 30), Set.of(99),
				List.of(liege, vassal));

		assertEquals(Set.of(10, 12), result.kept());
		assertEquals(Set.of(20, 21, 30), result.taken());
	}

	@Test
	void straightRoadBeatsAnEqualRoadThatBends() {
		// Capital 1 reaches settlement 3 through 2 (bending) or 5 (straight); the lower id would win a tie.
		Board board = new Board();
		board.link(1, 2).link(2, 3).link(1, 5).link(5, 3).link(3, 99).link(2, 97).link(2, 98);
		board.centre(1, 0, 0).centre(2, 100, 100).centre(3, 200, 0).centre(5, 100, 0);
		Set<Integer> title = Set.of(2, 3, 5);

		Result result = board.partition(title, Set.of(99),
				board.holder("holder", 1, Set.of(1), title, Set.of(3)));

		assertEquals(Set.of(5), result.road());
		assertEquals(Set.of(2), result.taken());
	}

	@Test
	void partitionIsDeterministic() {
		Board board = Board.grid(7, 7);
		Set<Integer> title = rect(1, 5, 1, 5);
		Set<Integer> outside = rect(0, 6, 0, 0);
		Set<Integer> settlements = Set.of(id(3, 3), id(5, 5), id(1, 4));
		Result first = board.partition(title, rect(0, 6, 6, 6),
				board.holder("holder", id(3, 0), outside, title, settlements));
		for (int i = 0; i < 5; i++) {
			Result again = board.partition(new HashSet<>(title), new HashSet<>(rect(0, 6, 6, 6)),
					board.holder("holder", id(3, 0), new HashSet<>(outside), new HashSet<>(title),
							new HashSet<>(settlements)));
			assertEquals(first, again);
		}
		assertLegal(board, id(3, 0), outside, first, settlements);
	}

	@Test
	void holdersArePlannedSeparatelyWithTheirOwnCapitals() {
		Board board = Board.grid(5, 7);
		Set<Integer> title = rect(1, 3, 1, 5);
		Set<Integer> liegeIncoming = rect(1, 3, 1, 3);
		Set<Integer> vassalIncoming = rect(1, 3, 4, 5);
		Holder liege = board.holder("liege", id(2, 0), rect(1, 3, 0, 0), liegeIncoming, Set.of(id(3, 3)));
		Holder vassal = board.holder("vassal", id(2, 5), Set.of(), vassalIncoming, Set.of(id(2, 5)));

		Result result = AnnexPartition.compute(board, title, rect(0, 4, 6, 6), List.of(vassal, liege));

		assertTrue(result.kept().containsAll(Set.of(id(2, 5), id(3, 3))));
		assertEquals(Set.of(id(3, 1), id(3, 2)), result.road());
	}

	@Test
	void nothingToTakeWhenEverythingIsSettlementOrRoad() {
		Board board = new Board();
		board.link(1, 10).link(10, 11).link(10, 99);
		Set<Integer> title = Set.of(10, 11);

		Result result = board.partition(title, Set.of(99),
				board.holder("holder", 1, Set.of(1), title, Set.of(11)));

		assertTrue(result.taken().isEmpty());
		assertEquals(Set.of(10), result.road());
		assertEquals(2, result.incoming());
	}

	private static AnnexPartition.Graph roadless(Board board) {
		return new AnnexPartition.Graph() {
			@Override
			public Collection<Integer> landNeighbours(int province) {
				return board.landNeighbours(province);
			}

			@Override
			public Collection<Integer> routeNeighbours(int province) {
				return Set.of();
			}

			@Override
			public int[] centre(int province) {
				return null;
			}
		};
	}

	private static void assertLegal(Board board, int capital, Set<Integer> outside, Result result,
			Set<Integer> protectedProvinces) {
		Set<Integer> holdings = new HashSet<>(outside);
		holdings.addAll(result.kept());
		Set<Integer> legal = board.legal(capital, holdings);
		assertTrue(legal.containsAll(holdings), "illegal: " + diff(holdings, legal));
		assertTrue(result.kept().containsAll(protectedProvinces));
	}

	private static Set<Integer> diff(Set<Integer> a, Set<Integer> b) {
		Set<Integer> out = new TreeSet<>(a);
		out.removeAll(b);
		return out;
	}

	/** Land graph with optional one-tile sea links; legality floods land from the capital like ProvinceHandler. */
	private static final class Board implements AnnexPartition.Graph {
		private final Map<Integer, Set<Integer>> land = new HashMap<>();
		private final Map<Integer, Set<Integer>> sea = new HashMap<>();
		private final Map<Integer, int[]> centres = new HashMap<>();

		static Board grid(int rows, int cols) {
			Board board = new Board();
			for (int r = 0; r < rows; r++) {
				for (int c = 0; c < cols; c++) {
					board.centres.put(id(r, c), new int[] {c * 100, r * 100});
					if (c + 1 < cols) {
						board.link(id(r, c), id(r, c + 1));
					}
					if (r + 1 < rows) {
						board.link(id(r, c), id(r + 1, c));
					}
				}
			}
			return board;
		}

		Board link(int a, int b) {
			land.computeIfAbsent(a, k -> new TreeSet<>()).add(b);
			land.computeIfAbsent(b, k -> new TreeSet<>()).add(a);
			return this;
		}

		Board centre(int province, int x, int z) {
			centres.put(province, new int[] {x, z});
			return this;
		}

		void sea(int a, int b) {
			sea.computeIfAbsent(a, k -> new TreeSet<>()).add(b);
			sea.computeIfAbsent(b, k -> new TreeSet<>()).add(a);
		}

		@Override
		public Collection<Integer> landNeighbours(int province) {
			return land.getOrDefault(province, Set.of());
		}

		@Override
		public Collection<Integer> routeNeighbours(int province) {
			Set<Integer> out = new TreeSet<>(landNeighbours(province));
			out.addAll(sea.getOrDefault(province, Set.of()));
			return out;
		}

		@Override
		public int[] centre(int province) {
			return centres.get(province);
		}

		Holder holder(String id, int capital, Set<Integer> outside, Set<Integer> incoming, Set<Integer> protect) {
			Set<Integer> holdings = new HashSet<>(outside);
			holdings.addAll(incoming);
			Set<Integer> protectedProvinces = new HashSet<>(protect);
			if (incoming.contains(capital)) {
				protectedProvinces.add(capital);
			}
			return new Holder(id, holdings, incoming, protectedProvinces, owned -> legal(capital, owned));
		}

		Result partition(Set<Integer> title, Set<Integer> attackerLand, Holder holder) {
			return AnnexPartition.compute(this, title, attackerLand, new ArrayList<>(List.of(holder)));
		}

		/** Land from the capital, plus one sea tile from the capital's land blob, then land again. */
		Set<Integer> legal(int capital, Set<Integer> owned) {
			Set<Integer> legal = new HashSet<>();
			if (!owned.contains(capital)) {
				return legal;
			}
			Set<Integer> blob = flood(Set.of(capital), owned);
			legal.addAll(blob);
			Set<Integer> overseas = new HashSet<>();
			for (int from : blob) {
				for (int across : sea.getOrDefault(from, Set.of())) {
					if (owned.contains(across)) {
						overseas.add(across);
					}
				}
			}
			legal.addAll(flood(overseas, owned));
			return legal;
		}

		private Set<Integer> flood(Set<Integer> start, Set<Integer> owned) {
			Set<Integer> out = new HashSet<>(start);
			ArrayDeque<Integer> queue = new ArrayDeque<>(start);
			while (!queue.isEmpty()) {
				for (int next : landNeighbours(queue.poll())) {
					if (owned.contains(next) && out.add(next)) {
						queue.add(next);
					}
				}
			}
			return out;
		}
	}
}
