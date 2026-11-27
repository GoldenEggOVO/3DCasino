package GoldenEggOVO.TDCasino.game;

import static org.junit.jupiter.api.Assertions.*;

import GoldenEggOVO.TDCasino.game.blackjack.BlackjackRound;
import GoldenEggOVO.TDCasino.game.dragon_tower.DragonTowerRound;
import GoldenEggOVO.TDCasino.game.duck_race.DuckRaceRound;
import GoldenEggOVO.TDCasino.game.hilo.HiloRound;
import GoldenEggOVO.TDCasino.game.keno.KenoRound;
import GoldenEggOVO.TDCasino.game.money_wheel.MoneyWheelRound;
import GoldenEggOVO.TDCasino.game.penguin_cross.PenguinCrossRound;
import GoldenEggOVO.TDCasino.game.slots.SlotsRound;
import GoldenEggOVO.TDCasino.game.wheel_of_fortune.WheelOfFortuneRound;
import GoldenEggOVO.TDCasino.rules.CasinoRules;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CasinoRuleAuditTest {
    @Test
    void blackjackReturnsTheStakeOnPushAndPrioritizesNaturalsOverOtherTwentyOnes() {
        assertEquals(0, CasinoRules.blackjackPayout(100, List.of(9, 22, 2), List.of(9, 22, 2)));
        assertEquals(0, CasinoRules.blackjackPayout(100, List.of(0, 1, 7), List.of(13, 25)));
        assertEquals(250, CasinoRules.blackjackPayout(100, List.of(0, 12), List.of(13, 14, 20)));
        assertEquals(100, CasinoRules.blackjackPayout(100, List.of(9, 6), List.of(22, 19)));
        assertEquals(200, CasinoRules.blackjackPayout(100, List.of(9, 7), List.of(22, 19)));
        assertEquals(0, CasinoRules.blackjackPayout(100, List.of(9, 5), List.of(22, 19)));
        assertEquals(200, CasinoRules.blackjackPayout(100, List.of(9, 5), List.of(22, 19, 6)));
    }

    @Test
    void blackjackSettlesInitialNaturalsBeforeAnyPlayerAction() {
        var round = new BlackjackRound(new Random(0) {
            @Override
            public int nextInt(int bound) {
                return bound == 13 ? 2 : bound - 1;
            }
        });
        round.setConfiguredStake(100);
        round.start(0);
        assertEquals(List.of(0, 12), round.player());
        assertEquals(250, round.payout());
        assertTrue(round.finished());
        round.hit();
        round.doubleDown();
        round.stand();
        assertEquals(4, round.cardsDrawn());
        assertEquals(100, round.stake());
        assertEquals(250, round.payout());
    }

    @Test
    void blackjackDealerStandsOnSoftSeventeen() {
        var round = new BlackjackRound(new Random(0) {
            @Override
            public int nextInt(int bound) {
                return switch (bound) {
                    case 14 -> 1;
                    case 6 -> 3;
                    default -> bound - 1;
                };
            }
        });
        round.setConfiguredStake(100);
        round.start(0);
        assertEquals(List.of(13, 5), round.dealer());
        round.stand();
        assertEquals(4, round.cardsDrawn());
        assertEquals(0, round.payout());
    }

    @Test
    void hiloEnumeratedOutcomesMatchStrictThresholdBoundariesInBothDirections() {
        int[] thresholds = {1, 50, 98};
        int[][] winningCounts = {{1, 98}, {50, 49}, {98, 1}};
        long[][] winningReturns = {{245000, 2500}, {4900, 5000}, {2500, 245000}};
        for (int index = 0; index < thresholds.length; index++) {
            for (int direction = 0; direction < 2; direction++) {
                int winners = 0;
                long returned = 0;
                for (int roll = 0; roll < 100; roll++) {
                    var round = new HiloRound(rolls(roll));
                    round.setStake(2450);
                    round.setThreshold(thresholds[index]);
                    round.action(direction == 0 ? "under" : "over");
                    round.action("play");
                    if (round.payout() > 0) {
                        winners++;
                        assertEquals(winningReturns[index][direction], round.payout());
                    }
                    returned += round.payout();
                }
                assertEquals(winningCounts[index][direction], winners);
                assertEquals(245000, returned);
            }
        }
    }

    @Test
    void slotsEnumeratedSingleRowOutcomesKeepPairsTriplesAndOddCentStakeSplitting() {
        long total = 0;
        for (int a = 0; a < 5; a++) {
            for (int b = 0; b < 5; b++) {
                for (int c = 0; c < 5; c++) {
                    var round = new SlotsRound(rolls(a, b, c, 0, 1, 2, 0, 1, 2));
                    round.setStake(300);
                    round.action("play");
                    total += round.payout();
                }
            }
        }
        assertEquals(9000, total);
        var odd = new SlotsRound(rolls(0, 1, 2, 0, 1, 2, 4, 4, 4));
        odd.setStake(1);
        odd.action("play");
        assertEquals(10, odd.payout());
    }

    @Test
    void invalidSelectionsCannotStartRoundsOrConsumeRandomOutcomes() {
        var unused = new Random(0) {
            @Override
            public int nextInt(int bound) {
                fail("An unavailable action consumed a random outcome");
                return 0;
            }
        };
        var duck = new DuckRaceRound(unused);
        var wheel = new MoneyWheelRound(unused);
        var keno = new KenoRound(unused);
        var tower = new DragonTowerRound(unused);
        var penguin = new PenguinCrossRound(unused);
        for (String invalid : new String[] {null, "", "cash", "select:-1", "select:41", "select:bad", "select:2147483648"}) {
            duck.action(invalid);
            wheel.action(invalid);
            keno.action(invalid);
            tower.action(invalid);
            penguin.action(invalid);
        }
        keno.action("play");
        assertEquals(0, duck.sequence());
        assertEquals(0, wheel.sequence());
        assertEquals(0, keno.sequence());
        assertEquals(0, tower.sequence());
        assertEquals(0, penguin.sequence());
        tower.action("play");
        penguin.action("play");
        tower.action("cash");
        penguin.action("cash");
        assertTrue(tower.active());
        assertTrue(penguin.active());
        assertEquals(0, tower.floors());
        assertEquals(0, penguin.steps());
    }

    @Test
    void fortuneReplayCannotChargeOrReplaceTheRoundBeforeItFinishes() {
        var round = new WheelOfFortuneRound(rolls(0, 10, 5));
        round.setStake(100);
        round.action("play");
        round.action("play");
        assertEquals(1, round.sequence());
        assertEquals(0, round.segment());
        round.replayFortune();
        round.replayFortune();
        assertEquals(1, round.sequence());
        assertEquals(500, round.payout());
        round.replayFortune();
        assertEquals(5, round.segment());
        assertEquals(500, round.payout());
    }

    private static Random rolls(int... values) {
        return new Random(0) {
            int index;

            @Override
            public int nextInt(int bound) {
                return values[index++ % values.length];
            }
        };
    }
}
