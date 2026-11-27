package GoldenEggOVO.TDCasino.machine;

import com.google.gson.Gson;
import GoldenEggOVO.TDCasino.game.GameRound;
import GoldenEggOVO.TDCasino.game.StartedRound;
import GoldenEggOVO.TDCasino.game.keno.KenoRound;
import GoldenEggOVO.TDCasino.game.hilo.HiloRound;
import GoldenEggOVO.TDCasino.game.mines.MinesRound;
import GoldenEggOVO.TDCasino.game.duck_race.DuckRaceRound;
import GoldenEggOVO.TDCasino.game.money_wheel.MoneyWheelRound;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
final class PlayerMachineProfileStore {
    record Profile(long stake, long totalNet, MachineResultState.Result last, Map<String, Integer> options, List<Integer> selected) {
        Profile {
            options = Map.copyOf(options);
            selected = List.copyOf(selected);
            if (stake < 0 || stake > GameRound.MAX_STAKE
                    || selected.size() > 10 || selected.stream().anyMatch(n -> n < 1 || n > 40)
                    || new HashSet<>(selected).size() != selected.size()
                    || last != null && (last.stake() < 0 || last.returned() < 0))
                throw new IllegalArgumentException("Invalid personal data");
        }
        static Profile capture(GameRound round, MachineResultState resultState) {
            Map<String, Integer> options = new HashMap<>();
            List<Integer> selected = List.of();
            if (round instanceof KenoRound keno) selected = keno.selected().stream().sorted().toList();
            if (round instanceof HiloRound hilo) {
                options.put("threshold", hilo.threshold()); options.put("high", hilo.high() ? 1 : 0);
            }
            if (round instanceof MinesRound mines) options.put("mines", mines.mineCount());
            if (round instanceof DuckRaceRound duck) options.put("choice", duck.choice());
            if (round instanceof MoneyWheelRound wheel) options.put("choice", wheel.choice());
            long stake = round instanceof StartedRound started ? started.configuredStake() : round.stake();
            return new Profile(stake, resultState.accountedNet(), resultState.accountedLast(), options, selected);
        }
        void restore(GameRound round, MachineResultState resultState) {
            round.setStake(stake);
            if (round instanceof KenoRound keno) for (int number : selected) keno.action("select:" + number);
            if (round instanceof HiloRound hilo) {
                hilo.setThreshold(options.getOrDefault("threshold", 50));
                hilo.action(options.getOrDefault("high", 1) == 1 ? "over" : "under");
            }
            if (round instanceof MinesRound mines)
                mines.changeMines(options.getOrDefault("mines", 3) - mines.mineCount());
            if (round instanceof DuckRaceRound duck) duck.action("select:" + options.getOrDefault("choice", 0));
            if (round instanceof MoneyWheelRound wheel) wheel.action("select:" + options.getOrDefault("choice", 0));
            resultState.restore(totalNet, last);
        }
    }
    private record Document(int schema, Map<String, Profile> machines) {}
    private final Path folder;
    private final Gson gson = new Gson();
    private final Map<UUID, Map<String, Profile>> cached = new HashMap<>();
    private final Set<UUID> unreadable = new HashSet<>();

    PlayerMachineProfileStore(Path folder) { this.folder = folder; }
    private Map<String, Profile> loadPlayerProfiles(UUID id) throws IOException {
        if (unreadable.contains(id)) throw new IOException("Personal data file is unreadable: " + id);
        if (cached.containsKey(id)) return cached.get(id);
        Path file = folder.resolve(id + ".json");
        Map<String, Profile> profiles = new HashMap<>();
        if (Files.exists(file)) {
            try {
                var document = gson.fromJson(Files.readString(file), Document.class);
                if (document == null || document.schema != 1 || document.machines == null
                        || document.machines.values().stream().anyMatch(Objects::isNull))
                    throw new IllegalArgumentException("Invalid personal data document");
                profiles.putAll(document.machines);
            } catch (IOException | RuntimeException ex) {
                unreadable.add(id);
                throw new IOException("Cannot read personal data: " + file, ex);
            }
        }
        cached.put(id, profiles);
        return profiles;
    }
    Profile load(UUID player, UUID owner, String game) throws IOException {
        return loadPlayerProfiles(player).get(owner + ":" + game);
    }
    void save(UUID player, UUID owner, String game, Profile profile) throws IOException {
        var profiles = loadPlayerProfiles(player);
        String key = owner + ":" + game;
        if (profile.equals(profiles.get(key))) return;
        var candidate = new HashMap<>(profiles);
        candidate.put(key, profile);
        Files.createDirectories(folder);
        Path file = folder.resolve(player + ".json"), temporary = folder.resolve(player + ".json.tmp");
        try (var channel = java.nio.channels.FileChannel.open(temporary, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            var bytes = java.nio.ByteBuffer.wrap(gson.toJson(new Document(1, candidate)).getBytes(StandardCharsets.UTF_8));
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        }
        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        profiles.put(key, profile);
    }
}
