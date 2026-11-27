package GoldenEggOVO.TDCasino.game.money_wheel;

import GoldenEggOVO.TDCasino.Core.platform.PlatformMessages;
import GoldenEggOVO.TDCasino.machine.*;
import GoldenEggOVO.TDCasino.machine.definition.MachineDefinition;

import org.bukkit.*;
import org.bukkit.entity.*;

import java.security.SecureRandom;
import java.util.*;
public final class MoneyWheelMachine extends AnimatedMachine<MoneyWheelRound> {

    private WheelDisplay wheel;

    public MoneyWheelMachine(
            MachineManager manager, UUID owner, Location origin, MachineDefinition definition) {
        super(manager, owner, origin, definition, () -> new MoneyWheelRound(new SecureRandom()));
    }

    @Override
    protected void buildGame() {
        body("money_wheel_machine");
        button("play", "casino_button_spin_round");
        int[] segments = MoneyWheelRound.segments(), multipliers = MoneyWheelRound.multipliers();
        for (int i = 0; i < multipliers.length; i++)
            button("select:" + i, "money_wheel_button_" + multipliers[i] + "x");
        var disc = model("money_wheel_disc", 0, 2, .25, 4);
        var pointer = model("money_wheel_pointer", 0, 2.93, .36, 4);
        var labels = new ArrayList<TextDisplay>();
        for (int segment : segments) {
            var label = text(0, 2, .321, .16);
            PlatformMessages.text(label, net.kyori.adventure.text.Component.text(multipliers[segment] + "X"));
            labels.add(label);
        }
        wheel = new WheelDisplay(this, disc, pointer, labels, .78, .16f);
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
        selectedButtons(a -> a.equals("select:" + round.choice()));
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
}
