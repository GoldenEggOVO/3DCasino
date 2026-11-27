package GoldenEggOVO.TDCasino.text;

import GoldenEggOVO.TDCasino.Core.text.MessageText;
import GoldenEggOVO.TDCasino.Core.text.RuleViolation;
import net.kyori.adventure.text.Component;

public final class CasinoMessages {
    private CasinoMessages() {}

    public static final class Failure extends IllegalArgumentException {
        private final String key;
        private final Object[] values;
        public Failure(String key, Object... values) {
            super(key);
            this.key = key;
            this.values = values.clone();
        }
    }

    public static Component component(String key, Object... values) {
        return GoldenEggOVO.TDCasino.Core.text.Language.component("casino." + key, values);
    }

    public static String text(String key, Object... values) {
        return MessageText.plain(component(key, values));
    }

    public static long revision() { return GoldenEggOVO.TDCasino.Core.text.Language.generation(); }

    public static String error(Throwable error) {
        if (error instanceof Failure failure) return text(failure.key, failure.values);
        if (error instanceof RuleViolation violation)
            return MessageText.plain(GoldenEggOVO.TDCasino.Core.text.Language.error(violation));
        if (error.getMessage() != null && GoldenEggOVO.TDCasino.Core.text.Language.contains("casino." + error.getMessage()))
            return text(error.getMessage());
        return text("error.unexpected");
    }

    public static Component modelComponent(String model, int index, String fallback) {
        String key = "models." + model + "." + index;
        return GoldenEggOVO.TDCasino.Core.text.Language.contains("casino." + key)
                ? component(key) : MessageText.render(fallback);
    }
}
