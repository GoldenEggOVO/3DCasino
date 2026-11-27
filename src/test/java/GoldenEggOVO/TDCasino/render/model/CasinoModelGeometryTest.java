package GoldenEggOVO.TDCasino.render.model;

import GoldenEggOVO.TDCasino.machine.definition.MachineDefinition;

import GoldenEggOVO.TDCasino.Core.render.model.ModelRepository;
import GoldenEggOVO.TDCasino.render.MachineGeometry;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CasinoModelGeometryTest {
    @Test void cardAliasesShareMetadataWhileKeepingAllRuntimeIdentifiers() {
        assertEquals(105, ModelRepository.ids("casino").size());
        assertSame(ModelRepository.get("casino/card_0"), ModelRepository.get("casino/card_51"));
        assertNotSame(ModelRepository.get("casino/card_0"), ModelRepository.get("casino/card_52"));
    }

    @Test void compositePoseMatchesPackedCoordinatesIncludingPitchAndNonuniformScale() {
        var pose = MachineGeometry.itemPose(4, -Math.PI / 2, .2);
        var point = MachineGeometry.nativePose(pose).transformPosition(new Vector3f(.25f, .5f, .75f));
        assertEquals(1, point.x, 1e-5);
        assertEquals(3, point.y, 1e-5);
        assertEquals(-1.8, point.z, 1e-5);
        pose.getScale().set(8, 4, 2);
        point = MachineGeometry.nativePose(pose).transformPosition(new Vector3f(.25f, .5f, .75f));
        assertEquals(2, point.x, 1e-5);
        assertEquals(1.5, point.y, 1e-5);
        assertEquals(-1.8, point.z, 1e-5);
    }

    @Test void cardMetadataRetainsItsBoundsInBlockUnits() {
        for (int index = 0; index <= 52; index++) {
            var model = ModelRepository.require("casino/card_" + index);
            assertEquals(index == 52 ? 2 : 1, model.blocks().size());
            var bounds = model.bounds();
            assertEquals(1, bounds.getWidthX(), 1e-6);
            assertEquals(4.0 / 3, bounds.getHeight(), 1e-6);
            assertTrue(bounds.getWidthZ() < .015);
        }
    }

    @Test void shippedBodiesHaveUsablePhysicalBounds() {
        for (String game : MachineDefinition.games()) {
            var model = ModelRepository.require("casino/" + game + "_machine");
            var bounds = model.bounds();
            assertTrue(Double.isFinite(bounds.getMinX()) && Double.isFinite(bounds.getMaxX()), game);
            assertTrue(bounds.getWidthX() > 0 && bounds.getHeight() > 0 && bounds.getWidthZ() > 0, game);
            assertTrue(bounds.getMinY() >= -.0025, game + " must rest on the floor");
        }
    }
}
