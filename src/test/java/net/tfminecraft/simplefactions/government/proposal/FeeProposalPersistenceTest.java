package net.tfminecraft.simplefactions.government.proposal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.government.handler.ProposalHandler;
import net.tfminecraft.simplefactions.objects.Faction;

class FeeProposalPersistenceTest {
    private final Government gov = mock(Government.class);

    private Proposal fee(FeeKind kind, String type, double rate) {
        Proposal proposal = new Proposal("Alice", gov);
        proposal.setFeeProposal(new FeeChange(kind, type, rate));
        return proposal;
    }

    @Test
    void feeProposalsSurviveSaveAndLoad() {
        ProposalHandler handler = new ProposalHandler(gov);
        handler.propose(fee(FeeKind.VEHICLE_TAX, null, 12.5));
        handler.propose(fee(FeeKind.TRANSFER_FEE, "cruiser", 2.0));
        handler.propose(fee(FeeKind.REGISTRATION_FEE, "pack:ship", 1.5));

        List<String> saved = handler.serializeProposals();
        ProposalHandler restored = new ProposalHandler(gov);
        restored.restoreProposals(mock(Faction.class), saved);

        assertEquals(3, restored.getProposals().size());
        FeeChange colon = restored.getProposals().get(2).getFeeChange();
        assertEquals("pack:ship", colon.getVehicleTypeId());
        assertEquals(1.5, colon.getNewRate());
        FeeChange general = restored.getProposals().get(0).getFeeChange();
        assertEquals(FeeKind.VEHICLE_TAX, general.getKind());
        assertNull(general.getVehicleTypeId());
        assertEquals(12.5, general.getNewRate());
        FeeChange cruiser = restored.getProposals().get(1).getFeeChange();
        assertEquals(FeeKind.TRANSFER_FEE, cruiser.getKind());
        assertEquals("cruiser", cruiser.getVehicleTypeId());
        assertEquals(2.0, cruiser.getNewRate());
    }

    @Test
    void onlyOneProposalPerFeeAndVehicle() {
        ProposalHandler handler = new ProposalHandler(gov);
        handler.propose(fee(FeeKind.REGISTRATION_FEE, "cruiser", 2.0));

        assertFalse(handler.canBeProposed(fee(FeeKind.REGISTRATION_FEE, "Cruiser", 3.0)));
        assertTrue(handler.canBeProposed(fee(FeeKind.REGISTRATION_FEE, null, 3.0)));
        assertTrue(handler.canBeProposed(fee(FeeKind.TRANSFER_FEE, "cruiser", 3.0)));
    }

    @Test
    void feeProposalsCountAsTaxChangesForMovements() {
        assertEquals(net.tfminecraft.simplefactions.government.movement.Action.TAX_CHANGE,
                fee(FeeKind.VEHICLE_TAX, null, 5).getPoliticalAction().getAction());
    }
}
