package GoldenEggOVO.TDCasino.audio;

import GoldenEggOVO.TDCasino.machine.MachineResultState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import GoldenEggOVO.TDCasino.TDCasinoPlugin;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
public final class MachineSounds {
    private record Note(int tick, Sound sound, float volume, float pitch) {}
    private final List<Note> notes = new ArrayList<>();
    private record PlayingResult(Object owner, String resource, long expires) {}
    private static final java.util.Map<Player, PlayingResult> RESULT_CHANNEL = new java.util.WeakHashMap<>();
    private final java.util.Set<Player> resultListeners = new java.util.HashSet<>();
    private final Object resultOwner = new Object();
    private final Location origin;
    private final TDCasinoPlugin plugin;

    public MachineSounds(TDCasinoPlugin plugin, Location origin) {
        this.plugin = plugin;
        this.origin = origin;
    }

    private void forListeners(BiConsumer<Player, Boolean> play) {
        if (!plugin.isEnabled()) return;
        for (var listener : plugin.playerViews().nearby(origin, 9)) {
            var player = listener.player();
            plugin.scheduler().execute(player, () -> play.accept(player, listener.packed()));
        }
    }

    public void play(Sound sound, float volume, float pitch) {
        String resource = physicalSound(sound);
        forListeners((player, packed) -> {
            if (packed && resource != null)
                player.playSound(origin, "3dcasino:" + resource, SoundCategory.BLOCKS, volume, 1f);
            else player.playSound(origin, sound, SoundCategory.BLOCKS, volume, pitch);
        });
    }

    private static String physicalSound(Sound sound) {
        if (sound == Sound.ITEM_BOOK_PAGE_TURN) return "cards.deal";
        if (sound == Sound.BLOCK_STONE_BUTTON_CLICK_ON) return "machine.button";
        if (sound == Sound.BLOCK_WOODEN_BUTTON_CLICK_ON) return "ball.land";
        if (sound == Sound.BLOCK_GLASS_HIT) return "ball.contact";
        if (sound == Sound.BLOCK_WOOD_HIT) return "wheel.tick";
        if (sound == Sound.BLOCK_PISTON_CONTRACT) return "machine.latch";
        if (sound == Sound.BLOCK_LEVER_CLICK) return "wheel.start";
        return null;
    }

    public void start() {
        clear();
        playResult("result.start", Sound.BLOCK_NOTE_BLOCK_BIT, .25f, .9f);
    }
    public void result(MachineResultState.Result result, int tick) {
        clear();
        switch (result.outcome()) {
            case LOSS -> playResult("result.loss", Sound.BLOCK_NOTE_BLOCK_BASS, .3f, .7f);
            case EVEN -> playResult("result.even", Sound.BLOCK_NOTE_BLOCK_PLING, .22f, 1f);
            case WIN, BIG_WIN -> {
                boolean big = result.outcome() == MachineResultState.Outcome.BIG_WIN;
                long current = generation;
                forListeners((player, packed) -> {
                    if (packed && current == generation)
                        playPackedResult(player, "3dcasino:result.win", big ? .35f : .25f);
                });
                float[] pitches = big ? new float[]{1f, 1.26f, 1.5f, 2f} : new float[]{1f, 1.26f, 1.5f};
                for (int i = 0; i < pitches.length; i++)
                    notes.add(new Note(tick + i * 3, Sound.BLOCK_NOTE_BLOCK_CHIME, big ? .35f : .25f, pitches[i]));
            }
        }
    }
    public void tick(int tick) {
        long now = System.nanoTime();
        resultListeners.removeIf(player -> {
            var active = RESULT_CHANNEL.get(player);
            if (active == null || active.owner() != resultOwner) return true;
            if (now < active.expires()) return false;
            RESULT_CHANNEL.remove(player);
            return true;
        });
        for (var iterator = notes.iterator(); iterator.hasNext();) {
            var note = iterator.next();
            if (note.tick <= tick) {
                long current = generation;
                forListeners((player, packed) -> {
                    if (!packed && current == generation)
                        player.playSound(origin, note.sound, SoundCategory.BLOCKS, note.volume, note.pitch);
                });
                iterator.remove();
            }
        }
    }
    private long generation;

    public void clear() {
        generation++;
        notes.clear();
        resultListeners.forEach(player -> {
            var active = RESULT_CHANNEL.get(player);
            if (active != null && active.owner() == resultOwner) {
                RESULT_CHANNEL.remove(player);
                if (plugin.isEnabled() && System.nanoTime() < active.expires()) plugin.scheduler().execute(player, () -> {
                    if (!RESULT_CHANNEL.containsKey(player)) player.stopSound(active.resource(), SoundCategory.BLOCKS);
                });
            }
        });
        resultListeners.clear();
    }

    public void resultSingle(MachineResultState.Result result, Sound sound, float volume, float pitch) {
        clear();
        String resource = switch (result.outcome()) {
            case LOSS -> "result.loss";
            case EVEN -> "result.even";
            case WIN, BIG_WIN -> "result.win";
        };
        playResult(resource, sound, volume, pitch);
    }

    private void playResult(String resource, Sound sound, float volume, float pitch) {
        long current = generation;
        forListeners((player, packed) -> {
            if (current != generation) return;
            if (packed) playPackedResult(player, "3dcasino:" + resource, volume);
            else player.playSound(origin, sound, SoundCategory.BLOCKS, volume, pitch);
        });
    }

    private void playPackedResult(Player player, String resource, float volume) {
        var previous = RESULT_CHANNEL.get(player);
        if (previous != null && System.nanoTime() < previous.expires())
            player.stopSound(previous.resource(), SoundCategory.BLOCKS);
        player.playSound(origin, resource, SoundCategory.BLOCKS, volume, 1f);
        RESULT_CHANNEL.put(player, new PlayingResult(resultOwner, resource, System.nanoTime() + 1_500_000_000L));
        resultListeners.add(player);
    }
}
