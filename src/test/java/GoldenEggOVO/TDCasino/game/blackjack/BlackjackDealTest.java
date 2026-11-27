package GoldenEggOVO.TDCasino.game.blackjack;

import GoldenEggOVO.TDCasino.CasinoModule;
import GoldenEggOVO.TDCasino.Core.player.PlayerSessions;
import GoldenEggOVO.TDCasino.Core.render.ResourcePackState;
import GoldenEggOVO.TDCasino.TDCasinoPlugin;
import GoldenEggOVO.TDCasino.economy.MachinePaymentLedger;
import GoldenEggOVO.TDCasino.economy.RoundFunding;
import GoldenEggOVO.TDCasino.game.GameRound;
import GoldenEggOVO.TDCasino.interaction.MachineSettingsTargets;
import GoldenEggOVO.TDCasino.machine.GameMachine;
import GoldenEggOVO.TDCasino.machine.MachineManager;
import GoldenEggOVO.TDCasino.machine.definition.MachineDefinition;
import GoldenEggOVO.TDCasino.render.model.ModelCarrierItems;
import org.bukkit.*;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import GoldenEggOVO.TDCasino.Core.platform.PlatformScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlackjackDealTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @Test void openingCardsDepartInPlayerDealerOrderTwoTicksApart() throws Exception {
        var f = new Fixture();
        f.machine.refresh();
        assertTrue(f.sounds.isEmpty(), "Refreshing the hand must not play the deal cue before departure");
        f.frame(1);
        assertEquals(List.of(1), f.sounds);
        f.frame(2);
        assertEquals(f.origins.get(f.cards("dealerCards").getFirst()), f.positions.get(f.cards("dealerCards").getFirst()));
        f.frame(3); f.frame(4); f.frame(5); f.frame(6);
        assertEquals(f.origins.get(f.cards("dealerCards").get(1)), f.positions.get(f.cards("dealerCards").get(1)));
        assertNotEquals(f.origins.get(f.cards("playerCards").get(1)), f.positions.get(f.cards("playerCards").get(1)));
        f.frame(7);
        assertEquals(List.of(1, 3, 5, 7), f.sounds);
        f.frame(9);
        assertEquals(f.machinePoint(-.215, .96, .35), f.positions.get(f.cards("playerCards").getFirst()));
        f.frame(15);
        assertEquals(f.machinePoint(.215, .964, -.32), f.positions.get(f.cards("dealerCards").get(1)));
        f.machine.refresh(); f.frame(16);
        assertEquals(List.of(1, 3, 5, 7), f.sounds, "Unchanged refreshes must not replay deal cues");
    }

    @Test void additionalCardsWaitBehindPendingDealsAndFutureCardsStayInTheShoe() throws Exception {
        var f = new Fixture();
        f.machine.refresh(); f.frame(1); f.frame(2);
        f.player.add(5);
        f.machine.refresh();
        var appended = f.cards("playerCards").get(2);
        f.frame(3); f.frame(4); f.frame(5); f.frame(6); f.frame(7); f.frame(8);
        assertEquals(f.origins.get(appended), f.positions.get(appended));
        assertEquals(List.of(1, 3, 5, 7), f.sounds);
        f.frame(9);
        assertEquals(List.of(1, 3, 5, 7, 9), f.sounds);
        f.frame(17);
        assertEquals(f.machinePoint(.43, .968, .35), f.positions.get(appended));
    }

    @Test void trimmingAndRestartingDiscardUnstartedDealCues() throws Exception {
        var f = new Fixture();
        f.machine.refresh(); f.frame(1);
        f.player.clear(); f.dealer.clear();
        f.machine.refresh();
        for (int tick = 2; tick <= 20; tick++) f.frame(tick);
        assertEquals(List.of(1), f.sounds);
        f.player.addAll(List.of(1, 2)); f.dealer.addAll(List.of(3, 4));
        f.machine.action("start");
        f.frame(21); f.frame(22); f.frame(23); f.frame(24); f.frame(25); f.frame(26); f.frame(27);
        assertEquals(List.of(1, 21, 23, 25, 27), f.sounds);
    }

    @Test void pendingCardsHaveSeparateShoeLayersAndFlyFromTheirOwnLayer() throws Exception {
        var f = new Fixture(); f.machine.refresh();
        var cards = List.of(f.cards("playerCards").get(0), f.cards("dealerCards").get(0),
                f.cards("playerCards").get(1), f.cards("dealerCards").get(1));
        var targets = List.of(f.machinePoint(-.215, .96, .35), f.machinePoint(-.215, .96, -.32),
                f.machinePoint(.215, .964, .35), f.machinePoint(.215, .964, -.32));
        for (int i = 0; i < cards.size(); i++) {
            assertEquals(f.machinePoint(1.3, 1.1 - .006 * i, -.55), f.origins.get(cards.get(i)));
            if (i > 0) {
                double gap = f.origins.get(cards.get(i - 1)).getY() - f.origins.get(cards.get(i)).getY();
                assertTrue(gap > .005375, "Shoe layers must separate the packed card thickness");
                assertTrue(gap > .00344, "Shoe layers must separate the native card thickness");
            }
        }
        for (int tick = 1; tick <= 15; tick++) {
            f.frame(tick);
            for (int i = 0; i < cards.size(); i++) {
                int start = 1 + i * 2;
                var origin = f.origins.get(cards.get(i));
                if (tick <= start) assertEquals(origin, f.positions.get(cards.get(i)));
                if (tick == start + 4) assertEquals(origin.clone().add(targets.get(i).toVector()
                        .subtract(origin.toVector()).multiply(.5))
                        .add(0, .045 * origin.distance(targets.get(i)), 0), f.positions.get(cards.get(i)));
                if (tick >= start + 8) assertEquals(targets.get(i), f.positions.get(cards.get(i)));
            }
        }
        assertEquals(List.of(1, 3, 5, 7), f.sounds);
    }

    @Test void appendingAfterATrimKeepsLayersDistinctAndAnEmptyQueueRestartsAtTheTop() throws Exception {
        var f = new Fixture(); f.machine.refresh(); f.frame(1);
        f.player.add(5); f.machine.refresh();
        assertEquals(f.machinePoint(1.3, 1.076, -.55), f.origins.get(f.cards("playerCards").get(2)));
        f.player.add(6); f.machine.refresh();
        var trimmed = f.cards("playerCards").get(3);
        assertEquals(f.machinePoint(1.3, 1.07, -.55), f.origins.get(trimmed));
        f.player.removeLast(); f.machine.refresh();
        verify(trimmed).remove();
        f.player.add(7); f.machine.refresh();
        var replacement = f.cards("playerCards").get(3);
        assertEquals(f.machinePoint(1.3, 1.064, -.55), f.origins.get(replacement));
        for (int tick = 2; tick <= 19; tick++) f.frame(tick);
        assertEquals(f.machinePoint(.645, .972, .35), f.positions.get(replacement));
        f.player.add(8); f.machine.refresh();
        assertEquals(f.shoe, f.origins.get(f.cards("playerCards").get(4)), "An empty slide queue reuses the top shoe layer");
        f.player.clear(); f.dealer.clear(); f.machine.refresh();
        f.player.addAll(List.of(1, 2)); f.dealer.addAll(List.of(3, 4));
        f.machine.action("start");
        assertEquals(f.shoe, f.origins.get(f.cards("playerCards").getFirst()));
        assertEquals(f.machinePoint(1.3, 1.082, -.55), f.origins.get(f.cards("dealerCards").get(1)));
    }

    @Test void realClicksQueueHitThenStandWhileRejectingDoubleAfterAHit() throws Exception {
        try (var models = materialCards()) {
            var f = new Fixture(true);
            f.click("start");
            assertEquals(List.of(0, 2), f.realRound.player());
            assertEquals(List.of(1, 3), f.realRound.dealer());
            f.frames(1, 3);
            f.click("hit");
            assertEquals(List.of(0, 2, 4), f.realRound.player());
            assertTrue(f.realRound.active());
            f.frames(4, 6);
            f.click("double");
            assertEquals(100, f.realRound.stake(), "Double is unavailable after a third card");
            verify(f.funding, never()).increase(any(), anyLong());
            f.frames(7, 9);
            f.click("stand");
            assertTrue(f.realRound.finished());
            assertEquals(List.of(1, 3, 5, 6), f.realRound.dealer());
            assertEquals(100, f.realRound.payout());
            assertEquals(7, f.realRound.cardsDrawn());
            f.click("start");
            assertEquals(1, f.realRound.sequence(), "Finished cards still in flight keep the busy gate closed");
            f.frames(10, 21);
            assertEquals(List.of(1, 3, 5, 7, 9, 11, 13), f.sounds);
            assertEquals(f.machinePoint(.43, .968, .35), f.positions.get(f.cards("playerCards").get(2)));
            assertEquals(f.machinePoint(.645, .972, -.32), f.positions.get(f.cards("dealerCards").get(3)));
            verify(f.funding).begin(100);
            verify(f.funding).settle(f.transaction, 100);
            verifyNoMoreInteractions(ignoreStubs(f.funding));
        }
    }

    @Test void aRealDoubleClickDuringOpeningDealsAppendsCardsWithoutChangingItsFunding() throws Exception {
        try (var models = materialCards()) {
            var f = new Fixture(true);
            f.click("start"); f.frames(1, 3);
            f.click("double");
            assertTrue(f.realRound.finished());
            assertEquals(200, f.realRound.stake());
            assertEquals(200, f.realRound.payout());
            assertEquals(List.of(0, 2, 4), f.realRound.player());
            assertEquals(List.of(1, 3, 5, 6), f.realRound.dealer());
            f.frames(4, 21);
            assertEquals(List.of(1, 3, 5, 7, 9, 11, 13), f.sounds);
            verify(f.funding).begin(100);
            verify(f.funding).increase(f.transaction, 100);
            verify(f.funding).settle(f.transaction, 200);
            verifyNoMoreInteractions(ignoreStubs(f.funding));
        }
    }

    @Test void changingAListenersPackDuringRealOpeningDealsOnlyChangesLaterCues() throws Exception {
        try (var models = materialCards()) {
            var f = new Fixture(true);
            f.click("start"); f.frame(1);
            when(f.pack.packed(f.listener)).thenReturn(true);
            f.frame(2);
            assertEquals(List.of("1:native"), f.routes, "Switching pack state must not replay an earlier cue");
            f.frames(3, 4);
            when(f.pack.packed(f.listener)).thenReturn(false);
            f.frames(5, 15);
            assertEquals(List.of("1:native", "3:packed", "5:native", "7:native"), f.routes);
            assertEquals(List.of(0, 2), f.realRound.player());
            assertEquals(List.of(1, 3), f.realRound.dealer());
            assertEquals(f.machinePoint(-.215, .96, .35), f.positions.get(f.cards("playerCards").getFirst()));
            assertEquals(f.machinePoint(.215, .964, -.32), f.positions.get(f.cards("dealerCards").get(1)));
            verify(f.funding).begin(100);
            verify(f.funding, never()).increase(any(), anyLong());
            verify(f.funding, never()).settle(any(), anyLong());
        }
    }

    @Test void managerCloseWithPendingDealsPreventsAllLaterManagedSoundsAndEntityUpdates() throws Exception {
        try (var models = materialCards()) {
            var f = new Fixture(true);
            f.click("start"); f.frame(1);
            var running = mock(MachineManager.class, CALLS_REAL_METHODS);
            var keyType = Class.forName(MachineManager.class.getName() + "$Key");
            var constructor = keyType.getDeclaredConstructors()[0]; constructor.setAccessible(true);
            var machines = new LinkedHashMap<Object, GameMachine<?>>();
            var key = constructor.newInstance(f.machine.owner(), "blackjack");
            machines.put(key, f.machine);
            setField(running, "machines", machines);
            setField(running, "placements", new LinkedHashMap<>());
            setField(running, "plugin", f.plugin);
            var task = mock(PlatformScheduler.Task.class);
            setField(running, "ticks", new LinkedHashMap<>(Map.of(key, task)));
            setField(running, "restoring", new java.util.HashSet<>());
            assertThrows(IllegalArgumentException.class, () -> running.remove(f.machine),
                    "Removing an occupied machine must retain the real management gate");
            assertTrue(running.contains(f.machine));
            assertEquals(List.of(1), f.sounds);
            var cards = new ArrayList<>(f.positions.keySet());
            running.close();
            assertFalse(running.contains(f.machine));
            verify(task).cancel();
            cards.forEach(card -> verify(card).remove());
            cards.forEach(org.mockito.Mockito::clearInvocations); clearInvocations(f.listener);
            var tick = MachineManager.class.getDeclaredMethod("tick", GameMachine.class); tick.setAccessible(true);
            for (int frame = 2; frame <= 25; frame++) tick.invoke(running, f.machine);
            assertEquals(List.of(1), f.sounds);
            for (var card : cards) {
                verify(card, never()).teleport(any(Location.class));
                verify(card, never()).setTransformation(any());
                verify(card, never()).setItemStack(any());
            }
            verifyNoInteractions(f.listener);
            verify(f.funding).begin(100);
            verify(f.funding).settle(f.transaction, 0);
            verifyNoMoreInteractions(ignoreStubs(f.funding));
        }
    }

    private static MockedStatic<ModelCarrierItems> materialCards() {
        var models = mockStatic(ModelCarrierItems.class);
        models.when(() -> ModelCarrierItems.resolve(anyString())).thenReturn(new ItemStack(Material.PAPER));
        return models;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value); return; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static final class Fixture {
        final List<Integer> player = new ArrayList<>(List.of(1, 2)), dealer = new ArrayList<>(List.of(3, 4));
        final List<Integer> sounds = new ArrayList<>();
        final List<String> routes = new ArrayList<>();
        final Map<ItemDisplay, Location> positions = new HashMap<>();
        final Map<ItemDisplay, Location> origins = new HashMap<>();
        final World world = mock(World.class);
        final Player listener = mock(Player.class);
        final TDCasinoPlugin plugin = mock(TDCasinoPlugin.class);
        final ResourcePackState pack = mock(ResourcePackState.class);
        final RoundFunding funding = mock(RoundFunding.class);
        final UUID transaction = UUID.randomUUID();
        final BlackjackRound realRound;
        final BlackjackMachine machine;
        final Location shoe;
        int age;

        Fixture() throws Exception { this(false); }

        @SuppressWarnings("unchecked") Fixture(boolean realRules) throws Exception {
            var manager = mock(MachineManager.class);
            var casino = mock(CasinoModule.class); var payments = mock(MachinePaymentLedger.class);
            var pluginGetter = MachineManager.class.getDeclaredMethod("plugin"); pluginGetter.setAccessible(true);
            when(pluginGetter.invoke(manager)).thenReturn(plugin);
            when(plugin.sessions()).thenReturn(new PlayerSessions());
            when(plugin.casino()).thenReturn(casino); when(casino.payments()).thenReturn(payments);
            when(plugin.resourcePackState()).thenReturn(pack);
            when(plugin.machineSettings()).thenReturn(mock(MachineSettingsTargets.class));
            when(payments.bind(any(), anyString())).thenReturn(realRules ? funding : RoundFunding.none());
            when(funding.begin(anyLong())).thenReturn(transaction);
            when(funding.normalize(anyLong())).thenAnswer(call -> call.getArgument(0));
            GoldenEggOVO.TDCasino.audio.SoundListeners.install(plugin, pack::packed, listener);
            doAnswer(call -> { sounds.add(age); routes.add(age + ":native"); return null; }).when(listener)
                    .playSound(any(Location.class), eq(Sound.ITEM_BOOK_PAGE_TURN), any(SoundCategory.class), anyFloat(), anyFloat());
            doAnswer(call -> { sounds.add(age); routes.add(age + ":packed"); return null; }).when(listener)
                    .playSound(any(Location.class), eq("3dcasino:cards.deal"), any(SoundCategory.class), anyFloat(), anyFloat());
            var owner = UUID.randomUUID(); when(listener.getUniqueId()).thenReturn(owner);
            machine = new BlackjackMachine(manager, owner, new Location(world, 3, 64, 5), MachineDefinition.builtin("blackjack"));
            if (realRules) {
                var field = GameMachine.class.getDeclaredField("round"); field.setAccessible(true);
                realRound = (BlackjackRound) field.get(machine);
                setField(realRound, "random", new Random(0) {
                    @Override public int nextInt(int bound) { return bound - 1; }
                });
                realRound.setStake(100);
            } else {
                realRound = null;
                var round = mock(BlackjackRound.class);
                when(round.player()).thenAnswer(call -> List.copyOf(player));
                when(round.dealer()).thenAnswer(call -> List.copyOf(dealer));
                when(round.active()).thenReturn(true);
                set("round", round);
            }
            set("readout", mock(TextDisplay.class));
            shoe = machinePoint(1.3, 1.1, -.55);
            when(world.spawn(any(Location.class), eq(ItemDisplay.class), any(Consumer.class))).thenAnswer(call -> {
                ItemDisplay card = mock(ItemDisplay.class);
                positions.put(card, call.getArgument(0, Location.class).clone());
                origins.put(card, call.getArgument(0, Location.class).clone());
                when(card.getLocation()).thenAnswer(ignored -> positions.get(card).clone());
                doAnswer(move -> { positions.put(card, move.getArgument(0, Location.class).clone()); return true; })
                        .when(card).teleport(any(Location.class));
                var stack = new ItemStack[] {new ItemStack(Material.PAPER)};
                doAnswer(change -> { stack[0] = change.getArgument(0); return null; }).when(card).setItemStack(any());
                when(card.getItemStack()).thenAnswer(ignored -> stack[0]);
                call.getArgument(2, Consumer.class).accept(card);
                return card;
            });
        }

        Location machinePoint(double x, double y, double z) throws Exception {
            var at = GameMachine.class.getDeclaredMethod("at", double.class, double.class, double.class);
            at.setAccessible(true); return (Location) at.invoke(machine, x, y, z);
        }
        @SuppressWarnings("unchecked") List<ItemDisplay> cards(String field) throws Exception {
            var f = BlackjackMachine.class.getDeclaredField(field); f.setAccessible(true); return (List<ItemDisplay>) f.get(machine);
        }
        void set(String name, Object value) throws Exception {
            setField(machine, name, value);
        }
        void frame(int tick) throws Exception { age = tick; set("age", tick); machine.animate(); }
        void frames(int first, int last) throws Exception { for (int tick = first; tick <= last; tick++) frame(tick); }
        void click(String action) throws Exception {
            Thread.sleep(160);
            var targetType = Class.forName(GameMachine.class.getName() + "$Target");
            var constructor = targetType.getDeclaredConstructors()[0]; constructor.setAccessible(true);
            var target = constructor.newInstance(action, null, new org.joml.Matrix4d(), new org.bukkit.util.BoundingBox(), -1);
            var hitType = Class.forName(GameMachine.class.getName() + "$TargetHit");
            var hitConstructor = hitType.getDeclaredConstructors()[0]; hitConstructor.setAccessible(true);
            var click = GameMachine.class.getDeclaredMethod("click", Player.class, hitType); click.setAccessible(true);
            click.invoke(machine, listener, hitConstructor.newInstance(target, 1.0));
        }
    }
}
