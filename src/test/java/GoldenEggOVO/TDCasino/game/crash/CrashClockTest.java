package GoldenEggOVO.TDCasino.game.crash;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;

class CrashClockTest {
    @Test
    void clockRollbackCannotReduceAnAlreadyReachedCashoutMultiplier() {
        var round = new CrashRound(new Random(0) {
            @Override
            public long nextLong(long bound) {
                return 804_000_000L;
            }
        });
        round.setConfiguredStake(1000);
        round.start(100);
        round.tick(3100);
        assertEquals(200, round.multiplier());
        round.tick(1600);
        assertEquals(200, round.multiplier());
        assertTrue(round.cash(1600));
        assertEquals(2000, round.payout());
    }
}
