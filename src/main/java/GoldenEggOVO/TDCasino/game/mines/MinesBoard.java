package GoldenEggOVO.TDCasino.game.mines;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MinesBoard {
    public enum Phase {
        ACTIVE,
        PAID,
        LOST
    }

    private long stake;
    private int mines;
    private int mask;
    private int revealed;
    private Phase phase;

    private MinesBoard() {}

    public static MinesBoard create(long stake, int mines, int mask) {
        if (stake < 0
                || stake > 1_000_000_000L
                || mines < 1
                || mines > 24
                || Integer.bitCount(mask) != mines
                || (mask >>> 25) != 0) {
            throw new IllegalArgumentException("error.mines-stake");
        }
        MinesBoard board = new MinesBoard();
        board.stake = stake;
        board.mines = mines;
        board.mask = mask;
        board.phase = Phase.ACTIVE;
        return board;
    }

    public void reveal(int cell) {
        if (phase != Phase.ACTIVE || allSafe()) {
            throw new IllegalStateException("error.reveal-unavailable");
        }
        if (cell < 0 || cell >= 25) {
            throw new IllegalArgumentException("error.cell");
        }
        int bit = 1 << cell;
        if ((revealed & bit) != 0) {
            throw new IllegalStateException("error.revealed");
        }
        revealed |= bit;
        if ((mask & bit) != 0) {
            phase = Phase.LOST;
        }
    }

    public int safeCount() {
        return Integer.bitCount(revealed & ~mask);
    }

    public boolean allSafe() {
        return safeCount() == 25 - mines;
    }

    public boolean finished() {
        return phase == Phase.PAID || phase == Phase.LOST;
    }

    public long payout() {
        if (phase == Phase.LOST) {
            return 0;
        }
        int safe = safeCount();
        if (safe == 0) {
            throw new IllegalStateException("error.cash-needs-safe");
        }
        return BigDecimal.valueOf(stake)
                .multiply(new BigDecimal("0.98"))
                .multiply(new BigDecimal(choose(25, safe)))
                .divide(new BigDecimal(choose(25 - mines, safe)), 0, RoundingMode.DOWN)
                .longValueExact();
    }

    private static long choose(int n, int k) {
        long value = 1;
        for (int i = 1; i <= k; i++) {
            value = value * (n - i + 1) / i;
        }
        return value;
    }

    public int mask() {
        return mask;
    }

    public int revealed() {
        return revealed;
    }

    public Phase phase() {
        return phase;
    }

    public long stake() {
        return stake;
    }

    public void pay() {
        if (phase != Phase.ACTIVE || safeCount() == 0) {
            throw new IllegalStateException("error.cash-needs-safe");
        }
        phase = Phase.PAID;
    }

    public MinesBoard copy() {
        MinesBoard board = new MinesBoard();
        board.stake = stake;
        board.mines = mines;
        board.mask = mask;
        board.revealed = revealed;
        board.phase = phase;
        return board;
    }
}
