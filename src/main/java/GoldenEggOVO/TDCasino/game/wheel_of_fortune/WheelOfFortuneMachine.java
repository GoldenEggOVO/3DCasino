package GoldenEggOVO.TDCasino.game.wheel_of_fortune;

import GoldenEggOVO.TDCasino.text.CasinoMessages;
import GoldenEggOVO.TDCasino.machine.*;
import GoldenEggOVO.TDCasino.machine.definition.MachineDefinition;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.entity.*;

import java.security.SecureRandom;
import java.util.*;
public final class WheelOfFortuneMachine extends AnimatedMachine<WheelOfFortuneRound> {

    private WheelDisplay wheel;

    public WheelOfFortuneMachine(
            MachineManager manager, UUID owner, Location origin, MachineDefinition definition) {
        super(manager, owner, origin, definition, () -> new WheelOfFortuneRound(new SecureRandom()));
    }

    @Override
    protected void buildGame() {
        body("wheel_of_fortune_machine");
        button("play", "casino_button_spin_round");
        var disc = model("wheel_of_fortune_disc", 0, 2, .25, 4);
        var pointer = model("wheel_of_fortune_pointer", 0, 2.93, .36, 4);
        var labels = new ArrayList<TextDisplay>();
        for (double value :
                WheelOfFortuneRound.multipliers()) {
            var label = text(0, 2, .321, .25);
            translatedLabel(label, () -> value < 0 ? CasinoMessages.component("fortune.again-label")
                    : Component.text(String.format(Locale.ROOT, "%sX", java.math.BigDecimal.valueOf(value)
                            .stripTrailingZeros().toPlainString())));
            labels.add(label);
        }
        wheel = new WheelDisplay(this, disc, pointer, labels, .64, .25f);
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
        wheel.refresh(round.finished(), round.segment());
    }

    @Override
    protected void animateFrame(double progress, double ease) {
        wheel.animate(progress);
    }

    @Override
    protected void animationPlanned() {
        wheel.planSpin(round.segment());
        sound(Sound.BLOCK_LEVER_CLICK, .16f, 1.1f);
    }

    @Override
    protected boolean repeatAnimation() {
        if (round.active()) {
            wheel.finishSpin();
            sound(Sound.BLOCK_NOTE_BLOCK_BIT, .3f, 1.7f);
            round.replayFortune();
            return true;
        }
        return false;
    }
}
