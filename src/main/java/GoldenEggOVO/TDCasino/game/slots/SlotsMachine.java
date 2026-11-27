package GoldenEggOVO.TDCasino.game.slots;

import GoldenEggOVO.TDCasino.render.MachineGeometry;
import GoldenEggOVO.TDCasino.Core.render.DisplayMovement;
import GoldenEggOVO.TDCasino.machine.*;
import GoldenEggOVO.TDCasino.machine.definition.MachineDefinition;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;

import java.security.SecureRandom;
import java.util.*;
public final class SlotsMachine extends AnimatedMachine<SlotsRound> {
    private static final Material[] SYMBOLS = {
        Material.APPLE,
        Material.GOLD_INGOT,
        Material.EMERALD,
        Material.DIAMOND,
        Material.NETHER_STAR
    };
    final List<ItemDisplay> figures = new ArrayList<>();
    private final int[] visibleSymbols = new int[9];
    private final int[] wraps = new int[9];
    private int stoppedReels;

    public SlotsMachine(
            MachineManager manager, UUID owner, Location origin, MachineDefinition definition) {
        super(manager, owner, origin, definition, () -> new SlotsRound(new SecureRandom()));
    }

    @Override
    protected void buildGame() {
        body("slots_machine");
        button("play", "casino_button_spin");
        for (int i = 0; i < 9; i++) {
            visibleSymbols[i] = i % SYMBOLS.length;
            figures.add(
                    item(
                            new ItemStack(SYMBOLS[visibleSymbols[i]]),
                            (i % 3 - 1) * .5,
                            2.36 - (i / 3) * .48,
                            .46,
                            .32,
                            0));
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
        var symbols = round.symbols();
        for (int i = 0; i < 9 && i < symbols.size(); i++) {
            showSymbol(i, symbols.get(i));
            DisplayMovement.move(figures.get(i), at((i % 3 - 1) * .5, 2.36 - (i / 3) * .48, .46));
            pose(figures.get(i), MachineGeometry.itemPose(.32, 0, 0));
            figures.get(i).setInterpolationDelay(0);
        }
    }

    private void showSymbol(int index, int symbol) {
        if (visibleSymbols[index] == symbol) return;
        visibleSymbols[index] = symbol;
        figures.get(index).setItemStack(new ItemStack(SYMBOLS[symbol]));
    }

    @Override
    protected void animateFrame(double progress, double ease) {
        int stopped = progress >= .85 ? 3 : progress >= .70 ? 2 : progress >= .55 ? 1 : 0;
        if (stopped > stoppedReels) sound(Sound.BLOCK_PISTON_CONTRACT, .2f, .9f + stopped * .15f);
        if (stopped < 3 && age % 4 == 0) sound(Sound.BLOCK_NOTE_BLOCK_HAT, .10f, 1.5f);
        var symbols = round.symbols();
        for (int i = 0; i < 9; i++) {
            int column = i % 3;
            if (column < stoppedReels) continue;
            int turns = 2 + column;
            double t = column < stopped ? 1 : Math.min(1, progress / (.55 + .15 * column));
            double position = i / 3 + 3 * turns * t * t * (3 - 2 * t);
            int lap = (int) Math.floor((position + .5) / 3);
            double row = position - 3 * lap;
            boolean wrapped = lap != wraps[i];
            var figure = figures.get(i);
            int duration = wrapped ? 0 : 1;
            if (figure.getTeleportDuration() != duration) figure.setTeleportDuration(duration);
            if (wrapped) {
                showSymbol(i, lap == turns ? symbols.get(i) : (i + 2 * lap) % SYMBOLS.length);
                wraps[i] = lap;
            }
            double edge = Math.max(0, Math.abs(row - 1) - 1);
            double screenRow = row - Math.copySign(edge * edge, row - 1);
            DisplayMovement.move(figure, at((column - 1) * .5, 2.36 - screenRow * .48, .46));
            var motion = MachineGeometry.itemPose(.32, 0, 0);
            motion.getScale().y *= (float) (1 - 2 * edge);
            pose(figure, motion);
            figure.setInterpolationDelay(0);
        }
        stoppedReels = stopped;
    }

    @Override
    protected void beforeAction(String action) {
        if (action.equals("play")) {
            stoppedReels = 0;
            Arrays.fill(wraps, 0);
        }
    }
}
