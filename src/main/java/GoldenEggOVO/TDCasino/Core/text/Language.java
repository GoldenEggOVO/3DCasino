package GoldenEggOVO.TDCasino.Core.text;

import org.bukkit.plugin.java.JavaPlugin;
import GoldenEggOVO.TDCasino.Core.text.RuleMessage;
import GoldenEggOVO.TDCasino.Core.text.RuleViolation;
import GoldenEggOVO.TDCasino.Core.text.MessageText;

import net.kyori.adventure.text.Component;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class Language {
    private static final Map<String, String> ENGLISH = bundled("en_US");
    private static final Map<String, String> CHINESE = bundled("zh_CN");
    private record Catalogue(Map<String, String> templates, Map<String, Component> labels) {
        Catalogue(Map<String, String> templates) {
            this(templates, new ConcurrentHashMap<>());
        }
    }

    private static volatile Catalogue current = new Catalogue(ENGLISH);
    private static long generation;

    public static Component component(String key, Object... parameters) {
        Catalogue catalogue = current;
        String template = catalogue.templates().getOrDefault(key, key);
        if (parameters.length != 0 || !catalogue.templates().containsKey(key))
            return MessageText.render(template, parameters);
        return catalogue.labels().computeIfAbsent(key, ignored -> MessageText.render(template));
    }

    static Component message(RuleMessage message) {
        List<Object> parameters = new ArrayList<>();
        message.parameters()
                .forEach(
                        (name, value) -> {
                            parameters.add(name);
                            parameters.add(value);
                        });
        return component(message.key(), parameters.toArray());
    }

    public static Component error(IllegalArgumentException exception) {
        return exception instanceof RuleViolation violation
                ? message(violation.message())
                : component("error.invalid");
    }

    public static boolean contains(String key) { return current.templates().containsKey(key); }

    public static long generation() {
        return generation;
    }

    public static void load(JavaPlugin plugin) {
        current =
                new Catalogue(load(
                        plugin.getDataFolder().toPath().resolve("languages"),
                        plugin.getConfig().getString("language", "en_US"),
                        plugin.getLogger()::warning));
        generation++;
    }

    public static Map<String, String> load(Path folder, String locale, Consumer<String> warning) {
        Map<String, String> entries = new LinkedHashMap<>(ENGLISH);
        try {
            Files.createDirectories(folder);
            for (String language : List.of("en_US", "zh_CN")) {
                Path file = folder.resolve(language + ".yml");
                if (!Files.exists(file))
                    try (InputStream input =
                            Language.class.getResourceAsStream("/languages/" + language + ".yml")) {
                        if (input == null)
                            throw new IOException("Missing bundled language: " + language);
                        Files.copy(input, file);
                    }
            }
        } catch (IOException exception) {
            warning.accept("Cannot initialize languages: " + exception.getMessage());
            return ENGLISH;
        }
        if (locale == null || !locale.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) {
            warning.accept("Invalid language filename; using en_US");
            locale = "en_US";
        }
        read(folder.resolve("en_US.yml"), entries, warning);
        if (locale.equals("zh_CN")) entries.putAll(CHINESE);
        if (!locale.equals("en_US")) read(folder.resolve(locale + ".yml"), entries, warning);
        return Map.copyOf(entries);
    }

    public static boolean reload(Path folder, String locale, Consumer<String> warning) {
        List<String> problems = new ArrayList<>();
        Map<String, String> candidate = load(folder, locale, problems::add);
        problems.forEach(warning);
        if (!problems.isEmpty()) return false;
        current = new Catalogue(candidate);
        generation++;
        return true;
    }

    private static void read(Path file, Map<String, String> entries, Consumer<String> warning) {
        try {
            YamlConfiguration yaml = yaml();
            yaml.load(file.toFile());
            for (var entry : flatten(yaml, "").entrySet()) {
                String key = entry.getKey();
                try {
                    String baseline = ENGLISH.get(key);
                    if (baseline == null)
                        throw new IllegalArgumentException(
                                "Unknown message key; use the current languages catalog");
                    if (!(entry.getValue() instanceof String value))
                        throw new IllegalArgumentException("Expected a string");
                    if (!MessageText.placeholders(baseline)
                            .containsAll(MessageText.placeholders(value)))
                        throw new IllegalArgumentException(
                                "Unknown placeholder; expected "
                                        + MessageText.placeholders(baseline));
                    MessageText.validate(value);
                    entries.put(key, value);
                } catch (IllegalArgumentException exception) {
                    warning.accept(
                            file.getFileName() + " [" + key + "]: " + exception.getMessage());
                }
            }
        } catch (Exception exception) {
            warning.accept("Cannot load " + file.getFileName() + ": " + exception.getMessage());
        }
    }

    private static Map<String, String> bundled(String locale) {
        try (InputStream input =
                Language.class.getResourceAsStream("/languages/" + locale + ".yml")) {
            if (input == null) throw new IOException("Missing bundled language");
            YamlConfiguration yaml = yaml();
            yaml.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            Map<String, String> values = new LinkedHashMap<>();
            flatten(yaml, "").forEach((key, value) -> values.put(key, (String) value));
            return Map.copyOf(values);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read bundled language: " + locale, exception);
        }
    }

    private static Map<String, Object> flatten(ConfigurationSection section, String prefix) {
        Map<String, Object> values = new LinkedHashMap<>();
        section.getValues(false)
                .forEach(
                        (key, value) -> {
                            if (value instanceof ConfigurationSection child)
                                values.putAll(flatten(child, prefix + key + "."));
                            else values.put(prefix + key, value);
                        });
        return values;
    }

    private static YamlConfiguration yaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().pathSeparator('\u001f');
        return yaml;
    }

    private Language() {}
}
