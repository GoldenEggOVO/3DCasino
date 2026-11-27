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
import GoldenEggOVO.TDCasino.economy.RoundFunding;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

class GameRoundFundingTest {
    @Test
    void defaultRoundsPlayWithZeroAmounts() {
        PenguinCrossRound crossing = new PenguinCrossRound(new Random(4));
        crossing.action("play");
        assertEquals(0, crossing.stake());
        assertTrue(crossing.active());
        BlackjackRound blackjack = new BlackjackRound(new Random(4));
        blackjack.start(0);
        assertEquals(0, blackjack.configuredStake());
        assertEquals(0, blackjack.stake());
    }

    @Test
    void amountAcceptsCentsAndRejectsChangesDuringPlay() {
        PenguinCrossRound round = new PenguinCrossRound(new Random(4));
        for (long invalid : new long[] {Long.MIN_VALUE, -1, 1_000_000_001L, Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> round.setStake(invalid));
        }
        round.setStake(1);
        round.action("play");
        assertThrows(IllegalStateException.class, () -> round.setStake(0));
        assertEquals(1, round.stake());
    }

    @Test
    void configuredAmountIsCapturedBeforeFunding() {
        BlackjackRound round = new BlackjackRound(new CountingRandom());
        AccountFunding account = new AccountFunding();
        round.funding(account);
        round.setConfiguredStake(2457);
        round.start(0);
        assertEquals(7543, account.balance);
        assertEquals(2457, round.stake());
        assertNotNull(round.paymentId());
    }

    @Test
    void rejectedStartDoesNotDrawCardsOrAdvanceTheRound() {
        CountingRandom random = new CountingRandom();
        BlackjackRound round = new BlackjackRound(random);
        AccountFunding account = new AccountFunding();
        account.rejectDebit = true;
        round.funding(account);
        round.setConfiguredStake(100);
        assertThrows(IllegalStateException.class, () -> round.start(0));
        assertEquals(0, random.calls);
        assertEquals(0, round.sequence());
        assertNull(round.paymentId());
        assertTrue(round.player().isEmpty());
        assertTrue(round.deck().isEmpty());
        assertFalse(round.active());
    }

    @Test
    void rejectedRestartPreservesThePreviousHandAndRandomState() {
        CountingRandom random = new CountingRandom();
        BlackjackRound round = new BlackjackRound(random);
        AccountFunding account = new AccountFunding();
        round.funding(account);
        round.setConfiguredStake(100);
        round.start(0);
        round.stand();
        var player = round.player();
        var dealer = round.dealer();
        var deck = round.deck();
        int calls = random.calls;
        long sequence = round.sequence();
        long returned = round.payout();
        account.rejectDebit = true;
        assertThrows(IllegalStateException.class, () -> round.start(1));
        assertEquals(player, round.player());
        assertEquals(dealer, round.dealer());
        assertEquals(deck, round.deck());
        assertEquals(calls, random.calls);
        assertEquals(sequence, round.sequence());
        assertEquals(returned, round.payout());
        assertTrue(round.finished());
    }

    @Test
    void rejectedRestartPreservesThePreviousDoubledStake() {
        BlackjackRound round = new BlackjackRound(new CountingRandom());
        AccountFunding account = new AccountFunding();
        round.funding(account);
        round.setConfiguredStake(101);
        round.start(0);
        round.doubleDown();
        assertEquals(202, round.stake());
        account.rejectDebit = true;
        assertThrows(IllegalStateException.class, () -> round.start(1));
        assertEquals(202, round.stake());
        assertEquals(101, round.configuredStake());
    }

    @Test
    void rejectedMinesStartDoesNotGenerateAMask() {
        CyclingRandom random = new CyclingRandom();
        MinesRound round = new MinesRound(random);
        AccountFunding account = new AccountFunding();
        account.rejectDebit = true;
        round.funding(account);
        round.setConfiguredStake(100);
        assertThrows(IllegalStateException.class, () -> round.start(0));
        assertNull(round.mines());
        assertEquals(0, random.calls);
        assertEquals(0, round.sequence());
        assertFalse(round.active());
    }

    @Test
    void rejectedMinesRestartKeepsItsPreviousBoardAndRandomState() {
        CyclingRandom random = new CyclingRandom();
        MinesRound round = new MinesRound(random);
        AccountFunding account = new AccountFunding();
        round.funding(account);
        round.setConfiguredStake(100);
        round.start(0);
        round.reveal(3);
        round.cash(0);
        int calls = random.calls;
        var board = round.mines();
        UUID payment = round.paymentId();
        account.rejectDebit = true;
        assertThrows(IllegalStateException.class, () -> round.start(1));
        assertEquals(board.mask(), round.mines().mask());
        assertEquals(board.revealed(), round.mines().revealed());
        assertEquals(board.phase(), round.mines().phase());
        assertEquals(111, round.payout());
        assertEquals(calls, random.calls);
        assertEquals(payment, round.paymentId());
        assertEquals(1, round.sequence());
    }

    @Test
    void rejectedDoublePreservesCardsStakeAndActiveState() {
        CountingRandom random = new CountingRandom();
        BlackjackRound round = new BlackjackRound(random);
        AccountFunding account = new AccountFunding();
        round.funding(account);
        round.setConfiguredStake(100);
        round.start(0);
        var player = round.player();
        var dealer = round.dealer();
        int drawn = round.cardsDrawn();
        int calls = random.calls;
        account.rejectDebit = true;
        assertThrows(IllegalStateException.class, round::doubleDown);
        assertEquals(player, round.player());
        assertEquals(dealer, round.dealer());
        assertEquals(drawn, round.cardsDrawn());
        assertEquals(calls, random.calls);
        assertEquals(100, round.stake());
        assertEquals(9900, account.balance);
        assertTrue(round.active());
    }

    @Test
    void doubleFundsTheExtraAmountAndReturnsTheFullDoubledStakeOnATie() {
        BlackjackRound round = new BlackjackRound(new CountingRandom());
        AccountFunding account = new AccountFunding();
        round.funding(account);
        round.setConfiguredStake(101);
        round.start(0);
        round.doubleDown();
        assertEquals(202, round.stake());
        assertEquals(202, round.payout());
        assertEquals(10000, account.balance);
        assertEquals(1, account.returns.size());
    }

    @Test
    void completedResultsCannotBePaidAgainByRepeatedActionsOrAbandonment() {
        SlotsRound round = new SlotsRound(new CountingRandom());
        AccountFunding account = new AccountFunding();
        round.funding(account);
        round.setStake(100);
        round.action("play");
        assertEquals(1000, round.payout());
        assertEquals(10900, account.balance);
        round.abandon();
        round.abandon();
        assertEquals(10900, account.balance);
        assertEquals(1, account.returns.size());
        assertTrue(round.finished());
    }

    @Test
    void abandoningProgressForfeitsTheUncollectedAmount() {
        PenguinCrossRound round = new PenguinCrossRound(new CountingRandom());
        AccountFunding account = new AccountFunding();
        round.funding(account);
        round.setStake(100);
        round.action("play");
        round.action("step");
        assertEquals(125, round.payout());
        round.abandon();
        assertEquals(0, round.payout());
        assertEquals(9900, account.balance);
        assertFalse(round.active());
        assertTrue(round.finished());
    }

    @Test
    void crashPaysAtCollectionAndAnimationCannotPayAgain() {
        CrashRound round = new CrashRound(crashRandom());
        AccountFunding account = new AccountFunding();
        round.funding(account);
        round.setConfiguredStake(2500);
        round.start(100);
        assertTrue(round.cash(400));
        assertTrue(round.active());
        assertEquals(10250, account.balance);
        round.tick(12100);
        assertEquals(2750, round.payout());
        assertEquals(10250, account.balance);
        assertEquals(1, account.returns.size());
        assertFalse(round.cash(12101));
    }

    @Test
    void abandoningCashedCrashRetainsItsCapturedReturn() {
        CrashRound round = new CrashRound(crashRandom());
        AccountFunding account = new AccountFunding();
        round.funding(account);
        round.setConfiguredStake(2500);
        round.start(100);
        round.cash(400);
        round.abandon();
        assertEquals(2750, round.payout());
        assertEquals(10250, account.balance);
        assertFalse(round.active());
        assertTrue(round.finished());
    }

    @Test
    void everyGameCanFinishWithZeroStake() {
        DuckRaceRound ducks = new DuckRaceRound(new CountingRandom());
        ducks.action("play");
        assertZeroResult(ducks);
        HiloRound hilo = new HiloRound(new CountingRandom());
        hilo.action("play");
        assertZeroResult(hilo);
        KenoRound keno = new KenoRound(new CountingRandom());
        keno.action("select:1");
        keno.action("play");
        assertZeroResult(keno);
        MoneyWheelRound money = new MoneyWheelRound(new CountingRandom());
        money.action("play");
        assertZeroResult(money);
        SlotsRound slots = new SlotsRound(new CountingRandom());
        slots.action("play");
        assertZeroResult(slots);
        WheelOfFortuneRound fortune = new WheelOfFortuneRound(new CountingRandom());
        fortune.action("play");
        assertZeroResult(fortune);
        DragonTowerRound tower = new DragonTowerRound(new CountingRandom());
        tower.action("play");
        for (int i = 0; i < 6; i++) tower.action("select:0");
        assertZeroResult(tower);
        PenguinCrossRound penguin = new PenguinCrossRound(new CountingRandom());
        penguin.action("play");
        for (int i = 0; i < 8; i++) penguin.action("step");
        assertZeroResult(penguin);
        BlackjackRound blackjack = new BlackjackRound(new CountingRandom());
        blackjack.start(0);
        blackjack.stand();
        assertZeroResult(blackjack);
        CrashRound crash = new CrashRound(crashRandom());
        crash.start(0);
        crash.tick(12000);
        assertZeroResult(crash);
        MinesRound mines = new MinesRound(new Random(7));
        mines.start(0);
        int safe = 0;
        while ((mines.mines().mask() & (1 << safe)) != 0) safe++;
        mines.reveal(safe);
        mines.cash(0);
        assertZeroResult(mines);
    }

    @Test
    void finalAmountIsCapturedBeforeASettlementFailure() {
        SlotsRound round = new SlotsRound(new CountingRandom());
        AccountFunding account = new AccountFunding();
        account.rejectSettlement = true;
        round.funding(account);
        round.setStake(100);
        assertThrows(IllegalStateException.class, () -> round.action("play"));
        assertEquals(1000, round.payout());
        assertFalse(round.active());
        assertTrue(round.finished());
        assertEquals(9900, account.balance);
    }

    @Test
    void crashKeepsTheCapturedAmountWhenItsFirstSettlementFails() {
        CrashRound round = new CrashRound(crashRandom());
        AccountFunding account = new AccountFunding();
        round.funding(account);
        round.setConfiguredStake(2500);
        round.start(100);
        account.rejectSettlement = true;
        assertThrows(IllegalStateException.class, () -> round.cash(400));
        assertEquals(2750, round.payout());
        assertTrue(round.cashed());
        account.rejectSettlement = false;
        round.abandon();
        assertEquals(2750, round.payout());
        assertEquals(10250, account.balance);
    }

    @Test
    void finalResultUsesTheBanksActualPrecision() {
        SlotsRound round = new SlotsRound(new CountingRandom());
        AccountFunding account = new AccountFunding();
        account.currencyUnit = 100;
        round.funding(account);
        round.setStake(101);
        round.action("play");
        assertEquals(1000, round.payout());
        assertEquals(10899, account.balance);
    }

    @Test
    void progressAndCollectedCrashUseTheBanksActualPrecision() {
        AccountFunding account = new AccountFunding();
        account.currencyUnit = 100;
        PenguinCrossRound penguin = new PenguinCrossRound(new CountingRandom());
        penguin.funding(account);
        penguin.setStake(100);
        penguin.action("play");
        penguin.action("step");
        assertEquals(100, penguin.payout());
        penguin.action("cash");
        CrashRound crash = new CrashRound(crashRandom());
        crash.funding(account);
        crash.setConfiguredStake(100);
        crash.start(100);
        crash.cash(400);
        assertEquals(100, crash.payout());
        crash.abandon();
        assertEquals(10000, account.balance);
    }

    @Test
    void minesQuotesAndPaysTheSameNormalizedReturn() {
        MinesRound round = new MinesRound(new Random(7));
        AccountFunding account = new AccountFunding();
        account.currencyUnit = 100;
        round.funding(account);
        round.setConfiguredStake(100);
        round.start(0);
        int safe = 0;
        while ((round.mines().mask() & (1 << safe)) != 0) safe++;
        round.reveal(safe);
        assertEquals(100, round.cashoutAmount());
        assertTrue(round.cash(0));
        assertEquals(100, round.payout());
        assertEquals(10000, account.balance);
    }

    private static void assertZeroResult(GameRound round) {
        assertTrue(round.finished(), round.getClass().getSimpleName());
        assertEquals(0, round.stake());
        assertEquals(0, round.payout());
    }

    private static Random crashRandom() {
        return new Random(0) {
            @Override
            public long nextLong(long bound) {
                return 804_000_000L;
            }
        };
    }

    private static final class CountingRandom extends Random {
        private int calls;

        @Override
        public int nextInt(int bound) {
            calls++;
            return bound - 1;
        }
    }

    private static final class CyclingRandom extends Random {
        private int calls;

        @Override
        public int nextInt(int bound) {
            return calls++ % bound;
        }
    }

    private static final class AccountFunding implements RoundFunding {
        private final Map<UUID, Long> stakes = new HashMap<>();
        private final Map<UUID, Long> returns = new HashMap<>();
        private long balance = 10000;
        private boolean rejectDebit;
        private boolean rejectSettlement;
        private long currencyUnit = 1;

        @Override
        public long normalize(long returned) {
            return returned - returned % currencyUnit;
        }

        @Override
        public UUID begin(long cents) {
            debit(cents);
            UUID id = UUID.randomUUID();
            stakes.put(id, cents);
            return id;
        }

        @Override
        public void increase(UUID id, long cents) {
            debit(cents);
            stakes.put(id, Math.addExact(stakes.get(id), cents));
        }

        private void debit(long cents) {
            if (rejectDebit || cents > balance) {
                throw new IllegalStateException("Insufficient funds");
            }
            balance -= cents;
        }

        @Override
        public void settle(UUID id, long returned) {
            if (rejectSettlement) {
                throw new IllegalStateException("Settlement unavailable");
            }
            Long previous = returns.putIfAbsent(id, returned);
            if (previous == null) {
                balance += returned;
            } else if (previous != returned) {
                throw new IllegalStateException("Conflicting return");
            }
        }
    }
}
