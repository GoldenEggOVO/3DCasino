package GoldenEggOVO.TDCasino.render.model;

import GoldenEggOVO.TDCasino.Core.render.model.ModelRepository;
import GoldenEggOVO.TDCasino.Core.render.model.ModelDefinition;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.List;
import org.junit.jupiter.api.Test;

class CasinoMachineThemeTest {
    private static final List<String> BODIES = List.of(
            "plinko_machine", "mines_machine", "mines_machine_sloped", "blackjack_machine",
            "crash_machine", "slots_machine", "duck_race_machine", "wheel_of_fortune_machine",
            "money_wheel_machine", "penguin_cross_machine", "keno_machine", "hilo_machine",
            "dragon_tower_machine", "dragon_tower_machine_compact");

    @Test
    void allTwelveMachinesAndTheirAlternateBodiesRenderWhiteArmorAndCyanLights() {
        assertAll(BODIES.stream().map(name -> () -> {
            var boxes = ModelRepository.get("casino/" + name).blocks();
            assertTrue(boxes.stream().anyMatch(box -> java.util.Set.of("WHITE_CONCRETE", "SMOOTH_QUARTZ")
                    .contains(box.material().name())), name + " needs visible white armor");
            assertTrue(boxes.stream().anyMatch(box -> box.material().name().equals("BLACK_CONCRETE")),
                    name + " needs a recessed dark chassis");
            assertTrue(boxes.stream().anyMatch(box -> box.material().name().equals("LIGHT_BLUE_CONCRETE")
                    && Integer.valueOf(15).equals(box.brightness())), name + " needs independently lit accents");
            assertTrue(boxes.stream().noneMatch(box -> java.util.Set.of("DARK_OAK_PLANKS", "WAXED_EXPOSED_COPPER")
                    .contains(box.material().name())), name + " must not retain wooden or bronze cabinet parts");
            assertTrue(boxes.stream().filter(box -> box.brightness() != null && box.brightness() > 6)
                    .allMatch(box -> box.material().name().equals("LIGHT_BLUE_CONCRETE")
                            && (box.name().contains("light") || box.name().equals("front_kick"))),
                    name + " must reserve full brightness for accent strips");
        }));
    }

    @Test
    void sharedButtonLabelsKeepTheirPackedFaceContrast() {
        assertAll(ModelRepository.ids("casino").stream().filter(name -> name.contains("button"))
                .map(name -> () -> ModelRepository.require("casino/" + name).labels()
                        .forEach(label -> assertEquals(0x493323, label.color(),
                                name + " labels are shared with the unchanged packed button face"))));
    }

    @Test
    void cabinetFrontsAndRotatingWheelFacesHaveNoVisibleCoplanarOverlaps() {
        var names = new java.util.ArrayList<>(BODIES);
        names.addAll(List.of("wheel_of_fortune_disc", "money_wheel_disc"));
        assertAll(names.stream().map(name -> () -> {
            var boxes = ModelRepository.get("casino/" + name).blocks();
            for (int first = 0; first < boxes.size(); first++) {
                var a = boxes.get(first);
                if (a.rotation().x() != 0) continue;
                for (int second = first + 1; second < boxes.size(); second++) {
                    var b = boxes.get(second);
                    if (b.rotation().x() != 0 || Math.abs((a.position().z() + a.size().z()/2) - (b.position().z() + b.size().z()/2)) > 6.25e-9)
                        continue;
                    var overlap = front(a);
                    overlap.intersect(front(b));
                    if (area(overlap) < 6.25e-9) continue;
                    for (int other = 0; other < boxes.size(); other++) {
                        var cover = boxes.get(other);
                        if (other != first && other != second && cover.rotation().x() == 0
                                && (cover.position().z() + cover.size().z()/2) > (a.position().z() + a.size().z()/2) + 6.25e-9)
                            overlap.subtract(front(cover));
                    }
                    assertTrue(area(overlap) < 6.25e-9,
                            name + " front faces overlap: boxes " + first + " and " + second);
                }
            }
        }));
    }

    @Test
    void functionalWheelSegmentsKeepTheirPayoutColors() {
        var fortune = List.of("GRAY_CONCRETE", "YELLOW_CONCRETE", "RED_CONCRETE", "PURPLE_CONCRETE",
                "ORANGE_CONCRETE", "LIME_CONCRETE", "RED_CONCRETE", "ORANGE_CONCRETE", "BLUE_CONCRETE",
                "RED_CONCRETE", "GRAY_CONCRETE", "YELLOW_CONCRETE", "PURPLE_CONCRETE", "RED_CONCRETE",
                "ORANGE_CONCRETE", "BLACK_CONCRETE", "PURPLE_CONCRETE", "RED_CONCRETE", "BLUE_CONCRETE",
                "ORANGE_CONCRETE");
        var money = List.of("LIME_CONCRETE", "BLUE_CONCRETE", "LIME_CONCRETE", "RED_CONCRETE",
                "LIME_CONCRETE", "BLUE_CONCRETE", "LIME_CONCRETE", "YELLOW_CONCRETE", "LIME_CONCRETE",
                "BLUE_CONCRETE", "LIME_CONCRETE", "RED_CONCRETE", "LIME_CONCRETE", "BLUE_CONCRETE",
                "LIME_CONCRETE", "BLUE_CONCRETE", "LIME_CONCRETE", "RED_CONCRETE", "LIME_CONCRETE",
                "BLUE_CONCRETE");
        for (var entry : java.util.Map.of("wheel_of_fortune_disc", fortune,
                "money_wheel_disc", money).entrySet()) {
            var boxes = ModelRepository.get("casino/" + entry.getKey()).blocks();
            double radius = (entry.getKey().equals("wheel_of_fortune_disc") ? .64 : .78) / 4;
            for (int segment = 0; segment < 20; segment++) {
                double angle = (segment + .5) * 2 * Math.PI / 20;
                double x = radius * Math.sin(angle), y = radius * Math.cos(angle);
                var visible = boxes.stream().filter(box -> front(box).contains(x, y))
                        .max(java.util.Comparator.comparingDouble(box -> (box.position().z() + box.size().z()/2))).orElseThrow();
                assertEquals(entry.getValue().get(segment), visible.material().name(),
                        entry.getKey() + " payout segment " + segment);
            }
        }
    }

    private static Area front(ModelDefinition.Block box) {
        var path = new Path2D.Double();
        double cx = box.position().x(), cy = box.position().y();
        double angle = Math.toRadians(box.rotation().z());
        double cosine = Math.cos(angle), sine = Math.sin(angle);
        int[][] corners = {{0,0},{1,0},{1,1},{0,1}};
        for (int i = 0; i < corners.length; i++) {
            double x = (corners[i][0] == 0 ? -.5 : .5) * box.size().x();
            double y = (corners[i][1] == 0 ? -.5 : .5) * box.size().y();
            double u = cx + x * cosine - y * sine, v = cy + x * sine + y * cosine;
            if (i == 0) path.moveTo(u, v); else path.lineTo(u, v);
        }
        path.closePath();
        return new Area(path);
    }

    private static double area(Area shape) {
        var iterator = shape.getPathIterator(null, 6.25e-9);
        double[] point = new double[6];
        double result = 0, x = 0, y = 0, startX = 0, startY = 0;
        while (!iterator.isDone()) {
            int kind = iterator.currentSegment(point);
            if (kind == PathIterator.SEG_MOVETO) {
                startX = x = point[0];
                startY = y = point[1];
            } else if (kind == PathIterator.SEG_LINETO) {
                result += x * point[1] - point[0] * y;
                x = point[0];
                y = point[1];
            } else if (kind == PathIterator.SEG_CLOSE) result += x * startY - startX * y;
            iterator.next();
        }
        return Math.abs(result) / 2;
    }
}
