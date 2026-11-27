package GoldenEggOVO.TDCasino.game.mines;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class MinesCellMotionTest {
    private static Transformation cellPose(boolean refined, double pitch) throws Exception {
        // Only world/manager construction is skipped; the production pose method runs directly.
        var machine = mock(MinesMachine.class);
        Field mode = MinesMachine.class.getDeclaredField("refined");
        mode.setAccessible(true);
        mode.setBoolean(machine, refined);
        Method pose = MinesMachine.class.getDeclaredMethod("cellPose", double.class);
        pose.setAccessible(true);
        return (Transformation) pose.invoke(machine, pitch);
    }

    @Test
    void refinedCellNeverDropsBelowItsRestingBottomAtTickBoundaries() throws Exception {
        for (int frame = 0; frame <= 8; frame++) {
            double pitch = MinesMachine.flipPitch(frame / 8.0, false);
            var pose = cellPose(true, pitch);
            var matrix = new Matrix4f().translate(pose.getTranslation()).rotate(pose.getLeftRotation())
                    .scale(pose.getScale()).rotate(pose.getRightRotation());
            double bottom = Double.POSITIVE_INFINITY;
            for (int corner = 0; corner < 8; corner++) {
                var point = matrix.transformPosition(new Vector3f(
                        (corner & 1) == 0 ? -.5f : .5f,
                        (corner & 2) == 0 ? -.5f : .5f,
                        (corner & 4) == 0 ? -.5f : .5f));
                bottom = Math.min(bottom, 1.115 + point.y);
            }
            assertTrue(bottom >= 1.05 - 1e-6, "Flip frame " + frame + " enters the table");
            assertEquals(0, pose.getTranslation().x, 1e-6);
            assertEquals(0, pose.getTranslation().z, 1e-6);
        }
        assertEquals(0, cellPose(true, 0).getTranslation().length(), 1e-6);
        assertEquals(0, cellPose(true, Math.sin(Math.PI) * 1.3).getTranslation().length(), 1e-6);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void refinedCellClearsTheTableBetweenAllSlerpedTickPoses(boolean bomb) throws Exception {
        float scale = (float) (2.0 / 3.5);
        var nodes = new Transformation[9];
        for (int frame = 0; frame <= 8; frame++)
            nodes[frame] = cellPose(true, MinesMachine.flipPitch(frame / 8.0, bomb));
        for (int segment = 0; segment < 8; segment++) {
            var first = nodes[segment]; var last = nodes[segment + 1];
            for (int step = 0; step <= 41; step++) {
                float t = step / 41f;
                var translation = new Vector3f(first.getTranslation()).mul(scale)
                        .lerp(new Vector3f(last.getTranslation()).mul(scale), t);
                var matrix = new Matrix4f().translate(translation)
                        .rotate(new Quaternionf(first.getLeftRotation()).slerp(last.getLeftRotation(), t))
                        .scale(new Vector3f(first.getScale()).mul(scale)
                                .lerp(new Vector3f(last.getScale()).mul(scale), t))
                        .rotate(new Quaternionf(first.getRightRotation()).slerp(last.getRightRotation(), t))
                        .rotateY((float) Math.PI);
                for (int corner = 0; corner < 8; corner++) {
                    var point = matrix.transformPosition(new Vector3f(
                            (corner & 1) == 0 ? -.5f : .5f,
                            (corner & 2) == 0 ? -.5f : .5f,
                            (corner & 4) == 0 ? -.5f : .5f));
                    assertTrue(1.115 * (2.0 / 3.5) + point.y >= .6 - 1e-6,
                            "Slerped frame " + segment + ":" + step + " enters the table");
                }
            }
        }
    }

    @Test
    void legacyCubeRetainsItsExistingPoseAndOrigin() throws Exception {
        for (double pitch : new double[] {0, .65, 1.3}) {
            var pose = cellPose(false, pitch);
            assertEquals(0, pose.getTranslation().length(), 1e-6);
            assertEquals(new Vector3f(.32f), pose.getScale());
        }
    }
}
