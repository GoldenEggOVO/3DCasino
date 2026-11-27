package GoldenEggOVO.TDCasino.game.mines;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.Random;

class MinesBoardTest {
    @Test
    void boardAcceptsZeroAndCentAmountsWithTheOriginalPayoutRounding() {
        MinesBoard zero = MinesBoard.create(0, 3, 7);
        zero.reveal(3);
        assertEquals(0, zero.payout());
        zero.pay();
        assertEquals(MinesBoard.Phase.PAID, zero.phase());
        MinesBoard cents = MinesBoard.create(101, 3, 7);
        cents.reveal(3);
        assertEquals(112, cents.payout());
    }

    @Test
    void malformedBoardsAndOutOfRangeCellsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> MinesBoard.create(-1, 3, 7));
        assertThrows(IllegalArgumentException.class, () -> MinesBoard.create(1_000_000_001L, 3, 7));
        assertThrows(IllegalArgumentException.class, () -> MinesBoard.create(100, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> MinesBoard.create(100, 3, 3));
        assertThrows(IllegalArgumentException.class, () -> MinesBoard.create(100, 1, 1 << 25));
        MinesBoard board = MinesBoard.create(100, 3, 7);
        for (int cell : new int[] {-1, 25, 32}) {
            assertThrows(IllegalArgumentException.class, () -> board.reveal(cell));
        }
        assertEquals(0, board.revealed());
    }

    @Test
    void revealedCellsAndFinishedBoardsRejectFurtherMoves() {
        MinesBoard board = MinesBoard.create(100, 3, 7);
        assertThrows(IllegalStateException.class, board::pay);
        board.reveal(3);
        assertThrows(IllegalStateException.class, () -> board.reveal(3));
        board.reveal(0);
        assertEquals(MinesBoard.Phase.LOST, board.phase());
        assertEquals(0, board.payout());
        assertTrue(board.finished());
        assertThrows(IllegalStateException.class, board::pay);
        assertThrows(IllegalStateException.class, () -> board.reveal(4));
    }

    @Test
    void lastSafeCellPaysTheCombinatorialReturnAndClosesTheGame() {
        MinesBoard board = MinesBoard.create(100, 24, ((1 << 25) - 1) ^ 1);
        board.reveal(0);
        assertTrue(board.allSafe());
        assertEquals(1, board.safeCount());
        assertEquals(2450, board.payout());
        board.pay();
        assertTrue(board.finished());
        assertThrows(IllegalStateException.class, board::pay);
    }

    @Test
    void snapshotsCannotRevealOrPayTheLiveBoard() {
        MinesRound round = new MinesRound(new Random(7));
        round.start(0);
        MinesBoard snapshot = round.mines();
        int safe = 0;
        while ((snapshot.mask() & (1 << safe)) != 0) {
            safe++;
        }
        snapshot.reveal(safe);
        snapshot.pay();
        assertEquals(0, round.mines().revealed());
        assertEquals(MinesBoard.Phase.ACTIVE, round.mines().phase());
        assertFalse(round.cash(0));
        round.reveal(safe);
        assertTrue(round.cash(0));
        assertFalse(round.cash(0));
    }

    @Test
    void invalidCellCannotAliasAnAlreadyRevealedCell() {
        MinesRound round = new MinesRound(new Random(7));
        round.start(0);
        assertThrows(IllegalArgumentException.class, () -> round.reveal(32));
        assertEquals(0, round.mines().revealed());
    }

    @Test
    void payoutsKeepTheOriginalHouseReturnAtEveryDifficulty() {
        for (int mines = 1; mines < 25; mines++) {
            MinesBoard board = MinesBoard.create(10000, mines, (1 << mines) - 1);
            double probability = 1;
            for (int safe = 1; safe <= 25 - mines; safe++) {
                board.reveal(mines + safe - 1);
                probability *= (double) (26 - mines - safe) / (26 - safe);
                double expected = board.payout() * probability;
                assertTrue(expected <= 9800.00001 && expected > 9799, "mines=" + mines + " safe=" + safe);
            }
        }
    }
}
