package GoldenEggOVO.TDCasino.machine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import GoldenEggOVO.TDCasino.CasinoModule;
import GoldenEggOVO.TDCasino.game.crash.CrashRound;
import GoldenEggOVO.TDCasino.game.crash.CrashMachine;
import GoldenEggOVO.TDCasino.game.blackjack.BlackjackMachine;
import GoldenEggOVO.TDCasino.game.blackjack.BlackjackRound;
import GoldenEggOVO.TDCasino.game.plinko.PlinkoMachine;
import GoldenEggOVO.TDCasino.game.plinko.PlinkoRound;
import GoldenEggOVO.TDCasino.game.slots.SlotsRound;
import GoldenEggOVO.TDCasino.machine.definition.MachineDefinition;
import GoldenEggOVO.TDCasino.economy.MachinePaymentLedger;
import GoldenEggOVO.TDCasino.economy.RoundFunding;
import GoldenEggOVO.TDCasino.Core.player.PlayerSessions;
import GoldenEggOVO.TDCasino.TDCasinoPlugin;
import java.lang.reflect.InvocationTargetException;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class MachineFundingTest {
    private static final class TestSlotsMachine extends AnimatedMachine<SlotsRound> {
        TestSlotsMachine(MachineManager manager, UUID owner, Location origin) {
            super(manager, owner, origin, MachineDefinition.builtin("slots"),
                    () -> new SlotsRound(new Random(1) {
                        @Override public int nextInt(int bound) { return 0; }
                    }));
        }
        protected void buildGame() {}
        protected void refresh() {}
        protected boolean available(String action) { return action.equals("play"); }
        protected void perform(String action) { round.action(action); }
        protected void animateFrame(double progress, double ease) {}
    }

    private static final class TestMachine extends GameMachine<CrashRound> {
        TestMachine(MachineManager manager, UUID owner, Location origin) {
            super(manager, owner, origin, MachineDefinition.builtin("crash"),
                    () -> new CrashRound(new Random(1)));
        }
        protected void buildGame() {}
        protected void refresh() {}
        protected boolean available(String action) { return action.equals("start") && !round.active(); }
        protected void action(String action) { round.start(1000); }
    }

    private static final class Fixture {
        final UUID aliceId = UUID.randomUUID(), bobId = UUID.randomUUID();
        final TDCasinoPlugin plugin = mock(TDCasinoPlugin.class);
        final CasinoModule casino = mock(CasinoModule.class);
        final MachinePaymentLedger payments = mock(MachinePaymentLedger.class);
        final MachineManager manager = mock(MachineManager.class);
        final RoundFunding funding = mock(RoundFunding.class);
        final PlayerSessions sessions = new PlayerSessions();
        final World world = mock(World.class);
        final Location origin = new Location(world, 0, 64, 0);
        final Player alice = player(aliceId), bob = player(bobId);

        Fixture() {
            when(manager.plugin()).thenReturn(plugin);
            when(plugin.scheduler()).thenReturn(mock(GoldenEggOVO.TDCasino.Core.platform.PlatformScheduler.class));
            when(plugin.casino()).thenReturn(casino);
            when(plugin.sessions()).thenReturn(sessions);
            when(plugin.machineSettings()).thenReturn(mock(GoldenEggOVO.TDCasino.interaction.MachineSettingsTargets.class));
            when(casino.payments()).thenReturn(payments);
            when(payments.bind(any(UUID.class), anyString())).thenReturn(funding);
            when(funding.begin(anyLong())).thenAnswer(call -> UUID.randomUUID());
            when(funding.normalize(anyLong())).thenAnswer(call -> call.getArgument(0));
            when(manager.contains(any())).thenReturn(true);
            when(manager.canUse(any(), any())).thenReturn(true);
        }

        Player player(UUID id) {
            var player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(id);
            when(player.getWorld()).thenReturn(world);
            when(player.getEyeLocation()).thenAnswer(call -> origin.clone().add(0, 1, 1));
            return player;
        }

        TestMachine machine() { return new TestMachine(manager, aliceId, origin); }

        PlinkoMachine plinko() {
            return new PlinkoMachine(manager, aliceId, origin, MachineDefinition.builtin("plinko"));
        }
    }

    @Test void unloadAndTickNeverReadAnEntityOwnedByAnotherRegion() {
        var f = new Fixture();
        var machine = f.machine();
        var remote = mock(org.bukkit.entity.Entity.class);
        machine.parts.add(remote);
        var distant = mock(org.bukkit.Chunk.class);
        when(distant.getWorld()).thenReturn(f.world);
        when(distant.getX()).thenReturn(30);
        var scheduler = f.plugin.scheduler();
        assertFalse(machine.touchesChunk(distant));
        verify(scheduler, never()).isOwned(remote);

        var visited = machine.worldAt(32, 0, 0);
        when(distant.getX()).thenReturn(visited.getBlockX() >> 4);
        when(distant.getZ()).thenReturn(visited.getBlockZ() >> 4);
        assertFalse(machine.touchesChunk(distant));
        assertTrue(machine.expired());
        verify(remote, never()).getLocation();
        verify(remote, never()).isValid();
    }

    @Test void anotherGameReservationBlocksTheActionBeforeFunding() throws Exception {
        var f = new Fixture();
        var machine = f.machine();
        assertTrue(f.sessions.reserve(f.aliceId, "tabletop:room"));
        click(machine, f.alice, "start");
        assertEquals(0, machine.round.sequence());
        assertFalse(machine.round.active());
        verify(f.funding, never()).begin(anyLong());
        assertFalse(f.sessions.reserve(f.aliceId, "other"));
    }

    @Test void crashFlightCanContinuePastTheInteractiveIdleTimeout() throws Exception {
        var f = new Fixture();
        var machine = new CrashMachine(f.manager, f.aliceId, f.origin, MachineDefinition.builtin("crash"));
        var gameMachine = (GameMachine<?>) machine;
        for (var entry : java.util.Map.of("rocket", mock(org.bukkit.entity.ItemDisplay.class),
                "readout", mock(org.bukkit.entity.TextDisplay.class)).entrySet()) {
            var field = CrashMachine.class.getDeclaredField(entry.getKey());
            field.setAccessible(true);
            field.set(machine, entry.getValue());
        }
        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            click(machine, f.alice, "start");
            var round = (CrashRound) ((GameMachine<?>) machine).round;
            var point = CrashRound.class.getDeclaredField("crashPoint");
            point.setAccessible(true);
            point.setInt(round, 10000);
            for (int tick = 0; tick < MachinePlayers.IDLE_TICKS; tick++) machine.tick();
            assertTrue(round.active());
            assertFalse(gameMachine.sessionExpired());
            assertTrue(gameMachine.occupiedBy(f.aliceId));
        }
        machine.close();
        assertTrue(f.sessions.reserve(f.aliceId, "tabletop:after-crash"));
    }

    @Test void personalAmountEditsNeverChangeTheOtherPlayersActiveStake() throws Exception {
        var f = new Fixture();
        var machine = f.machine();
        assertEquals(0, machine.stake(f.alice));
        machine.setStake(f.alice, 125);
        machine.setStake(f.bob, 700);
        click(machine, f.alice, "start");
        verify(f.funding).begin(125);
        assertEquals(125, machine.round.stake());
        assertThrows(IllegalArgumentException.class, () -> machine.setStake(f.alice, 250));
        machine.setStake(f.bob, 900);
        assertEquals(900, machine.stake(f.bob));
        assertEquals(125, machine.round.stake());
        verify(f.casino).validateStakeCents(900);
    }

    @Test void refusedFundingKeepsTheRoundUnstartedAndReleasesTheClaim() throws Exception {
        var f = new Fixture();
        var machine = f.machine();
        when(f.funding.begin(anyLong())).thenThrow(new IllegalArgumentException("error.balance"));
        click(machine, f.alice, "start");
        assertEquals(0, machine.round.sequence());
        assertFalse(machine.round.active());
        assertTrue(f.sessions.reserve(f.aliceId, "tabletop:room"));
        verify(f.funding, never()).settle(any(), anyLong());
    }

    @Test void refusedRestartKeepsThePreviousRoundAndItsVisibleResult() throws Exception {
        var f = new Fixture();
        var machine = f.machine();
        machine.setStake(f.alice, 125);
        click(machine, f.alice, "start");
        assertTrue(machine.round.cash(1000));
        machine.round.abandon();
        machine.tick();
        var previous = machine.round;
        long sequence = previous.sequence(), payout = previous.payout();
        var result = machine.resultState.last();
        when(f.funding.begin(anyLong())).thenThrow(new IllegalArgumentException("error.balance"));
        click(machine, f.alice, "start");
        assertSame(previous, machine.round);
        assertEquals(sequence, machine.round.sequence());
        assertEquals(payout, machine.round.payout());
        assertSame(result, machine.resultState.last());
        assertTrue(f.sessions.reserve(f.aliceId, "tabletop:room"));
    }

    @Test void blackjackRefusedRestartKeepsThePreviousCardDisplays() throws Exception {
        var f = new Fixture();
        var machine = new BlackjackMachine(f.manager, f.aliceId, f.origin, MachineDefinition.builtin("blackjack"));
        var round = (BlackjackRound) ((GameMachine<?>) machine).round;
        round.start(1000);
        round.stand();
        var dealer = round.dealer();
        var hand = round.player();
        var card = mock(org.bukkit.entity.ItemDisplay.class);
        for (String name : java.util.List.of("dealerCards", "playerCards")) {
            var field = BlackjackMachine.class.getDeclaredField(name);
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var cards = (java.util.List<org.bukkit.entity.ItemDisplay>) field.get(machine);
            cards.add(card);
        }
        when(f.funding.begin(anyLong())).thenThrow(new IllegalArgumentException("error.balance"));
        var action = BlackjackMachine.class.getDeclaredMethod("action", String.class);
        action.setAccessible(true);
        var failure = assertThrows(InvocationTargetException.class, () -> action.invoke(machine, "start"));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertEquals(dealer, round.dealer());
        assertEquals(hand, round.player());
        verify(card, never()).remove();
    }

    @Test void plinkoRefusedFundingCreatesNoFlight() throws Exception {
        var f = new Fixture();
        var machine = f.plinko();
        when(f.funding.begin(anyLong())).thenThrow(new IllegalArgumentException("error.balance"));
        assertThrows(IllegalArgumentException.class, () -> launch(machine));
        assertTrue(((PlinkoRound) ((GameMachine<?>) machine).round).activeFlights().isEmpty());
        verify(f.funding, never()).settle(any(), anyLong());
    }

    @Test void closeSettlesBeforeReleasingAndRemovesDisplaysEvenWhenSettlementFails() throws Exception {
        var f = new Fixture();
        var machine = f.machine();
        click(machine, f.alice, "start");
        var entity = mock(org.bukkit.entity.Entity.class);
        machine.parts.add(entity);
        var failure = new IllegalStateException("Journal unavailable");
        doThrow(failure).when(f.funding).settle(any(UUID.class), anyLong());
        assertSame(failure, assertThrows(IllegalStateException.class, machine::close));
        verify(entity).remove();
        assertTrue(machine.parts.isEmpty());
        assertTrue(f.sessions.reserve(f.aliceId, "tabletop:room"));
    }

    @Test void closeKeepsACapturedCashoutAmount() throws Exception {
        var f = new Fixture();
        var machine = f.machine();
        machine.setStake(f.alice, 125);
        click(machine, f.alice, "start");
        assertTrue(machine.round.cash(1000));
        long captured = machine.round.payout();
        assertEquals(125, captured);
        machine.close();
        verify(f.funding, atLeastOnce()).settle(any(UUID.class), eq(captured));
        verify(f.funding, never()).settle(any(UUID.class), eq(0L));
        assertTrue(f.sessions.reserve(f.aliceId, "tabletop:room"));
    }

    @Test void completedAnimationCountsAndSavesTheResultBeforeRevealAndCloseOnlyCountsOnce() throws Exception {
        var f = new Fixture();
        var saved = new java.util.ArrayList<PlayerMachineProfileStore.Profile>();
        doAnswer(call -> {
            saved.add(PlayerMachineProfileStore.Profile.capture(call.getArgument(3), call.getArgument(4)));
            return null;
        }).when(f.manager).savePlayerData(any(), anyString(), any(), any(), any());
        var machine = new TestSlotsMachine(f.manager, f.aliceId, f.origin);
        machine.setStake(f.alice, 125);
        click(machine, f.alice, "play");
        assertEquals(250, machine.round.payout());
        assertTrue(machine.busy());
        assertTrue(machine.round.finished());
        assertEquals(0, machine.resultState.totalNet());
        assertNull(machine.resultState.last());
        assertEquals(125, saved.getLast().totalNet());
        machine.close();
        machine.close();
        assertEquals(125, machine.resultState.totalNet());
        assertEquals(125, saved.getLast().totalNet());
        verify(f.funding, times(1)).settle(any(UUID.class), anyLong());
    }

    @Test void closeCountsAndSavesAnActiveLossOnce() throws Exception {
        var f = new Fixture();
        var saved = new java.util.ArrayList<PlayerMachineProfileStore.Profile>();
        doAnswer(call -> {
            saved.add(PlayerMachineProfileStore.Profile.capture(call.getArgument(3), call.getArgument(4)));
            return null;
        }).when(f.manager).savePlayerData(any(), anyString(), any(), any(), any());
        var machine = f.machine();
        machine.setStake(f.alice, 125);
        click(machine, f.alice, "start");
        machine.close();
        machine.close();
        assertEquals(-125, machine.resultState.totalNet());
        assertEquals(-125, saved.getLast().totalNet());
        verify(f.funding, times(1)).settle(any(UUID.class), eq(0L));
    }

    @Test void plinkoFullCapacityRejectsBeforeFunding() throws Exception {
        var f = new Fixture();
        var machine = f.plinko();
        var round = (PlinkoRound) ((GameMachine<?>) machine).round;
        for (int index = 0; index < 25; index++) {
            assertNotNull(round.launch());
            if (index != 24) for (int tick = 0; tick < 4; tick++) round.advance();
        }
        clearInvocations(f.funding);
        launch(machine);
        assertEquals(25, round.activeFlights().size());
        verify(f.funding, never()).begin(anyLong());
    }

    @Test void plinkoCapturesNormalizedPayoutBeforeDisplayFailure() throws Exception {
        var f = new Fixture();
        var machine = f.plinko();
        machine.setStake(f.alice, 125);
        when(f.funding.normalize(anyLong())).thenReturn(100L);
        var failure = new IllegalStateException("Display unavailable");
        doThrow(failure).when(f.world).spawn(any(Location.class), eq(org.bukkit.entity.ItemDisplay.class),
                org.mockito.ArgumentMatchers.<java.util.function.Consumer<org.bukkit.entity.ItemDisplay>>any());
        try (var models = mockStatic(GoldenEggOVO.TDCasino.render.model.ModelCarrierItems.class)) {
            models.when(() -> GoldenEggOVO.TDCasino.render.model.ModelCarrierItems.resolve(anyString()))
                    .thenReturn(mock(org.bukkit.inventory.ItemStack.class));
            assertSame(failure, assertThrows(IllegalStateException.class, () -> click(machine, f.alice, "play")));
        }
        assertEquals(0, ((GameMachine<?>) machine).resultState.totalNet());
        assertNull(((GameMachine<?>) machine).resultState.last());
        assertEquals(-25, PlayerMachineProfileStore.Profile.capture(((GameMachine<?>) machine).round,
                ((GameMachine<?>) machine).resultState).totalNet());
        machine.close();
        machine.close();
        assertEquals(-25, ((GameMachine<?>) machine).resultState.totalNet());
        var order = inOrder(f.funding, f.world);
        order.verify(f.funding).begin(125);
        order.verify(f.funding).normalize(anyLong());
        order.verify(f.funding).settle(any(UUID.class), eq(100L));
        order.verify(f.world).spawn(any(Location.class), eq(org.bukkit.entity.ItemDisplay.class),
                org.mockito.ArgumentMatchers.<java.util.function.Consumer<org.bukkit.entity.ItemDisplay>>any());
    }

    @Test void plinkoKeepsThePlayerAndAmountUntilTheLastBallLands() throws Exception {
        var f = new Fixture();
        var machine = f.plinko();
        var gameMachine = (GameMachine<?>) machine;
        var ballDisplays = new java.util.ArrayList<org.bukkit.entity.ItemDisplay>();
        var stack = mock(org.bukkit.inventory.ItemStack.class);
        when(f.funding.normalize(anyLong())).thenReturn(100L);
        when(f.world.spawn(any(Location.class), eq(org.bukkit.entity.ItemDisplay.class),
                org.mockito.ArgumentMatchers.<java.util.function.Consumer<org.bukkit.entity.ItemDisplay>>any()))
                .thenAnswer(call -> {
                    var ball = mock(org.bukkit.entity.ItemDisplay.class);
                    when(ball.getItemStack()).thenReturn(stack);
                    ballDisplays.add(ball);
                    return ball;
                });
        var light = PlinkoMachine.class.getDeclaredField("light");
        light.setAccessible(true);
        light.set(machine, mock(org.bukkit.entity.BlockDisplay.class));
        machine.setStake(f.alice, 125);
        machine.setStake(f.bob, 250);
        try (var models = mockStatic(GoldenEggOVO.TDCasino.render.model.ModelCarrierItems.class);
                var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            models.when(() -> GoldenEggOVO.TDCasino.render.model.ModelCarrierItems.resolve(anyString()))
                    .thenReturn(stack);
            click(machine, f.alice, "play");
            for (int tick = 0; tick < 4; tick++) machine.tick();
            click(machine, f.alice, "play");
            assertThrows(IllegalArgumentException.class, () -> machine.setStake(f.alice, 500));
            click(machine, f.bob, "play");
            assertEquals(2, ballDisplays.size());
            for (int tick = 0; tick < 93; tick++) machine.tick();
            assertEquals(1, gameMachine.resultState.pending());
            assertTrue(gameMachine.occupiedBy(f.aliceId));
            for (int tick = 0; tick < 4; tick++) machine.tick();
            assertEquals(0, gameMachine.resultState.pending());
            assertEquals(-50, gameMachine.resultState.totalNet());
            assertFalse(gameMachine.occupiedBy(f.aliceId));
            for (var ball : ballDisplays) verify(ball).remove();
            click(machine, f.bob, "play");
            assertEquals(250, gameMachine.round.stake());
            assertEquals(1, gameMachine.resultState.pending());
            assertEquals(0, gameMachine.resultState.totalNet());
            assertTrue(gameMachine.occupiedBy(f.bobId));
        }
        machine.close();
        verify(f.funding, times(2)).begin(125);
        verify(f.funding).begin(250);
        verify(f.funding, times(3)).settle(any(UUID.class), eq(100L));
    }

    private static void launch(PlinkoMachine machine) throws Exception {
        var action = PlinkoMachine.class.getDeclaredMethod("action", String.class);
        action.setAccessible(true);
        try { action.invoke(machine, "play"); }
        catch (InvocationTargetException error) { throw (Exception) error.getCause(); }
    }

    private static void click(GameMachine<?> machine, Player player, String action) throws Exception {
        var lastClick = GameMachine.class.getDeclaredField("lastClick");
        lastClick.setAccessible(true);
        lastClick.setLong(machine, 0);
        var targetType = Class.forName(GameMachine.class.getName() + "$Target");
        var targetConstructor = targetType.getDeclaredConstructors()[0];
        targetConstructor.setAccessible(true);
        var target = targetConstructor.newInstance(action, null, new org.joml.Matrix4d(),
                new org.bukkit.util.BoundingBox(), -1);
        var hitConstructor = GameMachine.TargetHit.class.getDeclaredConstructors()[0];
        hitConstructor.setAccessible(true);
        machine.click(player, (GameMachine.TargetHit) hitConstructor.newInstance(target, 1.0));
    }
}
