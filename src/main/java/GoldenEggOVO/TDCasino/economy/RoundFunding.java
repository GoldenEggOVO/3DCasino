package GoldenEggOVO.TDCasino.economy;

import java.util.UUID;

public interface RoundFunding {
    UUID begin(long cents);
    void increase(UUID round, long cents);
    void settle(UUID round, long returned);
    default long normalize(long returned) { return returned; }

    static RoundFunding none() {
        return new RoundFunding() {
            public UUID begin(long cents) { return UUID.randomUUID(); }
            public void increase(UUID round, long cents) {}
            public void settle(UUID round, long returned) {}
        };
    }
}
