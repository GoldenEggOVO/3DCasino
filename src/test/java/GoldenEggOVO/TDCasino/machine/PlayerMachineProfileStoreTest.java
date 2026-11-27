package GoldenEggOVO.TDCasino.machine;

import GoldenEggOVO.TDCasino.game.keno.KenoRound;
import GoldenEggOVO.TDCasino.game.hilo.HiloRound;
import GoldenEggOVO.TDCasino.game.mines.MinesRound;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Random;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PlayerMachineProfileStoreTest {
    @TempDir Path folder;
    private final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID(), owner = UUID.randomUUID();

    @Test void settledTotalsAndTheFinalResultSurviveRestartBeforeReveal() throws Exception {
        var resultState = new MachineResultState();
        resultState.launch(1L, 1000);
        resultState.settle(1L, 1500);
        var store = new PlayerMachineProfileStore(folder);
        store.save(alice, owner, "keno", PlayerMachineProfileStore.Profile.capture(new KenoRound(new Random(1)), resultState));
        var restored = new MachineResultState();
        new PlayerMachineProfileStore(folder).load(alice, owner, "keno").restore(new KenoRound(new Random(2)), restored);
        assertEquals(500, restored.totalNet());
        assertEquals(new MachineResultState.Result(1000, 1500), restored.last());
        assertEquals(0, restored.pending());
        assertEquals(0, resultState.totalNet());
        assertNull(resultState.last());
    }

    @Test void preferencesAndSettledResultsSurviveRestartWithoutSharingOrResumingRounds() throws Exception {
        var keno = new KenoRound(new Random(1));
        keno.action("select:7");
        keno.action("play");
        var resultState = new MachineResultState();
        resultState.launch(1, 1000); resultState.reveal(1, 1400);
        resultState.launch(2, 1000); // an unfinished game must not be restored
        var store = new PlayerMachineProfileStore(folder);
        store.save(alice, owner, "keno", PlayerMachineProfileStore.Profile.capture(keno, resultState));
        var loaded = new PlayerMachineProfileStore(folder);
        var restored = new KenoRound(new Random(2));
        var restoredFeedback = new MachineResultState();
        loaded.load(alice, owner, "keno").restore(restored, restoredFeedback);
        assertEquals(java.util.Set.of(7), restored.selected());
        assertFalse(restored.active());
        assertFalse(restored.finished());
        assertEquals(0, restored.sequence());
        assertEquals(400, restoredFeedback.totalNet());
        assertEquals(0, restoredFeedback.pending());
        assertNull(loaded.load(bob, owner, "keno"));
        assertNull(loaded.load(alice, UUID.randomUUID(), "keno"));
        assertNull(loaded.load(alice, owner, "hilo"));
    }

    @Test void savesHiloDirectionThresholdAndMinesCount() throws Exception {
        var hilo = new HiloRound(new Random(1)); hilo.setThreshold(23); hilo.action("under");
        var mines = new MinesRound(new Random(1)); mines.changeMines(5);
        var store = new PlayerMachineProfileStore(folder);
        store.save(alice, owner, "hilo", PlayerMachineProfileStore.Profile.capture(hilo, new MachineResultState()));
        store.save(alice, owner, "mines", PlayerMachineProfileStore.Profile.capture(mines, new MachineResultState()));
        store = new PlayerMachineProfileStore(folder);
        var restoredHilo = new HiloRound(new Random(2));
        store.load(alice, owner, "hilo").restore(restoredHilo, new MachineResultState());
        assertEquals(23, restoredHilo.threshold()); assertFalse(restoredHilo.high());
        var restoredMines = new MinesRound(new Random(2));
        store.load(alice, owner, "mines").restore(restoredMines, new MachineResultState());
        assertEquals(8, restoredMines.mineCount());
    }

    @Test void corruptFileIsReportedAndNeverOverwritten() throws Exception {
        Files.writeString(folder.resolve(alice + ".json"), "broken original");
        var store = new PlayerMachineProfileStore(folder);
        assertThrows(java.io.IOException.class, () -> store.load(alice, owner, "keno"));
        assertThrows(java.io.IOException.class, () -> store.save(alice, owner, "keno",
                PlayerMachineProfileStore.Profile.capture(new KenoRound(new Random()), new MachineResultState())));
        assertEquals("broken original", Files.readString(folder.resolve(alice + ".json")));
    }

    @Test void personalAmountsAreSeparateAndLegacyProfilesDefaultToZero() throws Exception {
        var store = new PlayerMachineProfileStore(folder);
        var first = new KenoRound(new Random(1));
        first.setStake(125);
        store.save(alice, owner, "keno", PlayerMachineProfileStore.Profile.capture(first, new MachineResultState()));
        var second = new KenoRound(new Random(2));
        second.setStake(700);
        store.save(bob, owner, "keno", PlayerMachineProfileStore.Profile.capture(second, new MachineResultState()));
        var loaded = new PlayerMachineProfileStore(folder);
        var restored = new KenoRound(new Random(3));
        loaded.load(alice, owner, "keno").restore(restored, new MachineResultState());
        assertEquals(125, restored.stake());
        assertEquals(700, loaded.load(bob, owner, "keno").stake());
        var document = com.google.gson.JsonParser.parseString(Files.readString(folder.resolve(alice + ".json"))).getAsJsonObject();
        document.getAsJsonObject("machines").getAsJsonObject(owner + ":keno").remove("stake");
        Files.writeString(folder.resolve(alice + ".json"), document.toString());
        var legacy = new KenoRound(new Random(4));
        new PlayerMachineProfileStore(folder).load(alice, owner, "keno").restore(legacy, new MachineResultState());
        assertEquals(0, legacy.stake());
    }

    @Test void zeroAmountResultsCanBeSavedAndRestored() throws Exception {
        var resultState = new MachineResultState();
        resultState.launch(1, 0);
        resultState.reveal(1, 0);
        var store = new PlayerMachineProfileStore(folder);
        store.save(alice, owner, "keno", PlayerMachineProfileStore.Profile.capture(new KenoRound(new Random(1)), resultState));
        var restored = new MachineResultState();
        new PlayerMachineProfileStore(folder).load(alice, owner, "keno").restore(new KenoRound(new Random(2)), restored);
        assertEquals(0, restored.last().stake());
        assertEquals(MachineResultState.Outcome.EVEN, restored.last().outcome());
    }
}
