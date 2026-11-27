package GoldenEggOVO.TDCasino.game.keno;

import GoldenEggOVO.TDCasino.Core.platform.PlatformMessages;
import GoldenEggOVO.TDCasino.Core.render.DisplayMovement;
import GoldenEggOVO.TDCasino.render.MachineGeometry;
import GoldenEggOVO.TDCasino.machine.*;
import GoldenEggOVO.TDCasino.machine.definition.MachineDefinition;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.security.SecureRandom;
import java.util.*;
public final class KenoMachine extends AnimatedMachine<KenoRound> {

    final List<ItemDisplay> tiles = new ArrayList<>(), gems = new ArrayList<>();
    private final List<TextDisplay> numbers = new ArrayList<>();
    private int highlightedNumber = -1;
    private boolean highlightedHit;
    int kenoRevealed = -1;

    public KenoMachine(
            MachineManager manager, UUID owner, Location origin, MachineDefinition definition) {
        super(manager, owner, origin, definition, () -> new KenoRound(new SecureRandom()));
    }

    @Override
    protected void buildGame() {
        body("keno_machine");
        button("play", "casino_button_play");
        for (int i = 1; i <= 40; i++) {
            double x = -1.15 + (i - 1) % 8 * (2.3 / 7), z = -.72 + (i - 1) / 8 * .335;
            var tile = item(model("keno_tile"), x, .85, z,
                    4, -Math.PI / 2);
            tiles.add(tile);
            hitDisplay("select:" + i, tile, new BoundingBox(-.03, -.03, -.00875, .03, .03, .00875), -1);
            var label = text(x, .889, z + .035, .32);
            label.setInterpolationDuration(1);
            numbers.add(label);
            PlatformMessages.text(label, net.kyori.adventure.text.Component.text(Integer.toString(i)));
            numberPose(i - 1, 0);
        }
        for (int i = 0; i < 10; i++) {
            var gem = item(new ItemStack(Material.EMERALD), 0, .93, 0, 0, 0);
            gem.setTeleportDuration(0);
            gem.setInterpolationDuration(0);
            gems.add(gem);
        }
    }

    @Override
    protected boolean available(String action) {
        return round.available(action);
    }

    @Override
    protected void perform(String action) {
        round.action(action);
    }

    @Override
    protected void refresh() {
        clearNumberPulse();
        kenoRevealed = -1;
        revealKeno(round.finished() ? 10 : 0);
    }

    @Override
    protected void animateFrame(double progress, double ease) {
        double draw = progress * 10;
        int count = Math.min(10, (int) draw + 1);
        if (count != kenoRevealed || highlightedNumber < 0) {
            revealKeno(count);
            clearNumberPulse();
            highlightedNumber = round.drawnNumbers().get(count - 1) - 1;
            highlightedHit = round.selected().contains(highlightedNumber + 1);
        }
        double pulse = Math.sin(Math.PI * (draw - count + 1));
        numberPose(highlightedNumber, pulse * pulse * (highlightedHit ? 1 : .65));
    }

    private void numberPose(int index, double pulse) {
        var label = numbers.get(index);
        pose(label, new Transformation(new Vector3f(0, (float) (.035 * pulse), 0),
                new Quaternionf().rotateX((float) -Math.PI / 2),
                new Vector3f((float) (.32 + .13 * pulse)), new Quaternionf()));
        label.setInterpolationDelay(0);
    }

    private void clearNumberPulse() {
        if (highlightedNumber >= 0) numberPose(highlightedNumber, 0);
        highlightedNumber = -1;
    }

    void revealKeno(int count) {
        if (kenoRevealed == count) return;
        if (count > kenoRevealed && busy()) sound(Sound.BLOCK_NOTE_BLOCK_PLING, .16f, 1f + count * .06f);
        kenoRevealed = count;
        var selected = round.selected();
        var drawnNumbers = round.drawnNumbers();
        var revealedNumbers = drawnNumbers.subList(0, Math.min(count, drawnNumbers.size()));
        for (int i = 0; i < tiles.size(); i++) {
            boolean picked = selected.contains(i + 1), drawn = revealedNumbers.contains(i + 1);
            var tile = tiles.get(i);
            tile.setItemStack(model(picked ? "keno_tile_selected" : "keno_tile"));
            tile.setGlowColorOverride(drawn ? Color.AQUA : Color.YELLOW);
            tile.setGlowing(drawn || picked);
        }
        for (int i = 0; i < gems.size(); i++) {
            boolean shown =
                    i < count
                            && i < drawnNumbers.size()
                            && selected.contains(drawnNumbers.get(i));
            if (shown) {
                int index = drawnNumbers.get(i) - 1;
                DisplayMovement.move(gems.get(i), at(-1.15 + index % 8 * (2.3 / 7), 1.07, -.72 + index / 8 * .335));
            }
            pose(gems.get(i), MachineGeometry.itemPose(shown ? .19 : 0, 0, 0));
        }
    }

    @Override
    protected void beforeAction(String action) {
        kenoRevealed = -1;
    }
}
