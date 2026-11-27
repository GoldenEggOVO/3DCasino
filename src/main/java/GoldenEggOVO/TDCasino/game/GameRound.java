package GoldenEggOVO.TDCasino.game;

import static GoldenEggOVO.TDCasino.text.CasinoMessages.text;
import GoldenEggOVO.TDCasino.economy.RoundFunding;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

public abstract class GameRound {
    public static final long STAKE = 0;
    public static final long MIN_STAKE = 0;
    public static final long MAX_STAKE = 1_000_000_000L;
    protected final Random random;
    private long sequence;
    private RoundFunding funding = RoundFunding.none();
    private UUID paymentId;
    private boolean outcomeKnown;
    protected boolean active;
    protected boolean finished;
    protected long stake = STAKE;
    protected long payout;
    protected String result = text("round.idle");

    protected GameRound(Random random) {
        this.random = Objects.requireNonNull(random);
    }

    public final long sequence() { return sequence; }

    public final boolean active() {
        return active;
    }

    public final boolean finished() {
        return finished;
    }

    public final long stake() {
        return stake;
    }

    public final long payout() {
        return payout;
    }

    public final String result() {
        return result;
    }

    public final void funding(RoundFunding value) {
        if (active) {
            throw new IllegalStateException("error.active");
        }
        funding = Objects.requireNonNull(value);
    }

    protected final RoundFunding funding() {
        return funding;
    }

    public final UUID paymentId() {
        return paymentId;
    }

    public void setStake(long amount) {
        if (amount < MIN_STAKE || amount > MAX_STAKE) {
            throw new IllegalArgumentException("error.stake");
        }
        if (active) {
            throw new IllegalStateException("error.active");
        }
        stake = amount;
    }

    protected final void begin() {
        begin(stake);
    }

    protected final void begin(long amount) {
        UUID payment = funding.begin(amount);
        stake = amount;
        paymentId = payment;
        sequence++;
        active = true;
        finished = false;
        outcomeKnown = false;
        payout = 0;
        result = text("round.playing");
    }

    protected final void finish(long amount, String message) {
        payout = normalizePayout(amount);
        active = false;
        finished = true;
        result = text("round.result", "message", message, "amount", money(payout));
        settleFunding();
    }

    protected final void addStake(long delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("error.stake");
        }
        long amount = Math.addExact(stake, delta);
        funding.increase(paymentId, delta);
        stake = amount;
    }

    protected final void settleFunding() {
        outcomeKnown = true;
        funding.settle(paymentId, payout);
    }

    protected final long normalizePayout(long amount) {
        return funding.normalize(amount);
    }

    protected static String money(long cents) {
        return String.format(Locale.ROOT, "%.2f", cents / 100.0);
    }

    public final void abandon() {
        if (!active) {
            return;
        }
        if (!outcomeKnown) {
            payout = 0;
        }
        active = false;
        finished = true;
        settleFunding();
    }

    protected static int selection(String action) {
        if (action == null || !action.startsWith("select:")) {
            return -1;
        }
        try {
            return Integer.parseInt(action.substring(7));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
