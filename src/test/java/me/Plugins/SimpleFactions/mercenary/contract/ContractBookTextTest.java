package me.Plugins.SimpleFactions.mercenary.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The book's wording and its parser, tested as plain strings per the GUI testing
 * convention in AGENTS.md. What the page says is a game rule, not decoration.
 */
class ContractBookTextTest {
    private ContractFixture fixture;
    private MercenaryContract contract;

    @BeforeEach
    void setUp() {
        fixture = ContractFixture.formed(4);
        contract = fixture.offer(
                new ContractTerms(3, 60.0, 15.0, 7, 60.0, 500.0), System.currentTimeMillis());
    }

    @AfterEach
    void tearDown() {
        ContractFixture.tearDown();
    }

    /* =====================================================
     * Terms page carries every locked field
     * ===================================================== */

    @Test
    void theTermsPageNamesEveryLockedField() {
        String page = ContractBook.termsPage(contract);
        assertTrue(page.contains("Hired Blades"));
        assertTrue(page.contains("Slots: 3"));
        assertTrue(page.contains("Price per battle (d): 60"));
        assertTrue(page.contains("Price per day (d): 15"));
        assertTrue(page.contains("Duration(days): 7"));
        assertTrue(page.contains("Absence refund (d): 60"));
        assertTrue(page.contains("Breach refund (d): 500"));
    }

    /** A contract names no war, and the scope page has to say so out loud. */
    @Test
    void theScopePageSaysTheContractCoversEveryWar() {
        String page = ContractBook.scopePage(contract);
        assertTrue(page.contains("names no war"));
        assertTrue(page.contains("every war"));
        assertTrue(page.contains("7 days"));
    }

    /**
     * The single most misreadable figure in the whole system: two prices that both
     * apply on a battle day. The page must state it and show the sum.
     */
    @Test
    void thePricingPageSpellsOutThatBothPricesApply() {
        String page = ContractBook.pricingPage(contract);
        assertTrue(page.contains(ContractBook.BOTH_PRICES_SENTENCE));
        assertTrue(page.contains("BOTH"));
        assertTrue(page.contains("not alternatives"));
        assertTrue(page.contains("Per day: 45"), page);
        assertTrue(page.contains("Per battle: 180"), page);
        assertTrue(page.contains("A day with a battle: 225"), page);
    }

    @Test
    void theRefundsPageStatesTheBreachFigureAndThatServedDaysAreOwed() {
        String page = ContractBook.refundsPage(contract);
        assertTrue(page.contains("Absence refund: 60"));
        assertTrue(page.contains("Breach refund: 500"));
        assertTrue(page.contains("3 slots it promised"));
        assertTrue(page.contains("owed in every case"));
    }

    @Test
    void theSignaturePageStampsTheReputationAtSigning() {
        String page = ContractBook.signaturePage(contract);
        assertTrue(page.contains("Reputation at signing: 50"));
        assertTrue(page.contains(fixture.company.getLeader()));
        assertTrue(page.contains(fixture.hirer.getName()));
    }

    @Test
    void theMinimumsPageQuotesTheConfiguredFloors() {
        assertTrue(ContractBook.minimumsPage().contains("50"));
        assertTrue(ContractBook.minimumsPage().contains("10"));
        assertTrue(ContractBook.minimumsPage().contains("14 days"));
        assertTrue(ContractBook.minimumsSummary().contains("max 14 days"));
    }

    /* =====================================================
     * Parsing, the counter-offer mechanism
     * ===================================================== */

    @Test
    void theTermsPageParsesBackToTheSameFigures() {
        assertEquals(contract.getTerms(),
                ContractBook.parseTerms(ContractBook.termsPage(contract)));
    }

    @Test
    void aFreshDraftParsesToTheDefaults() {
        assertEquals(ContractTerms.defaults(),
                ContractBook.parseTerms(ContractBook.draftTermsPage(fixture.company)));
    }

    /** Editing the page is the counter-offer, so an edited figure must be read back. */
    @Test
    void anEditedFigureIsWhatComesBackOut() {
        String edited = ContractBook.termsPage(contract).replace("Slots: 3", "Slots: 2");
        assertEquals(2, ContractBook.parseTerms(edited).slots());
        assertEquals(60.0, ContractBook.parseTerms(edited).pricePerSlotPerBattle());
    }

    @Test
    void theTwoPriceLinesAreNeverConfusedForEachOther() {
        ContractTerms parsed = ContractBook.parseTerms(ContractBook.termsPage(contract));
        assertEquals(60.0, parsed.pricePerSlotPerBattle());
        assertEquals(15.0, parsed.pricePerSlotPerDay());
        assertNotEquals(parsed.pricePerSlotPerBattle(), parsed.pricePerSlotPerDay());
    }

    @Test
    void colourCodesAndGarbageLinesAreIgnored() {
        String page = "§6§l[CONTRACT TERMS]\n"
                + "§0Company: Hired Blades\n"
                + "Slots: 2\n"
                + "some scribble\n"
                + "Price per battle (d): 55\n"
                + "Price per day (d): 11\n"
                + "Duration(days): 3\n"
                + "Absence refund (d): 55\n"
                + "Breach refund (d): 100\n";
        assertEquals(new ContractTerms(2, 55.0, 11.0, 3, 55.0, 100.0),
                ContractBook.parseTerms(page));
    }

    @Test
    void anUnreadableFigureFallsToZeroAndFailsValidation() {
        String page = ContractBook.termsPage(contract).replace("Slots: 3", "Slots: three");
        ContractTerms parsed = ContractBook.parseTerms(page);
        assertEquals(0, parsed.slots());
        assertFalse(ContractValidator.validate(
                parsed, fixture.company, System.currentTimeMillis()).ok());
    }

    @Test
    void parsingNothingYieldsNothing() {
        assertEquals(null, ContractBook.parseTerms((String) null));
    }
}
