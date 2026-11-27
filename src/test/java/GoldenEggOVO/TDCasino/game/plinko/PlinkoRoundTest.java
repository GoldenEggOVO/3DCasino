package GoldenEggOVO.TDCasino.game.plinko;

import static org.junit.jupiter.api.Assertions.*;

import GoldenEggOVO.TDCasino.economy.RoundFunding;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlinkoRoundTest {
    @Test
    void normalizesAndPaysBeforeTheBallLands() {
        var funding = new Funding();
        funding.limit = 100;
        var round = round(funding, 0);
        var flight = round.launch();
        assertNotNull(flight);
        assertEquals(List.of(125L), funding.charges);
        assertEquals(List.of(8261L), funding.rawReturns);
        assertEquals(List.of(100L), funding.payments);
        assertEquals(100, flight.returned());
        assertEquals(0, round.payout());
        for (int tick = 0; tick < 96; tick++) assertTrue(round.advance().isEmpty());
        assertEquals(List.of(flight), round.advance());
        assertEquals(100, round.payout());
        round.abandon();
        assertEquals(List.of(100L), funding.payments);
    }

    @Test
    void concurrentBallsKeepTheirOwnResultsAndLandIndependently() {
        var funding = new Funding();
        var round = round(funding, 0, 63);
        var first = round.launch();
        for (int tick = 0; tick < 4; tick++) round.advance();
        var second = round.launch();
        assertEquals(List.of(first, second), round.activeFlights());
        assertEquals(List.of(8261L, 25L), funding.payments);
        for (int tick = 0; tick < 92; tick++) assertTrue(round.advance().isEmpty());
        assertEquals(List.of(first), round.advance());
        assertEquals(8261, round.payout());
        assertEquals(List.of(second), round.activeFlights());
        for (int tick = 0; tick < 3; tick++) assertTrue(round.advance().isEmpty());
        assertEquals(List.of(second), round.advance());
        assertEquals(25, round.payout());
        assertTrue(round.activeFlights().isEmpty());
    }

    @Test
    void rapidRepeatsDoNotChargeOrConsumeAnotherRandomPath() {
        var funding = new Funding();
        var round = round(funding, 0, 4095);
        assertNotNull(round.launch());
        assertNull(round.launch());
        for (int tick = 0; tick < 3; tick++) round.advance();
        assertNull(round.launch());
        assertEquals(List.of(125L), funding.charges);
        round.advance();
        assertEquals(4095, round.launch().path());
        assertEquals(List.of(125L, 125L), funding.charges);
    }

    @Test
    void fullCapacityRejectsPaymentUntilThereIsSpaceAndTheCooldownEnds() {
        var funding = new Funding();
        var round = round(funding, 0);
        for (int index = 0; index < 25; index++) {
            assertNotNull(round.launch());
            if (index < 24) for (int tick = 0; tick < 4; tick++) round.advance();
        }
        assertFalse(round.canLaunch());
        assertNull(round.launch());
        assertEquals(25, funding.charges.size());
        assertEquals(1, round.advance().size());
        assertFalse(round.canLaunch());
        for (int tick = 0; tick < 3; tick++) round.advance();
        assertTrue(round.canLaunch());
        assertNotNull(round.launch());
        assertEquals(26, funding.charges.size());
    }

    @Test
    void refusedFundingDoesNotCreateABallOrConsumeItsPath() {
        var funding = new Funding();
        var round = round(funding, 4095);
        funding.refused = true;
        assertThrows(IllegalArgumentException.class, round::launch);
        assertTrue(round.activeFlights().isEmpty());
        assertTrue(funding.payments.isEmpty());
        funding.refused = false;
        assertEquals(4095, round.launch().path());
    }

    @Test
    void missingTransactionDoesNotCreateABall() {
        var funding = new Funding();
        funding.missingTransaction = true;
        var round = round(funding, 0);
        assertNull(round.launch());
        assertTrue(round.activeFlights().isEmpty());
        assertTrue(funding.rawReturns.isEmpty());
        assertTrue(funding.payments.isEmpty());
    }

    @Test
    void failedSettlementDoesNotCreateABall() {
        var funding = new Funding();
        funding.failedSettlement = true;
        var round = round(funding, 0);
        assertThrows(IllegalStateException.class, round::launch);
        assertTrue(round.activeFlights().isEmpty());
        assertEquals(0, round.payout());
    }

    @Test
    void zeroStakeStillProducesAPlayableBall() {
        var funding = new Funding();
        var round = round(funding, 0);
        round.setStake(0);
        assertNotNull(round.launch());
        assertEquals(List.of(0L), funding.charges);
        assertEquals(List.of(0L), funding.payments);
    }

    private static PlinkoRound round(Funding funding, int... paths) {
        var round = new PlinkoRound(new Random(1) {
            private int index;

            @Override
            public int nextInt(int bound) {
                assertEquals(4096, bound);
                return paths[Math.min(index++, paths.length - 1)];
            }
        });
        round.funding(funding);
        round.setStake(125);
        return round;
    }

    private static final class Funding implements RoundFunding {
        final List<Long> charges = new ArrayList<>();
        final List<Long> rawReturns = new ArrayList<>();
        final List<Long> payments = new ArrayList<>();
        final List<UUID> transactions = new ArrayList<>();
        long limit = Long.MAX_VALUE;
        boolean refused;
        boolean missingTransaction;
        boolean failedSettlement;

        public UUID begin(long cents) {
            if (refused) throw new IllegalArgumentException("error.balance");
            if (missingTransaction) return null;
            charges.add(cents);
            var transaction = UUID.randomUUID();
            transactions.add(transaction);
            return transaction;
        }

        public void increase(UUID transaction, long cents) {
            fail("Plinko must fund each ball separately");
        }

        public long normalize(long returned) {
            rawReturns.add(returned);
            return Math.min(limit, returned);
        }

        public void settle(UUID transaction, long returned) {
            assertEquals(transactions.getLast(), transaction);
            if (failedSettlement) throw new IllegalStateException("Settlement unavailable");
            payments.add(returned);
        }
    }
}
