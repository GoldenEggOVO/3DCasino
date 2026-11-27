package GoldenEggOVO.TDCasino.machine;

import GoldenEggOVO.TDCasino.machine.MachineResultState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MachineResultStateTest {
    @Test void settledResultsRemainHiddenAndAreCountedOnceWhenRevealed() {
        var state = new MachineResultState();
        state.launch(1L, 1000);
        assertTrue(state.settle(1L, 1500));
        assertEquals(500, state.accountedNet());
        assertEquals(0, state.totalNet());
        assertNull(state.last());
        assertEquals(1, state.pending());
        assertFalse(state.settle(1L, 9000));
        state.reviseStake(1L, 2000);
        assertEquals(new MachineResultState.Result(1000, 1500), state.reveal(1L, 9000));
        assertEquals(500, state.totalNet());
        assertEquals(500, state.accountedNet());
        assertEquals(0, state.pending());
        assertFalse(state.settle(1L, 9000));
    }

    @Test void resultsStayHiddenUntilRevealedAndAreConsumedOnce() {
        var state = new MachineResultState();
        state.launch(1L, 1000);
        assertEquals(1, state.pending());
        assertNull(state.last());
        assertEquals(-900, state.reveal(1L, 100).net());
        assertEquals(MachineResultState.Outcome.LOSS, state.last().outcome());
        assertNull(state.reveal(1L, 100));
        assertEquals(-900, state.totalNet());
        state.launch(2L, 1000);
        assertNull(state.last());
        assertEquals(0, state.reveal(2L, 1000).net());
    }
    @Test void doubleDownUsesActualTotalStakeAndKeepsCompletedResultStable() {
        var state = new MachineResultState();
        state.launch(1L, 1000);
        state.reviseStake(1L, 2000);
        assertEquals(2000, state.reveal(1L, 4000).net());
        state.reviseStake(1L, 100);
        assertEquals(2000, state.last().stake());
    }
    @Test void parallelBallsRetainTheirOwnStakeAndCanLandOutOfOrder() {
        var state = new MachineResultState();
        state.launch("a", 1000);
        state.launch("b", 2000);
        assertEquals(1000, state.reveal("b", 3000).net());
        assertEquals(1, state.pending());
        assertEquals(-1000, state.reveal("a", 0).net());
        assertEquals(0, state.pending());
        assertEquals(0, state.totalNet());
        assertNull(state.reveal("b", 3000));
    }
    @Test void soundOutcomeUsesNetRatherThanPositiveReturn() {
        assertEquals(MachineResultState.Outcome.LOSS, new MachineResultState.Result(100, 10).outcome());
        assertEquals(MachineResultState.Outcome.EVEN, new MachineResultState.Result(100, 100).outcome());
        assertEquals(MachineResultState.Outcome.WIN, new MachineResultState.Result(100, 200).outcome());
        assertEquals(MachineResultState.Outcome.BIG_WIN, new MachineResultState.Result(100, 500).outcome());
    }

    @Test void zeroAmountPlayDoesNotDivideByZeroOrRemainPending() {
        var state = new MachineResultState();
        state.launch(1, 0);
        assertEquals(MachineResultState.Outcome.EVEN, state.reveal(1, 0).outcome());
        assertEquals(0, state.pending());
        assertEquals(0, state.totalNet());
        assertEquals(MachineResultState.Outcome.WIN, new MachineResultState.Result(0, 1).outcome());
        assertThrows(IllegalArgumentException.class, () -> state.launch(2, -1));
    }
}
