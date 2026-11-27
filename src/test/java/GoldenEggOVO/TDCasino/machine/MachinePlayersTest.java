package GoldenEggOVO.TDCasino.machine;

import GoldenEggOVO.TDCasino.game.keno.KenoRound;
import GoldenEggOVO.TDCasino.game.crash.CrashRound;
import GoldenEggOVO.TDCasino.economy.RoundFunding;
import GoldenEggOVO.TDCasino.Core.player.PlayerSessions;
import org.junit.jupiter.api.Test;
import java.util.Random;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MachinePlayersTest {
    private final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
    private MachinePlayers<KenoRound> players() {
        return new MachinePlayers<>(() -> new KenoRound(new Random(1)));
    }

    @Test void reservesPreparationAndExcludesOtherPlayersUntilSettlement() {
        var players = players();
        assertTrue(players.claim(alice));
        var data = players.data(alice);
        data.round.action("select:1");
        assertFalse(players.claim(bob));
        players.update(false);
        assertEquals(alice, players.player()); // selection must survive until PLAY
        data.round.action("play");
        players.started();
        players.update(true); // reveal animation still running
        assertFalse(players.claim(bob));
        players.update(false);
        assertNull(players.player());
        assertTrue(players.claim(bob));
    }

    @Test void playerRoundsSelectionsAndFeedbackAreSeparate() {
        var players = players();
        var a = players.data(alice);
        var b = players.data(bob);
        a.round.action("select:7");
        a.resultState.launch(1, 1000);
        a.resultState.reveal(1, 1500);
        assertNotSame(a.round, b.round);
        assertTrue(b.round.selected().isEmpty());
        assertEquals(0, b.resultState.totalNet());
        assertNull(b.resultState.last());
        assertEquals(500, players.data(alice).resultState.totalNet());
        assertTrue(players.data(alice).round.selected().contains(7));
    }

    @Test void rejectedClicksCannotExtendAnAbandonedPlayersReservation() {
        var players = players();
        players.claim(alice);
        for (int i = 0; i < MachinePlayers.IDLE_TICKS; i++) {
            assertFalse(players.claim(bob));
            players.tick();
        }
        assertTrue(players.expired());
    }

    @Test void abandoningResetsUnfinishedDataButPreservesCompletedPersonalResults() {
        var players = players();
        players.claim(alice);
        var a = players.data(alice);
        a.round.action("select:7");
        a.resultState.launch(1, 1000);
        a.resultState.reveal(1, 1500);
        a.resultState.launch(2, 1000);
        players.abandon();
        assertNull(players.player());
        assertTrue(players.data(alice).round.selected().contains(7));
        assertFalse(players.data(alice).round.active());
        assertEquals(0, players.data(alice).round.sequence());
        assertEquals(0, players.data(alice).resultState.pending());
        assertEquals(500, players.data(alice).resultState.totalNet());
        assertTrue(players.claim(bob));
    }

    @Test void abandoningKeepsFreshPreferencesAndTotalsWhenSavedProfileIsStale() {
        var players = new MachinePlayers<>(() -> new KenoRound(new Random(1)),
                (id, data) -> data.resultState.restore(100, null));
        players.claim(alice);
        var a = players.data(alice);
        a.round.action("select:9");
        a.resultState.launch(1, 1000);
        a.resultState.reveal(1, 1500);
        a.resultState.launch(2, 1000);
        players.abandon();
        assertEquals(600, a.resultState.totalNet());
        assertEquals(0, a.resultState.pending());
        assertTrue(a.round.selected().contains(9));
        assertEquals(0, a.round.sequence());
    }

    @Test void plinkoBatchStaysExclusiveUntilAllBallsFinish() {
        var players = players();
        players.claim(alice);
        players.started();
        players.update(true);
        assertTrue(players.claim(alice));
        assertFalse(players.claim(bob));
        players.update(true);
        assertFalse(players.claim(bob));
        players.update(false);
        assertTrue(players.claim(bob));
    }

    @Test void sharedSessionsPreventUsingTwoGamesAndRepeatedInputKeepsTheSameReservation() {
        var sessions = new PlayerSessions();
        var first = new MachinePlayers<>(() -> new KenoRound(new Random(1)), (id, data) -> {}, sessions, "casino:first:keno");
        var second = new MachinePlayers<>(() -> new KenoRound(new Random(1)), (id, data) -> {}, sessions, "casino:second:keno");
        assertTrue(first.claim(alice));
        assertTrue(first.claim(alice));
        assertFalse(second.claim(alice));
        first.abandon();
        assertTrue(second.claim(alice));
    }

    @Test void failedClaimAndOldMachineCleanupNeverReleaseATabletopReservation() {
        var sessions = new PlayerSessions();
        var players = new MachinePlayers<>(() -> new KenoRound(new Random(1)), (id, data) -> {}, sessions, "casino:owner:keno");
        assertTrue(sessions.reserve(alice, "tabletop:room"));
        assertFalse(players.claim(alice));
        players.abandon();
        assertFalse(sessions.reserve(alice, "other"));
        sessions.release(alice, "tabletop:room");
        assertTrue(players.claim(alice));
        sessions.release(alice, "casino:owner:keno");
        assertTrue(sessions.reserve(alice, "tabletop:next"));
        players.abandon();
        assertFalse(sessions.reserve(alice, "other"));
    }

    @Test void abandoningResolvesAnActiveFundedRoundBeforeReleasingThePlayer() {
        var returns = new java.util.ArrayList<Long>();
        var funding = new RoundFunding() {
            public UUID begin(long cents) { return UUID.randomUUID(); }
            public void increase(UUID round, long cents) {}
            public void settle(UUID round, long returned) { returns.add(returned); }
        };
        var players = new MachinePlayers<>(() -> new CrashRound(new Random(1)),
                (id, data) -> data.round.funding(funding));
        assertTrue(players.claim(alice));
        players.data(alice).round.setStake(200);
        players.data(alice).round.start(1000);
        players.abandon();
        players.abandon();
        assertEquals(java.util.List.of(0L), returns);
        assertNull(players.player());
        assertEquals(200, players.data(alice).round.configuredStake());
        assertEquals(-200, players.data(alice).resultState.totalNet());
    }
}
