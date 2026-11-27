package GoldenEggOVO.TDCasino;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginSeparationTest {
    @Test void pluginDoesNotExposeATabletopBridge() {
        assertThrows(NoSuchMethodException.class, () -> TDCasinoPlugin.class.getMethod("companion"));
    }
}
