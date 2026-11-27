package GoldenEggOVO.TDCasino.machine;

import GoldenEggOVO.TDCasino.Core.render.model.ModelRepository;

import GoldenEggOVO.TDCasino.game.plinko.PlinkoPath;
import GoldenEggOVO.TDCasino.render.MachineGeometry;
import GoldenEggOVO.TDCasino.rules.CasinoRules;

import static org.junit.jupiter.api.Assertions.*;

import GoldenEggOVO.TDCasino.machine.definition.MachineDefinition;
import GoldenEggOVO.TDCasino.game.plinko.PlinkoMachine;

import org.junit.jupiter.api.Test;

class OriginalMachineLayoutTest {
    @Test
    void blackjackTableIsJustAboveControlsAndStillMeetsTheFloor() throws Exception {
        var bounds = ModelRepository.get("casino/" + "blackjack_machine").bounds();
        double bottom = bounds.getMinY() * 4, top = bounds.getMaxY() * 4;
        assertEquals(0, bottom, 1e-9);
        assertEquals(.92, top, 1e-6);
        var button = MachineDefinition.builtin("blackjack").button("start");
        var buttonTop = button.transform().apply(0, .4 * button.size(), .18 * button.size());
        assertTrue(top > buttonTop.y());
        assertTrue(top - buttonTop.y() < .1);
    }

    @Test
    void plinkoConfiguredButtonRestoresAndStaysBelowMultipliers() {
        var button = MachineDefinition.builtin("plinko").button("play");
        var rest = MachineGeometry.buttonPose(button, .75, 0);
        var pressed = MachineGeometry.buttonPose(button, .75, button.press());
        assertEquals(.9, rest.getScale().x * .6 / 4, 1e-6);
        assertEquals(.65, rest.getScale().y / 4, 1e-6);
        assertEquals(rest.getScale(), pressed.getScale());
        assertEquals(rest.getLeftRotation(), pressed.getLeftRotation());
        assertEquals(0, rest.getTranslation().length(), 1e-9);
        assertTrue(pressed.getTranslation().y < 0);
        var top = button.transform().apply(0, .4 * button.size(), .18 * button.size());
        assertTrue(top.y() * .75 < .76 * .75);
        assertEquals(.055, pressed.getTranslation().length(), 1e-6);
    }

    @Test
    void blackjackButtonsRemainSeparatedAndClearOfTheTableWithoutTheirPanel() {
        var definition = MachineDefinition.builtin("blackjack");
        double right = Double.NEGATIVE_INFINITY;
        for (String action : new String[] {"double", "stand", "hit", "start"}) {
            var button = definition.button(action);
            double halfWidth = .3 * button.size();
            assertTrue(button.transform().x() - halfWidth > right + .02);
            right = button.transform().x() + halfWidth;
            assertTrue(Math.abs(button.transform().x()) + halfWidth < 1);
            for (double y : new double[] {0, .4})
                for (double z : new double[] {-.05, .13}) {
                    var point = button.transform().apply(0, y * button.size(), z * button.size() - button.press());
                    assertTrue(point.z() > 1.15);
                    assertTrue(point.y() > .48 && point.y() < .9);
                }
        }
    }

    @Test
    void plinkoLabelsMatchThePayoutOfTheirOwnLandingSlots() {
        for (int slot = 0; slot < 13; slot++) {
            String label = PlinkoMachine.multiplierLabel(slot);
            assertTrue(label.endsWith("X"));
            double shown = Double.parseDouble(label.substring(0, label.length() - 1));
            double payout = CasinoRules.plinkoPayout(1_000_000, slot) / 1_000_000.0;
            assertEquals(payout, shown, .0051);
            assertEquals(label, PlinkoMachine.multiplierLabel(12 - slot));
            var landing = PlinkoPath.at((1 << slot) - 1, 12);
            assertEquals(landing.x(), PlinkoMachine.slotLabelX(slot), 1e-9);
        }
        assertTrue(Double.parseDouble(PlinkoMachine.multiplierLabel(0).replace("X", "")) > 1);
        assertTrue(Double.parseDouble(PlinkoMachine.multiplierLabel(6).replace("X", "")) < 1);
    }
}
