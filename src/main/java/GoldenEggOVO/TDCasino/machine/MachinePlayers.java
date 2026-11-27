package GoldenEggOVO.TDCasino.machine;

import GoldenEggOVO.TDCasino.game.GameRound;
import GoldenEggOVO.TDCasino.Core.player.PlayerSessions;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
final class MachinePlayers<R extends GameRound> {
    static final int IDLE_TICKS = 20 * 120;
    static final class Data<R extends GameRound> {
        R round;
        final MachineResultState resultState = new MachineResultState();
        long observedRound;
        Data(R round) { this.round = round; }
        void settleFinishedRound() {
            long sequence = round.sequence();
            if (sequence == 0 || !round.finished()) return;
            if (sequence != observedRound) {
                observedRound = sequence;
                resultState.launch(sequence, round.stake());
            }
            resultState.reviseStake(sequence, round.stake());
            resultState.settle(sequence, round.payout());
        }
    }
    private final Supplier<R> factory;
    private final java.util.function.BiConsumer<UUID, Data<R>> initialize;
    private final PlayerSessions sessions;
    private final String key;
    private final Map<UUID, Data<R>> data = new HashMap<>();
    private UUID player;
    private boolean started;
    private int idleTicks;

    MachinePlayers(Supplier<R> factory) { this(factory, (id, data) -> {}); }
    MachinePlayers(Supplier<R> factory, java.util.function.BiConsumer<UUID, Data<R>> initialize) {
        this(factory, initialize, new PlayerSessions(), "casino:test");
    }
    MachinePlayers(Supplier<R> factory, java.util.function.BiConsumer<UUID, Data<R>> initialize,
            PlayerSessions sessions, String key) {
        this.factory = factory;
        this.initialize = initialize;
        this.sessions = sessions;
        this.key = key;
    }
    Data<R> data(UUID id) {
        return data.computeIfAbsent(id, key -> {
            var state = new Data<>(factory.get());
            initialize.accept(key, state);
            return state;
        });
    }
    UUID player() { return player; }
    boolean accepts(UUID id) { return player == null || player.equals(id); }
    boolean claim(UUID id) {
        if (!accepts(id)) return false;
        if (player == null && !sessions.reserve(id, key)) return false;
        player = id;
        idleTicks = 0;
        return true;
    }
    void started() { started = true; }
    void update(boolean inProgress) {
        if (player != null && started && !inProgress) release();
    }
    void tick() { if (player != null) idleTicks++; }
    boolean expired() { return player != null && idleTicks >= IDLE_TICKS; }
    void releaseClaim() { release(); }
    void abandon() {
        try {
            if (player != null) {
                var state = data(player);
                try {
                    state.round.abandon();
                } finally {
                    state.settleFinishedRound();
                }
                var profile = PlayerMachineProfileStore.Profile.capture(state.round, state.resultState);
                state.round = factory.get();
                state.observedRound = 0;
                profile.restore(state.round, state.resultState);
            }
        } finally {
            release();
        }
    }
    private void release() {
        if (player != null) sessions.release(player, key);
        player = null;
        started = false;
        idleTicks = 0;
    }
}
