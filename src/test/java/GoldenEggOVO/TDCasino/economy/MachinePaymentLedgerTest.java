package GoldenEggOVO.TDCasino.economy;

import GoldenEggOVO.TDCasino.Core.economy.EconomyAccess;

import GoldenEggOVO.TDCasino.Core.text.RuleViolation;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class MachinePaymentLedgerTest {
    @TempDir Path directory;
    final UUID player = UUID.randomUUID();
    final Bank bank = new Bank();
    final AtomicBoolean enabled = new AtomicBoolean(true);
    final List<String> warnings = new ArrayList<>();
    long minimum = 100;
    long maximum = 10_000;

    Path journal() { return directory.resolve("casino-payments.json"); }

    MachinePaymentLedger payments() {
        return new MachinePaymentLedger(journal(), () -> bank, enabled::get, () -> minimum, () -> maximum, warnings::add);
    }

    @Test
    void zeroAndDisabledRoundsNeverResolveTheBankOrCreateAJournal() {
        var payments = new MachinePaymentLedger(journal(), () -> { throw new AssertionError("Bank accessed"); },
                enabled::get, () -> 100, () -> 1000, warnings::add);
        var funding = payments.bind(player, "slots");
        UUID zero = funding.begin(0);
        assertNotNull(zero);
        funding.increase(zero, 500);
        funding.settle(zero, 1500);
        enabled.set(false);
        UUID disabled = funding.begin(500);
        assertNotEquals(zero, disabled);
        funding.settle(disabled, 1000);
        payments.close();
        assertFalse(Files.exists(journal()));
    }

    @Test
    void existingPaidRoundCanIncreaseAndSettle() {
        var payments = payments();
        var funding = payments.bind(player, "blackjack");
        UUID round = funding.begin(1000);
        funding.increase(round, 1000);
        funding.settle(round, 4000);
        assertEquals(120, bank.funds);
    }

    @Test
    void deferredRegionCreditSurvivesShutdownAndRepeatedCallbacksPayOnlyOnce() {
        var queued = new ArrayList<UUID>();
        var payments = new MachinePaymentLedger(journal(), () -> bank, enabled::get,
                () -> minimum, () -> maximum, warnings::add, queued::add);
        var funding = payments.bind(player, "slots");
        UUID round = funding.begin(1000);
        funding.settle(round, 2000);
        assertEquals(List.of(player), queued);
        assertEquals(90, bank.funds);
        assertTrue(readJournal().contains("CREDIT_PENDING"));
        payments.close();
        payments.retry(player);
        assertEquals(0, bank.deposits);

        var recovered = new MachinePaymentLedger(journal(), () -> bank, enabled::get,
                () -> minimum, () -> maximum, warnings::add, queued::add);
        recovered.recover();
        recovered.retry(UUID.randomUUID());
        assertEquals(0, bank.deposits);
        for (UUID id : queued) recovered.retry(id);
        assertEquals(1, bank.deposits);
        assertEquals(110, bank.funds);
    }

    @Test
    void settledZeroRoundsKeepABoundedReplayWindowAndRejectUnknownIds() {
        var funding = payments().bind(player, "slots");
        UUID first = funding.begin(0);
        funding.settle(first, 0);
        UUID latest = first;
        for (int index = 0; index < 128; index++) {
            latest = funding.begin(0);
            funding.settle(latest, 0);
        }
        UUID replay = latest;
        assertDoesNotThrow(() -> funding.settle(replay, 0));
        assertThrows(RuleViolation.class, () -> funding.settle(first, 0));
        assertThrows(RuleViolation.class, () -> funding.settle(UUID.randomUUID(), 0));
        assertEquals(0, bank.withdrawals);
        assertEquals(0, bank.deposits);
        assertFalse(Files.exists(journal()));
    }

    @Test
    void paidRoundWithdrawsAfterDurableIntentAndReturnsTheFullAwardOnce() {
        var payments = payments();
        bank.beforeWithdraw = () -> assertTrue(readJournal().contains("DEBIT_IN_FLIGHT"));
        var funding = payments.bind(player, "blackjack");
        UUID round = funding.begin(1000);
        assertEquals(90, bank.funds);
        assertTrue(payments.blocked(player));
        bank.beforeDeposit = () -> {
            String stored = readJournal();
            assertTrue(stored.contains("CREDIT_IN_FLIGHT"));
            assertTrue(stored.contains("2500"));
        };
        funding.settle(round, 2500);
        funding.settle(round, 2500);
        assertEquals(115, bank.funds);
        assertEquals(1, bank.deposits);
        assertFalse(payments.blocked(player));
        payments().recover();
        assertEquals(115, bank.funds);
        assertEquals(1, bank.deposits);
    }

    @Test
    void closedHistoryIsBoundedWhileTheCurrentBindingRemainsIdempotent() {
        var payments = payments();
        var held = payments.bind(player, "crash");
        UUID first = held.begin(100);
        held.settle(first, 100);
        var funding = payments.bind(player, "slots");
        UUID latest = null;
        for (int index = 0; index < 270; index++) {
            latest = funding.begin(100);
            funding.settle(latest, 100);
        }
        var records = JsonParser.parseString(readJournal()).getAsJsonObject().getAsJsonArray("rounds");
        assertEquals(256, records.size());
        assertFalse(readJournal().contains(first.toString()));
        int deposits = bank.deposits;
        assertDoesNotThrow(() -> held.settle(first, 9000));
        assertEquals(deposits, bank.deposits);
        assertEquals(100, bank.funds);
        assertThrows(RuleViolation.class, () -> payments.bind(player, "crash").settle(first, 9000));
        var recovered = payments();
        recovered.recover();
        UUID replay = latest;
        assertDoesNotThrow(() -> recovered.bind(player, "slots").settle(replay, 9000));
        assertThrows(RuleViolation.class, () -> recovered.bind(player, "slots").settle(UUID.randomUUID(), 9000));
        assertEquals(deposits, bank.deposits);
    }

    @Test
    void historyCompactionPreservesPendingAndReviewPaymentsAcrossRecovery() {
        var payments = payments();
        UUID pendingPlayer = UUID.randomUUID(), reviewPlayer = UUID.randomUUID();
        var pending = payments.bind(pendingPlayer, "slots");
        UUID pendingId = pending.begin(100);
        bank.credit = EconomyAccess.Transfer.REJECTED;
        pending.settle(pendingId, 200);
        bank.debit = EconomyAccess.Transfer.UNCERTAIN;
        assertThrows(RuleViolation.class, () -> payments.bind(reviewPlayer, "slots").begin(100));
        bank.debit = EconomyAccess.Transfer.SUCCESS;
        bank.credit = EconomyAccess.Transfer.SUCCESS;
        var funding = payments.bind(player, "slots");
        for (int index = 0; index < 270; index++) {
            UUID id = funding.begin(100);
            funding.settle(id, 100);
        }
        assertTrue(readJournal().contains(pendingId.toString()));
        assertTrue(readJournal().contains("REVIEW"));
        assertEquals(258, JsonParser.parseString(readJournal()).getAsJsonObject().getAsJsonArray("rounds").size());
        var recovered = payments();
        assertTrue(recovered.blocked(pendingPlayer));
        assertTrue(recovered.blocked(reviewPlayer));
        int deposits = bank.deposits;
        recovered.recover();
        assertFalse(recovered.blocked(pendingPlayer));
        assertTrue(recovered.blocked(reviewPlayer));
        assertEquals(deposits + 1, bank.deposits);
        assertEquals(257, JsonParser.parseString(readJournal()).getAsJsonObject().getAsJsonArray("rounds").size());
        recovered.retry();
        assertEquals(deposits + 1, bank.deposits);
    }

    @Test
    void recoveryCompactsLegacyClosedHistoryWithoutDroppingAnActiveRound() throws Exception {
        var closed = new com.google.gson.JsonArray();
        for (int index = 0; index < 270; index++) {
            var record = new com.google.gson.JsonObject();
            record.addProperty("id", UUID.randomUUID().toString());
            record.addProperty("player", player.toString());
            record.addProperty("machine", "slots");
            record.addProperty("stake", 100);
            record.addProperty("debit", 0);
            record.addProperty("returned", 100);
            record.addProperty("precision", 2);
            record.addProperty("state", "CLOSED");
            closed.add(record);
        }
        var active = closed.get(0).getAsJsonObject().deepCopy();
        active.addProperty("id", UUID.randomUUID().toString());
        active.addProperty("state", "ACTIVE");
        active.remove("returned");
        closed.add(active);
        var document = new com.google.gson.JsonObject();
        document.addProperty("schema", 1);
        document.add("rounds", closed);
        Files.writeString(journal(), document.toString());
        var recovered = payments();
        assertTrue(recovered.blocked(player));
        recovered.recover();
        assertFalse(recovered.blocked(player));
        assertEquals(256, JsonParser.parseString(readJournal()).getAsJsonObject().getAsJsonArray("rounds").size());
        assertTrue(readJournal().contains(active.get("id").getAsString()));
        assertEquals(0, bank.withdrawals);
        assertEquals(0, bank.deposits);
    }

    @Test
    void rejectedDebitDoesNotBlockAReplacementRound() {
        var payments = payments();
        var funding = payments.bind(player, "slots");
        bank.debit = EconomyAccess.Transfer.REJECTED;
        assertThrows(IllegalArgumentException.class, () -> funding.begin(1000));
        assertEquals(100, bank.funds);
        assertFalse(payments.blocked(player));
        bank.debit = EconomyAccess.Transfer.SUCCESS;
        assertNotNull(funding.begin(1000));
        assertEquals(90, bank.funds);
    }

    @Test
    void invalidStakesAndInsufficientFundsNeverWithdraw() {
        var funding = payments().bind(player, "slots");
        assertThrows(IllegalArgumentException.class, () -> funding.begin(-1));
        assertThrows(IllegalArgumentException.class, () -> funding.begin(99));
        assertThrows(IllegalArgumentException.class, () -> funding.begin(10_001));
        bank.funds = 0.5;
        assertThrows(IllegalArgumentException.class, () -> funding.begin(100));
        assertEquals(0, bank.withdrawals);
    }

    @Test
    void balanceProviderFailureUsesTheLocalizedUnavailableErrorWithoutCharging() {
        bank.beforeBalance = () -> { throw new IllegalStateException("Balance provider failed"); };
        var funding = payments().bind(player, "slots");
        RuleViolation error = assertThrows(RuleViolation.class, () -> funding.begin(1000));
        assertEquals("error.economy.unavailable", error.message().key());
        assertEquals(0, bank.withdrawals);
        assertFalse(Files.exists(journal()));
    }

    @Test
    void increasesRespectTheTotalMaximumAndRejectedIncreaseLeavesTheRoundUsable() {
        maximum = 2000;
        var funding = payments().bind(player, "blackjack");
        UUID round = funding.begin(1000);
        bank.debit = EconomyAccess.Transfer.REJECTED;
        assertThrows(IllegalArgumentException.class, () -> funding.increase(round, 500));
        assertEquals(90, bank.funds);
        bank.debit = EconomyAccess.Transfer.SUCCESS;
        funding.increase(round, 1000);
        assertEquals(80, bank.funds);
        assertThrows(IllegalArgumentException.class, () -> funding.increase(round, 1));
        funding.settle(round, 4000);
        assertEquals(120, bank.funds);
    }

    @Test
    void unresolvedPlayerCannotStartAnotherPaidRoundButCanPractice() {
        var payments = payments();
        payments.bind(player, "slots").begin(1000);
        var other = payments.bind(player, "roulette");
        assertThrows(IllegalArgumentException.class, () -> other.begin(1000));
        assertNotNull(other.begin(0));
        assertEquals(90, bank.funds);
    }

    @Test
    void recoveryAbandonsAChargedRoundWithoutRefundOrReward() {
        payments().bind(player, "mines").begin(1000);
        var recovered = payments();
        recovered.recover();
        recovered.recover();
        assertEquals(90, bank.funds);
        assertEquals(0, bank.deposits);
        assertFalse(recovered.blocked(player));
        assertNotNull(recovered.bind(player, "mines").begin(1000));
    }

    @Test
    void rejectedCreditIsRecoveredAndRetriedOnlyUntilSuccess() {
        var payments = payments();
        var funding = payments.bind(player, "slots");
        UUID round = funding.begin(1000);
        bank.credit = EconomyAccess.Transfer.REJECTED;
        funding.settle(round, 2000);
        assertTrue(payments.blocked(player));
        assertEquals(90, bank.funds);
        bank.credit = EconomyAccess.Transfer.SUCCESS;
        var recovered = payments();
        recovered.recover();
        recovered.retry();
        assertFalse(recovered.blocked(player));
        assertEquals(110, bank.funds);
        assertEquals(2, bank.deposits);
    }

    @Test
    void uncertainDebitIsNeverRetriedOrAutomaticallyRefunded() {
        bank.debit = EconomyAccess.Transfer.UNCERTAIN;
        var payments = payments();
        assertThrows(IllegalArgumentException.class, () -> payments.bind(player, "slots").begin(1000));
        payments.retry();
        var recovered = payments();
        recovered.recover();
        recovered.retry();
        assertTrue(recovered.blocked(player));
        assertEquals(1, bank.withdrawals);
        assertEquals(0, bank.deposits);
    }

    @Test
    void uncertainIncreaseRetainsTheKnownGameResultForManualReviewAfterRestart() {
        var payments = payments();
        var funding = payments.bind(player, "blackjack");
        UUID round = funding.begin(1000);
        bank.debit = EconomyAccess.Transfer.UNCERTAIN;
        assertThrows(RuleViolation.class, () -> funding.increase(round, 1000));
        funding.settle(round, 2000);
        funding.settle(round, 3000);
        payments.close();
        var recovered = payments();
        recovered.recover();
        recovered.retry();
        var record = JsonParser.parseString(readJournal()).getAsJsonObject()
                .getAsJsonArray("rounds").get(0).getAsJsonObject();
        assertEquals("REVIEW", record.get("state").getAsString());
        assertEquals(1000, record.get("stake").getAsLong());
        assertEquals(1000, record.get("debit").getAsLong());
        assertEquals(2000, record.get("returned").getAsLong());
        assertTrue(recovered.blocked(player));
        assertEquals(2, bank.withdrawals);
        assertEquals(0, bank.deposits);
        assertEquals(90, bank.funds);
    }

    @Test
    void uncertainCreditIsNeverRetried() {
        var payments = payments();
        var funding = payments.bind(player, "slots");
        UUID round = funding.begin(1000);
        bank.credit = EconomyAccess.Transfer.UNCERTAIN;
        funding.settle(round, 2000);
        payments.retry();
        var recovered = payments();
        recovered.recover();
        recovered.retry();
        assertTrue(recovered.blocked(player));
        assertEquals(1, bank.deposits);
    }

    @Test
    void crashDuringDebitIsHeldForReviewAfterRestart() {
        bank.beforeWithdraw = () -> { throw new AssertionError("Simulated process termination"); };
        assertThrows(AssertionError.class, () -> payments().bind(player, "slots").begin(1000));
        bank.beforeWithdraw = () -> {};
        var recovered = payments();
        recovered.recover();
        recovered.retry();
        assertTrue(recovered.blocked(player));
        assertEquals(1, bank.withdrawals);
        assertEquals(0, bank.deposits);
    }

    @Test
    void crashDuringCreditDoesNotPayAgainAfterRestart() {
        var funding = payments().bind(player, "slots");
        UUID round = funding.begin(1000);
        bank.beforeDeposit = () -> { throw new AssertionError("Simulated process termination"); };
        assertThrows(AssertionError.class, () -> funding.settle(round, 2000));
        bank.beforeDeposit = () -> {};
        var recovered = payments();
        recovered.recover();
        recovered.retry();
        assertTrue(recovered.blocked(player));
        assertEquals(1, bank.deposits);
    }

    @Test
    void providerExceptionIsUncertainAndCannotBeRetried() {
        bank.beforeWithdraw = () -> { throw new IllegalStateException("Provider failure"); };
        assertThrows(IllegalArgumentException.class, () -> payments().bind(player, "slots").begin(1000));
        var recovered = payments();
        recovered.recover();
        recovered.retry();
        assertTrue(recovered.blocked(player));
        assertEquals(1, bank.withdrawals);
    }

    @Test
    void creditProviderExceptionIsHeldForReviewAndNeverRetried() {
        var funding = payments().bind(player, "slots");
        UUID round = funding.begin(1000);
        bank.beforeDeposit = () -> { throw new IllegalStateException("Provider failure"); };
        funding.settle(round, 2000);
        bank.beforeDeposit = () -> {};
        var recovered = payments();
        recovered.recover();
        recovered.retry();
        assertTrue(recovered.blocked(player));
        assertEquals(1, bank.deposits);
    }

    @Test
    void unavailableCreditProviderKeepsTheKnownAwardForRecovery() {
        var funding = payments().bind(player, "slots");
        UUID round = funding.begin(1000);
        bank.available = false;
        funding.settle(round, 2000);
        assertEquals(0, bank.deposits);
        bank.available = true;
        payments().recover();
        assertEquals(110, bank.funds);
        assertEquals(1, bank.deposits);
    }

    @Test
    void failedWriteAfterSuccessfulDebitClosesEntryAndNeverRepeatsTheDebit() throws Exception {
        var payments = payments();
        var funding = payments.bind(player, "slots");
        bank.afterWithdraw = this::interruptJournalWrites;
        assertThrows(IllegalArgumentException.class, () -> funding.begin(1000));
        assertEquals(90, bank.funds);
        assertTrue(payments.blocked(player));
        assertThrows(IllegalArgumentException.class, () -> funding.begin(1000));
        restoreJournal();
        bank.afterWithdraw = () -> {};
        var recovered = payments();
        recovered.recover();
        recovered.retry();
        assertTrue(recovered.blocked(player));
        assertEquals(1, bank.withdrawals);
        assertEquals(0, bank.deposits);
    }

    @Test
    void failedWriteAfterSuccessfulCreditNeverRepeatsTheCredit() throws Exception {
        var payments = payments();
        var funding = payments.bind(player, "slots");
        UUID round = funding.begin(1000);
        bank.afterDeposit = this::interruptJournalWrites;
        assertThrows(IllegalArgumentException.class, () -> funding.settle(round, 2000));
        assertEquals(110, bank.funds);
        payments.retry();
        restoreJournal();
        bank.afterDeposit = () -> {};
        var recovered = payments();
        recovered.recover();
        recovered.retry();
        assertTrue(recovered.blocked(player));
        assertEquals(1, bank.deposits);
    }

    @Test
    void precisionIsCapturedForTheRoundAndAwardsAreRoundedDown() {
        bank.precision = 1;
        var funding = payments().bind(player, "blackjack");
        assertThrows(IllegalArgumentException.class, () -> funding.begin(101));
        UUID round = funding.begin(110);
        bank.precision = 2;
        assertEquals(160, funding.normalize(169));
        funding.settle(round, 169);
        assertEquals(100.5, bank.funds, 0.000001);
    }

    @Test
    void wholeUnitCurrenciesRejectFractionalStakesAndRoundAwardsDown() {
        bank.precision = 0;
        var funding = payments().bind(player, "slots");
        assertThrows(IllegalArgumentException.class, () -> funding.begin(150));
        UUID round = funding.begin(100);
        assertEquals(200, funding.normalize(299));
        funding.settle(round, 299);
        assertEquals(101, bank.funds);
    }

    @Test
    void corruptJournalBlocksPaymentWithoutOverwritingTheEvidence() throws Exception {
        Files.writeString(journal(), "broken journal");
        var payments = payments();
        assertThrows(IllegalArgumentException.class, () -> payments.bind(player, "slots").begin(1000));
        assertNotNull(payments.bind(player, "slots").begin(0));
        assertEquals("broken journal", Files.readString(journal()));
        assertEquals(0, bank.withdrawals);
        assertFalse(warnings.isEmpty());
    }

    @Test
    void failedIntentWriteClosesPaidEntryBeforeAnyWithdrawal() throws Exception {
        Path parent = directory.resolve("not-a-directory");
        Files.writeString(parent, "preserved");
        var payments = new MachinePaymentLedger(parent.resolve("payments.json"), () -> bank, enabled::get,
                () -> minimum, () -> maximum, warnings::add);
        var funding = payments.bind(player, "slots");
        assertThrows(IllegalArgumentException.class, () -> funding.begin(1000));
        assertThrows(IllegalArgumentException.class, () -> funding.begin(1000));
        assertEquals(0, bank.withdrawals);
        assertNotNull(funding.begin(0));
        assertFalse(warnings.isEmpty());
    }

    @Test
    void anotherPlayerOrMachineCannotSettleAnExistingRound() {
        var payments = payments();
        var funding = payments.bind(player, "slots");
        UUID round = funding.begin(1000);
        assertThrows(IllegalArgumentException.class,
                () -> payments.bind(UUID.randomUUID(), "slots").settle(round, 9000));
        assertThrows(IllegalArgumentException.class,
                () -> payments.bind(player, "roulette").settle(round, 9000));
        assertEquals(90, bank.funds);
        funding.settle(round, 0);
        assertFalse(payments.blocked(player));
    }

    @Test
    void disablingPaymentsStillAllowsAnExistingAwardToBePaid() {
        var funding = payments().bind(player, "slots");
        UUID round = funding.begin(1000);
        enabled.set(false);
        funding.settle(round, 2000);
        assertEquals(110, bank.funds);
    }

    @Test
    void closeAbandonsActiveRoundsAndPreventsFurtherPaidEntry() {
        var payments = payments();
        var funding = payments.bind(player, "slots");
        funding.begin(1000);
        payments.close();
        assertEquals(90, bank.funds);
        assertFalse(payments.blocked(player));
        assertThrows(IllegalArgumentException.class, () -> funding.begin(1000));
    }

    String readJournal() {
        try {
            return Files.readString(journal());
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    void interruptJournalWrites() {
        try {
            Files.move(journal(), directory.resolve("saved-journal.json"));
            Files.createDirectory(journal());
            Files.writeString(journal().resolve("blocker"), "preserved");
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    void restoreJournal() throws Exception {
        Files.delete(journal().resolve("blocker"));
        Files.delete(journal());
        Files.move(directory.resolve("saved-journal.json"), journal());
    }

    static final class Bank implements EconomyAccess {
        double funds = 100;
        int withdrawals;
        int deposits;
        int precision = 2;
        boolean available = true;
        Transfer debit = Transfer.SUCCESS;
        Transfer credit = Transfer.SUCCESS;
        Runnable beforeWithdraw = () -> {};
        Runnable beforeDeposit = () -> {};
        Runnable beforeBalance = () -> {};
        Runnable afterWithdraw = () -> {};
        Runnable afterDeposit = () -> {};

        public boolean available() { return available; }
        public OptionalDouble balance(UUID player) {
            beforeBalance.run();
            return OptionalDouble.of(funds);
        }
        public String format(double amount) { return Double.toString(amount); }
        public int fractionalDigits() { return precision; }
        public Transfer withdraw(UUID player, double amount) {
            withdrawals++;
            beforeWithdraw.run();
            if (debit == Transfer.SUCCESS) funds -= amount;
            afterWithdraw.run();
            return debit;
        }
        public Transfer deposit(UUID player, double amount) {
            deposits++;
            beforeDeposit.run();
            if (credit == Transfer.SUCCESS) funds += amount;
            afterDeposit.run();
            return credit;
        }
    }
}
