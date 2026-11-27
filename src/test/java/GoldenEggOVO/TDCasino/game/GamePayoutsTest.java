package GoldenEggOVO.TDCasino.game;

import static org.junit.jupiter.api.Assertions.*;

import GoldenEggOVO.TDCasino.game.blackjack.BlackjackRound;
import GoldenEggOVO.TDCasino.game.crash.CrashRound;
import GoldenEggOVO.TDCasino.game.dragon_tower.DragonTowerRound;
import GoldenEggOVO.TDCasino.game.duck_race.DuckRaceRound;
import GoldenEggOVO.TDCasino.game.hilo.HiloRound;
import GoldenEggOVO.TDCasino.game.keno.KenoRound;
import GoldenEggOVO.TDCasino.game.mines.MinesRound;
import GoldenEggOVO.TDCasino.game.money_wheel.MoneyWheelRound;
import GoldenEggOVO.TDCasino.game.penguin_cross.PenguinCrossRound;
import GoldenEggOVO.TDCasino.game.slots.SlotsRound;
import GoldenEggOVO.TDCasino.game.wheel_of_fortune.WheelOfFortuneRound;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;

class GamePayoutsTest {
    @Test
    void slotsPayOnlyHorizontalRowsAndSplitTheWholeStake() {
        SlotsRound rows = new SlotsRound(rolls(4, 4, 4, 1, 1, 2, 0, 2, 3));
        rows.setStake(1000);
        rows.action("play");
        assertEquals(3663, rows.payout());
        SlotsRound vertical = new SlotsRound(rolls(0, 1, 2, 0, 1, 2, 0, 1, 2));
        vertical.setStake(1000);
        vertical.action("play");
        assertEquals(0, vertical.payout());
        SlotsRound pairs = new SlotsRound(rolls(0, 0, 1));
        pairs.setStake(1000);
        pairs.action("play");
        assertEquals(1000, pairs.payout());
    }

    @Test
    void fortuneKeepsTheOriginalFractionalAndZeroReturns() {
        for (int[] outcome : new int[][] {{2, 100}, {3, 500}, {4, 250}, {5, 5000}, {15, 0}}) {
            WheelOfFortuneRound round = new WheelOfFortuneRound(rolls(outcome[0]));
            round.setStake(1000);
            round.action("play");
            assertEquals(outcome[1], round.payout());
            assertTrue(round.finished());
            assertFalse(round.active());
        }
    }

    @Test
    void moneyWheelPreservesCategoryWeightsAndPayouts() {
        for (int category = 0; category < 4; category++) {
            long returned = 0;
            for (int segment = 0; segment < 20; segment++) {
                MoneyWheelRound round = new MoneyWheelRound(rolls(segment));
                round.setStake(1000);
                round.action("select:" + category);
                round.action("play");
                assertEquals(segment, round.segment());
                returned += round.payout();
            }
            assertEquals(new long[] {20000, 18000, 15000, 10000}[category], returned);
        }
    }

    @Test
    void duckSelectionsDoNotChangeTheDrawAndReturnFourTimesTheStake() {
        DuckRaceRound round = new DuckRaceRound(rolls(3));
        round.setStake(1000);
        round.action("select:3");
        round.action("play");
        assertEquals(3, round.winner());
        assertEquals(4000, round.payout());
    }

    @Test
    void kenoCapsSelectionsAndDrawsWithoutReplacement() {
        KenoRound round = new KenoRound(new Random(17));
        for (int number = 1; number <= 11; number++) round.action("select:" + number);
        assertEquals(10, round.selected().size());
        assertFalse(round.available("select:0"));
        assertFalse(round.available("select:41"));
        round.action("play");
        assertEquals(10, round.drawnNumbers().size());
        assertEquals(10, new HashSet<>(round.drawnNumbers()).size());
        assertTrue(round.drawnNumbers().stream().allMatch(number -> number >= 1 && number <= 40));
        KenoRound winner = new KenoRound(rolls(0));
        winner.setStake(1000);
        winner.action("select:1");
        winner.action("play");
        assertEquals(4000, winner.payout());
    }

    @Test
    void hiloKeepsStrictComparisonAndPublishedThresholdLimits() {
        HiloRound equality = new HiloRound(rolls(50));
        equality.setStake(1000);
        equality.action("play");
        assertEquals(0, equality.payout());
        equality.setThreshold(-100);
        assertEquals(1, equality.threshold());
        equality.setThreshold(100);
        assertEquals(98, equality.threshold());
        HiloRound winner = new HiloRound(rolls(99));
        winner.setStake(1000);
        winner.action("play");
        assertEquals(2040, winner.payout());
        assertEquals(List.of(99), winner.rolls());
    }

    @Test
    void towerKeepsFourLanesWithOneTrapAndSixFloors() {
        for (int lane = 0; lane < 4; lane++) {
            DragonTowerRound round = new DragonTowerRound(rolls(3));
            round.setStake(1000);
            round.action("play");
            assertFalse(round.available("select:4"));
            round.action("select:" + lane);
            assertEquals(lane == 3 ? 0 : 1333, round.payout());
            assertEquals(List.of(3), round.traps());
        }
        DragonTowerRound round = new DragonTowerRound(rolls(2));
        round.setStake(1000);
        round.action("play");
        for (int floor = 0; floor < 6; floor++) round.action("select:1");
        assertEquals(6, round.floors());
        assertEquals(5619, round.payout());
        round.action("select:1");
        assertEquals(6, round.floors());
        assertEquals(5619, round.payout());
    }

    @Test
    void penguinKeepsTheTwentyPercentLossBoundaryAndEightStepLimit() {
        PenguinCrossRound loss = new PenguinCrossRound(rolls(19));
        loss.setStake(1000);
        loss.action("play");
        loss.action("step");
        assertEquals(0, loss.payout());
        assertTrue(loss.finished());
        PenguinCrossRound winner = new PenguinCrossRound(rolls(20));
        winner.setStake(1000);
        winner.action("play");
        assertFalse(winner.available("cash"));
        for (int step = 0; step < 8; step++) winner.action("step");
        assertEquals(8, winner.steps());
        assertEquals(5960, winner.payout());
        winner.action("step");
        winner.action("cash");
        assertEquals(5960, winner.payout());
    }

    @Test
    void crashCannotCollectPastItsPointWithoutAnAnimationTick() {
        CrashRound round = new CrashRound(new Random(0) {
            @Override
            public long nextLong(long bound) {
                return 0;
            }
        });
        round.setConfiguredStake(1000);
        round.start(100);
        assertFalse(round.cash(100));
        assertEquals(98, round.crashPoint());
        assertEquals(0, round.payout());
        assertTrue(round.finished());
    }

    @Test
    void minesDifficultyLocksDuringPlayAndLastSafeCellCollectsAutomatically() {
        MinesRound round = new MinesRound(new Random(7));
        round.changeMines(-100);
        assertEquals(1, round.mineCount());
        round.changeMines(100);
        assertEquals(24, round.mineCount());
        round.setConfiguredStake(2500);
        round.start(0);
        round.changeMines(-1);
        assertEquals(24, round.mineCount());
        assertEquals(24, Integer.bitCount(round.mines().mask()));
        int safe = 0;
        while ((round.mines().mask() & (1 << safe)) != 0) safe++;
        round.reveal(safe);
        assertEquals(61250, round.payout());
        assertFalse(round.active());
        assertTrue(round.finished());
        round.changeMines(-1);
        assertEquals(23, round.mineCount());
    }

    @Test
    void blackjackUsesOneDeckAndCannotDoubleAfterHitting() {
        BlackjackRound round = new BlackjackRound(new Random(0) {
            @Override
            public int nextInt(int bound) {
                return bound - 1;
            }
        });
        round.setConfiguredStake(1000);
        round.start(0);
        assertEquals(52, round.deck().stream().distinct().count());
        round.hit();
        assertEquals(3, round.player().size());
        assertEquals(5, round.cardsDrawn());
        round.doubleDown();
        assertEquals(1000, round.stake());
        assertEquals(5, round.cardsDrawn());
        round.stand();
        assertEquals(1000, round.payout());
    }

    private static Random rolls(int... values) {
        return new Random(0) {
            int index;

            @Override
            public int nextInt(int bound) {
                return Math.floorMod(values[index++ % values.length], bound);
            }
        };
    }
}
