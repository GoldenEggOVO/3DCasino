package GoldenEggOVO.TDCasino.render;

import GoldenEggOVO.TDCasino.machine.definition.MachineDefinition;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MachinePanelGeometryTest {
    @Test
    void hiloUsesActualPercentageForTheRailBoundary() {
        assertEquals(-.55, MachinePanelGeometry.sliderX(25), 1e-9);
        assertEquals(.55, MachinePanelGeometry.sliderX(75), 1e-9);
    }

    @Test
    void hiloControlsShareThePanelsPlane() {
        var definition = MachineDefinition.builtin("hilo");
        for (String action : new String[] {"under", "over", "play"}) {
            var p = definition.button(action).transform().apply(0, 0, 0);
            var local =
                    MachineGeometry.panelPoint(
                            p.x(), p.y() - 1.1, p.z(), -MachinePanelGeometry.HILO_PITCH);
            assertEquals(.055, local.z(), 1e-6);
            assertEquals(action.equals("play") ? .20 : -.53, local.y(), 1e-6);
        }
    }

    @Test
    void dragonHasAShallowSymmetricArcWithSeparatedCells() {
        for (int row = 0; row < 6; row++)
            for (int col = 0; col < 4; col++) {
                var p = MachinePanelGeometry.dragonCell(row, col);
                var opposite = MachinePanelGeometry.dragonCell(row, 3 - col);
                assertEquals(p.x(), -opposite.x(), 1e-9);
                assertEquals(p.z(), opposite.z(), 1e-9);
                assertEquals(1.65, Math.hypot(p.x(), p.z() - .4 + 1.65), 1e-9);
                if (col < 3)
                    assertTrue(MachinePanelGeometry.dragonCell(row, col + 1).x() - p.x() > .32);
            }
    }

    @Test
    void railRoundTripsEverySelectableValue() {
        for (int n = 1; n <= 98; n++)
            assertEquals(n, MachinePanelGeometry.threshold(MachinePanelGeometry.sliderX(n)));
        assertEquals(1, MachinePanelGeometry.threshold(-100));
        assertEquals(98, MachinePanelGeometry.threshold(100));
    }

    @Test
    void rayUsesTheSlopedSurfaceAndRejectsBehindOrParallel() {
        var target = new MachineGeometry.Point(.55, 1.1, 0);
        var eye = new MachineGeometry.Point(0, 1.7, 3);
        double length = Math.sqrt(.55 * .55 + .6 * .6 + 9);
        assertEquals(
                MachinePanelGeometry.threshold(target.x()),
                MachinePanelGeometry.aimedThreshold(
                        eye, new MachineGeometry.Point(.55 / length, -.6 / length, -3 / length)));
        assertEquals(-1, MachinePanelGeometry.aimedThreshold(eye, new MachineGeometry.Point(0, 0, 1)));
        assertEquals(-1, MachinePanelGeometry.aimedThreshold(eye, new MachineGeometry.Point(1, 0, 0)));
    }
}
