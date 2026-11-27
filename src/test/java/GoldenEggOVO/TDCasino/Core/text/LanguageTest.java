package GoldenEggOVO.TDCasino.Core.text;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LanguageTest {
    @TempDir Path temp;

    @Test void partialChineseCatalogPreservesUserTextAndFallsBackToBundledChinese() throws Exception {
        var defaults = Language.load(temp, "zh_CN", warning -> fail(warning));
        String custom = "\"casino.menu.title\": 'Custom title'\n";
        Files.writeString(temp.resolve("zh_CN.yml"), custom);
        var loaded = Language.load(temp, "zh_CN", warning -> fail(warning));
        assertEquals("Custom title", loaded.get("casino.menu.title"));
        assertEquals(defaults.get("pack.ready"), loaded.get("pack.ready"));
        assertEquals(custom, Files.readString(temp.resolve("zh_CN.yml")));
    }

    @Test void unknownKeysAndPlaceholdersCannotReplaceTheActiveCatalog() throws Exception {
        assertTrue(Language.reload(temp, "en_US", warning -> fail(warning)));
        var before = Language.component("casino.menu.title");
        Files.writeString(temp.resolve("en_US.yml"), "\"casino.menu.title\": 'Bad {unknown}'\n");
        var warnings = new java.util.ArrayList<String>();
        assertFalse(Language.reload(temp, "en_US", warnings::add));
        assertFalse(warnings.isEmpty());
        assertEquals(before, Language.component("casino.menu.title"));
    }

    @Test void bundledCatalogsHaveTheSameKeysAndParameterNames() {
        var english = Language.load(temp.resolve("en"), "en_US", warning -> fail(warning));
        var chinese = Language.load(temp.resolve("zh"), "zh_CN", warning -> fail(warning));
        assertEquals(english.keySet(), chinese.keySet());
        english.forEach((key, value) -> assertEquals(MessageText.placeholders(value), MessageText.placeholders(chinese.get(key)), key));
        assertFalse(english.keySet().stream().anyMatch(key -> key.startsWith("room.") || key.startsWith("piece.")));
        assertTrue(english.get("chat.prefix").contains("3DCasino"));
    }

    @Test void localeTraversalFallsBackWithoutReadingOutsideTheLanguageFolder() {
        var warnings = new java.util.ArrayList<String>();
        var loaded = Language.load(temp, "../secret", warnings::add);
        assertFalse(warnings.isEmpty());
        assertEquals("Settings", loaded.get("casino.menu.settings"));
    }
}
