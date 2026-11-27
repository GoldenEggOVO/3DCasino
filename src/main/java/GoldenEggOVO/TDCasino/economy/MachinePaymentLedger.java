package GoldenEggOVO.TDCasino.economy;

import GoldenEggOVO.TDCasino.Core.economy.EconomyAccess;

import GoldenEggOVO.TDCasino.Core.text.RuleViolation;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class MachinePaymentLedger {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CLOSED_HISTORY = 256;
    private enum State { DEBIT_IN_FLIGHT, ACTIVE, CREDIT_PENDING, CREDIT_IN_FLIGHT, REVIEW, CLOSED }

    private static final class Round {
        UUID id;
        UUID player;
        String machine;
        long stake;
        long debit;
        Long returned;
        int precision;
        State state;
    }

    private final Path journal;
    private final Supplier<EconomyAccess> bank;
    private final BooleanSupplier enabled;
    private final LongSupplier minCents;
    private final LongSupplier maxCents;
    private final Consumer<String> warning;
    private final Consumer<UUID> requestCredit;
    private final Map<UUID, Round> rounds = new LinkedHashMap<>();
    private boolean storageFailed;
    private boolean closed;

    public MachinePaymentLedger(Path journal, Supplier<EconomyAccess> bank, BooleanSupplier enabled,
                          LongSupplier minCents, LongSupplier maxCents, Consumer<String> warning) {
        this(journal, bank, enabled, minCents, maxCents, warning, null);
    }

    public MachinePaymentLedger(Path journal, Supplier<EconomyAccess> bank, BooleanSupplier enabled,
                          LongSupplier minCents, LongSupplier maxCents, Consumer<String> warning,
                          Consumer<UUID> requestCredit) {
        this.journal = journal.toAbsolutePath();
        this.requestCredit = requestCredit == null ? this::retry : requestCredit;
        this.bank = bank;
        this.enabled = enabled;
        this.minCents = minCents;
        this.maxCents = maxCents;
        this.warning = warning;
        if (!Files.exists(this.journal)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(this.journal)).getAsJsonObject();
            if (root.get("schema").getAsBigDecimal().intValueExact() != 1)
                throw new IllegalArgumentException("Unsupported Casino payment schema");
            for (var element : root.getAsJsonArray("rounds")) {
                JsonObject object = element.getAsJsonObject();
                object.get("stake").getAsBigDecimal().longValueExact();
                object.get("debit").getAsBigDecimal().longValueExact();
                object.get("precision").getAsBigDecimal().intValueExact();
                if (object.has("returned")) object.get("returned").getAsBigDecimal().longValueExact();
                Round round = JSON.fromJson(object, Round.class);
                validate(round);
                if (rounds.put(round.id, round) != null)
                    throw new IllegalArgumentException("Duplicate Casino payment round");
            }
        } catch (IOException | RuntimeException exception) {
            storageFailed = true;
            warning.accept("Cannot read Casino payment journal; paid entry disabled and original file retained: " + exception);
        }
    }

    public RoundFunding bind(UUID player, String machine) {
        Objects.requireNonNull(player);
        if (machine == null || machine.isBlank()) throw new IllegalArgumentException("Missing Casino machine");
        return new Binding(player, machine);
    }

    private final class Binding implements RoundFunding {
        final UUID player;
        final String machine;
        final Set<UUID> zero = new HashSet<>();
        final Set<UUID> settledZero = new LinkedHashSet<>();
        Round current;
        int precision = 2;

        Binding(UUID player, String machine) {
            this.player = player;
            this.machine = machine;
        }

        public UUID begin(long cents) {
            synchronized (MachinePaymentLedger.this) {
                if (cents < 0) throw failure("error.economy.amount", "Invalid Casino stake");
                if (cents == 0 || !enabled.getAsBoolean()) {
                    UUID id = UUID.randomUUID();
                    zero.add(id);
                    return id;
                }
                healthy();
                if (cents < minCents.getAsLong() || cents > maxCents.getAsLong())
                    throw failure("casino.error.stake", "Casino stake is outside the configured range");
                if (blocked(player))
                    throw failure("error.economy.review", "Unresolved Casino payment requires review");
                EconomyAccess provider = provider();
                precision = precision(provider);
                checkDebit(provider, player, cents, precision);
                Round round = new Round();
                round.id = UUID.randomUUID();
                round.player = player;
                round.machine = machine;
                round.precision = precision;
                current = round;
                rounds.put(round.id, round);
                debit(round, cents, provider);
                return round.id;
            }
        }

        public void increase(UUID id, long cents) {
            synchronized (MachinePaymentLedger.this) {
                if (cents < 0) throw failure("error.economy.amount", "Invalid Casino stake increase");
                if (zero.contains(id)) return;
                Round round = owned(id);
                healthy();
                if (round.state != State.ACTIVE || round.returned != null)
                    throw failure("error.economy.review", "Casino round is not available for a stake increase");
                if (cents == 0) return;
                if (!enabled.getAsBoolean()) throw failure("error.economy.unavailable", "Casino payments are disabled");
                if (cents > maxCents.getAsLong() - round.stake)
                    throw failure("casino.error.stake", "Casino stake exceeds the configured maximum");
                EconomyAccess provider = provider();
                checkDebit(provider, player, cents, round.precision);
                debit(round, cents, provider);
            }
        }

        public void settle(UUID id, long returned) {
            synchronized (MachinePaymentLedger.this) {
                if (returned < 0) throw failure("error.economy.amount", "Invalid Casino award");
                if (zero.remove(id)) {
                    settledZero.add(id);
                    if (settledZero.size() > 64) settledZero.remove(settledZero.iterator().next());
                    return;
                }
                if (settledZero.contains(id)) return;
                Round round = owned(id);
                if (round.returned != null) return;
                healthy();
                if (round.state != State.ACTIVE
                        && !(round.state == State.REVIEW && round.debit > 0 && round.stake > 0))
                    throw failure("error.economy.review", "Casino payment requires review");
                round.returned = rounded(returned, round.precision);
                if (round.state == State.ACTIVE)
                    round.state = round.returned == 0 ? State.CLOSED : State.CREDIT_PENDING;
                store();
                if (round.state == State.CREDIT_PENDING) requestCredit.accept(round.player);
            }
        }

        public long normalize(long cents) {
            if (cents < 0) throw failure("error.economy.amount", "Invalid Casino award");
            return rounded(cents, precision);
        }

        private Round owned(UUID id) {
            Round round = rounds.get(id);
            // A live game can repeat its final settlement after global history has advanced.
            if (round == null && current != null && current.id.equals(id) && current.state == State.CLOSED)
                round = current;
            if (round == null || !round.player.equals(player) || !round.machine.equals(machine))
                throw failure("error.economy.review", "Unknown Casino payment round");
            return round;
        }
    }

    public synchronized void recover() {
        if (storageFailed) return;
        for (Round round : List.copyOf(rounds.values())) {
            if (round.state == State.ACTIVE) {
                round.returned = 0L;
                round.state = State.CLOSED;
                store();
            } else if (round.state == State.DEBIT_IN_FLIGHT || round.state == State.CREDIT_IN_FLIGHT) {
                round.state = State.REVIEW;
                store();
                warning.accept("Casino payment requires manual review: " + round.id);
            }
        }
        retry();
        if (trimClosedHistory()) store();
    }

    public synchronized void retry() {
        if (storageFailed || closed) return;
        for (UUID player : rounds.values().stream().filter(round -> round.state == State.CREDIT_PENDING)
                .map(round -> round.player).distinct().toList()) requestCredit.accept(player);
    }

    public synchronized void retry(UUID player) {
        if (storageFailed || closed) return;
        for (Round round : List.copyOf(rounds.values()))
            if (round.player.equals(player)) credit(round);
    }

    public synchronized void close() {
        if (closed) return;
        try {
            recover();
        } finally {
            closed = true;
        }
    }

    public synchronized boolean blocked(UUID player) {
        return storageFailed || rounds.values().stream()
                .anyMatch(round -> round.player.equals(player) && round.state != State.CLOSED);
    }

    private void debit(Round round, long cents, EconomyAccess provider) {
        round.debit = cents;
        round.state = State.DEBIT_IN_FLIGHT;
        store();
        EconomyAccess.Transfer result;
        try {
            result = provider.withdraw(round.player, amount(cents));
        } catch (RuntimeException exception) {
            warning.accept("Casino debit outcome is uncertain for " + round.id + ": " + exception);
            result = EconomyAccess.Transfer.UNCERTAIN;
        }
        if (result == EconomyAccess.Transfer.SUCCESS) {
            round.stake = Math.addExact(round.stake, cents);
            round.debit = 0;
            round.state = State.ACTIVE;
        } else if (result == EconomyAccess.Transfer.REJECTED) {
            round.debit = 0;
            round.state = round.stake == 0 ? State.CLOSED : State.ACTIVE;
            if (round.stake == 0) round.returned = 0L;
        } else {
            round.state = State.REVIEW;
            warning.accept("Casino debit requires manual review: " + round.id);
        }
        store();
        if (result != EconomyAccess.Transfer.SUCCESS)
            throw failure(result == EconomyAccess.Transfer.REJECTED ? "casino.error.debit" : "error.economy.review",
                    result == EconomyAccess.Transfer.REJECTED
                            ? "Casino stake withdrawal was rejected" : "Casino payment requires review");
    }

    private void credit(Round round) {
        if (storageFailed || round.state != State.CREDIT_PENDING) return;
        EconomyAccess provider;
        try {
            provider = bank.get();
            if (provider == null || !provider.available()
                    || rounded(round.returned, precision(provider)) != round.returned
                    || !provider.supports(amount(round.returned))) return;
        } catch (RuntimeException exception) {
            warning.accept("Casino credit provider is unavailable for " + round.id + ": " + exception);
            return;
        }
        round.state = State.CREDIT_IN_FLIGHT;
        store();
        EconomyAccess.Transfer result;
        try {
            result = provider.deposit(round.player, amount(round.returned));
        } catch (RuntimeException exception) {
            warning.accept("Casino credit outcome is uncertain for " + round.id + ": " + exception);
            result = EconomyAccess.Transfer.UNCERTAIN;
        }
        round.state = result == EconomyAccess.Transfer.SUCCESS ? State.CLOSED
                : result == EconomyAccess.Transfer.REJECTED ? State.CREDIT_PENDING : State.REVIEW;
        if (round.state == State.REVIEW)
            warning.accept("Casino credit requires manual review: " + round.id);
        store();
    }

    private EconomyAccess provider() {
        try {
            EconomyAccess provider = bank.get();
            if (provider != null && provider.available()) return provider;
        } catch (RuntimeException exception) {
            warning.accept("Casino economy provider is unavailable: " + exception);
        }
        throw failure("error.economy.unavailable", "Casino economy provider is unavailable");
    }

    private void checkDebit(EconomyAccess provider, UUID player, long cents, int precision) {
        try {
            double amount = amount(cents);
            if (rounded(cents, precision) != cents || rounded(cents, precision(provider)) != cents
                    || !provider.supports(amount))
                throw failure("error.economy.amount", "Casino amount is unsupported by the currency");
            var balance = provider.balance(player);
            if (balance.isEmpty() || !Double.isFinite(balance.getAsDouble()))
                throw failure("error.economy.unavailable", "Casino balance is unavailable");
            if (balance.getAsDouble() < amount)
                throw failure("casino.error.debit", "Insufficient balance for Casino stake");
        } catch (RuleViolation exception) {
            throw exception;
        } catch (RuntimeException exception) {
            warning.accept("Cannot validate Casino stake for " + player + ": " + exception);
            throw failure("error.economy.unavailable", "Casino balance is unavailable");
        }
    }

    private int precision(EconomyAccess provider) {
        try {
            int digits = provider.fractionalDigits();
            return digits < 0 ? 2 : Math.min(2, digits);
        } catch (RuntimeException exception) {
            warning.accept("Cannot read Casino currency precision: " + exception);
            throw failure("error.economy.unavailable", "Casino currency precision is unavailable");
        }
    }

    private static long rounded(long cents, int precision) {
        long unit = precision == 0 ? 100 : precision == 1 ? 10 : 1;
        return cents / unit * unit;
    }

    private static double amount(long cents) {
        double amount = BigDecimal.valueOf(cents, 2).doubleValue();
        try {
            if (BigDecimal.valueOf(amount).movePointRight(2).longValueExact() != cents)
                throw failure("error.economy.amount", "Casino amount cannot be represented exactly");
        } catch (ArithmeticException exception) {
            throw failure("error.economy.amount", "Casino amount cannot be represented exactly");
        }
        return amount;
    }

    private void healthy() {
        if (storageFailed) throw failure("error.economy.storage", "Casino payment journal is unavailable");
        if (closed) throw failure("error.economy.unavailable", "Casino payments are closed");
    }

    private void store() {
        if (storageFailed) throw failure("error.economy.storage", "Casino payment journal is unavailable");
        try {
            trimClosedHistory();
            Files.createDirectories(journal.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("schema", 1);
            root.add("rounds", JSON.toJsonTree(rounds.values()));
            Path temporary = journal.resolveSibling(journal.getFileName() + ".tmp");
            byte[] contents = JSON.toJson(root).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(contents);
                while (buffer.hasRemaining()) output.write(buffer);
                output.force(true);
            }
            Files.move(temporary, journal, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException exception) {
            storageFailed = true;
            warning.accept("Cannot store Casino payment journal; paid entry disabled: " + exception);
            throw failure("error.economy.storage", "Casino payment journal is unavailable");
        }
    }

    private boolean trimClosedHistory() {
        int closed = 0;
        for (Round round : rounds.values()) if (round.state == State.CLOSED) closed++;
        int excess = closed - CLOSED_HISTORY;
        if (excess <= 0) return false;
        var iterator = rounds.values().iterator();
        while (iterator.hasNext() && excess > 0) {
            if (iterator.next().state == State.CLOSED) {
                iterator.remove();
                excess--;
            }
        }
        return true;
    }

    private static void validate(Round round) {
        if (round.id == null || round.player == null || round.machine == null || round.machine.isBlank()
                || round.state == null || round.precision < 0 || round.precision > 2
                || round.stake < 0 || round.debit < 0 || round.returned != null && round.returned < 0
                || rounded(round.stake, round.precision) != round.stake
                || rounded(round.debit, round.precision) != round.debit
                || round.returned != null && rounded(round.returned, round.precision) != round.returned)
            throw new IllegalArgumentException("Invalid Casino payment record");
        Math.addExact(round.stake, round.debit);
        boolean valid = switch (round.state) {
            case ACTIVE -> round.stake > 0 && round.debit == 0 && round.returned == null;
            case DEBIT_IN_FLIGHT -> round.debit > 0 && round.returned == null;
            case CREDIT_PENDING, CREDIT_IN_FLIGHT -> round.stake > 0 && round.debit == 0
                    && round.returned != null && round.returned > 0;
            case REVIEW -> round.debit > 0 && (round.returned == null || round.stake > 0)
                    || round.stake > 0 && round.debit == 0 && round.returned != null && round.returned > 0;
            case CLOSED -> round.debit == 0 && round.returned != null;
        };
        if (!valid) throw new IllegalArgumentException("Inconsistent Casino payment record");
    }

    private static RuleViolation failure(String key, String message) {
        return new RuleViolation(key, message);
    }
}
