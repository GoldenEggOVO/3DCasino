package GoldenEggOVO.TDCasino.game;

import static GoldenEggOVO.TDCasino.text.CasinoMessages.text;
import java.util.Random;

public abstract class StartedRound extends GameRound {
    private long configuredStake = STAKE;

    protected StartedRound(Random random) {
        super(random);
    }

    public final long configuredStake() {
        return configuredStake;
    }

    public final void setConfiguredStake(long amount) {
        if (!active && amount >= MIN_STAKE && amount <= MAX_STAKE) {
            setStake(amount);
        }
    }

    @Override
    public final void setStake(long amount) {
        super.setStake(amount);
        configuredStake = amount;
    }

    protected final void beginRound() {
        begin(configuredStake);
    }

    protected final void settle(long amount) {
        payout = normalizePayout(amount);
        active = false;
        finished = true;
        result = payout > 0 ? text("round.payout", "amount", money(payout)) : text("round.finished");
        settleFunding();
    }

    public abstract void start(long now);
}
