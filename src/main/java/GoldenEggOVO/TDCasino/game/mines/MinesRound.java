package GoldenEggOVO.TDCasino.game.mines;

import GoldenEggOVO.TDCasino.game.StartedRound;

import java.util.Random;

public final class MinesRound extends StartedRound {
    private MinesBoard mines;
    private int mineCount = 3;

    public MinesRound(Random random) {
        super(random);
    }

    public int mineCount() {
        return mineCount;
    }

    public MinesBoard mines() {
        return mines == null ? null : mines.copy();
    }

    public long cashoutAmount() {
        return mines == null || mines.safeCount() == 0 ? 0 : normalizePayout(mines.payout());
    }

    public void changeMines(int delta) {
        if (!active) {
            mineCount = (int) Math.max(1, Math.min(24, (long) mineCount + delta));
        }
    }

    @Override
    public void start(long now) {
        if (active) {
            return;
        }
        beginRound();
        int mask = 0;
        while (Integer.bitCount(mask) < mineCount) {
            mask |= 1 << random.nextInt(25);
        }
        mines = MinesBoard.create(stake, mineCount, mask);
    }

    public void reveal(int cell) {
        if (!active) {
            return;
        }
        if (cell < 0 || cell >= 25) {
            throw new IllegalArgumentException("error.cell");
        }
        if ((mines.revealed() & (1 << cell)) != 0) {
            return;
        }
        mines.reveal(cell);
        if (mines.phase() == MinesBoard.Phase.LOST) {
            settle(0);
        } else if (mines.allSafe()) {
            cash(0);
        }
    }

    public boolean cash(long now) {
        if (!active || mines.safeCount() == 0) {
            return false;
        }
        long reward = cashoutAmount();
        mines.pay();
        settle(reward);
        return true;
    }
}
