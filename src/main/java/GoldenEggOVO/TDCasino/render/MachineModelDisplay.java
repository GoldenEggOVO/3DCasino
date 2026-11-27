package GoldenEggOVO.TDCasino.render;

import GoldenEggOVO.TDCasino.Core.render.model.ModelDefinition;
import GoldenEggOVO.TDCasino.Core.render.model.ModelGeometry;
import GoldenEggOVO.TDCasino.Core.render.model.ModelRepository;
import GoldenEggOVO.TDCasino.render.model.ModelCarrierItems;
import GoldenEggOVO.TDCasino.text.CasinoMessages;
import GoldenEggOVO.TDCasino.Core.text.LabelLayout;
import GoldenEggOVO.TDCasino.Core.platform.PlatformMessages;
import GoldenEggOVO.TDCasino.Core.render.DisplayAudience;
import GoldenEggOVO.TDCasino.Core.render.DisplayMovement;
import GoldenEggOVO.TDCasino.Core.render.DisplayPlacement;
import GoldenEggOVO.TDCasino.Core.render.PlayingCardHeads;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
public final class MachineModelDisplay {
    private record Part(Display display, Matrix4f local, Transformation rigidLocal) {
        Part(Display display, Matrix4f local) { this(display, local, null); }
    }
    private record LabelPart(TextDisplay display, Matrix4f local, ModelDefinition.Label label, int index) {}
    private final ItemDisplay carrier;
    private final Consumer<Entity> register;
    private final List<Entity> ownerParts;
    private final DisplayAudience audience;
    private final Function<String, ItemStack> packedItems;
    private final List<Part> children = new ArrayList<>();
    private final List<LabelPart> labels = new ArrayList<>();
    private long languageRevision = CasinoMessages.revision();
    private String name;
    private Matrix4f previous;
    private final Quaternionf previousLeftRotation = new Quaternionf(), previousRightRotation = new Quaternionf();
    private Quaternionf previousRigidRotation;
    private Location location;
    private boolean glowing;
    private Color glowColor;
    private boolean nativeBuilt, labelsBuilt;
    private ItemDisplay packed;

    public MachineModelDisplay(ItemDisplay carrier, Consumer<Entity> register, List<Entity> ownerParts) {
        this(carrier, register, ownerParts, null, null);
    }

    public MachineModelDisplay(ItemDisplay carrier, Consumer<Entity> register, List<Entity> ownerParts,
                               DisplayAudience audience, Function<String, ItemStack> packedItems) {
        this.carrier = carrier;
        this.register = register;
        this.ownerParts = ownerParts;
        this.audience = audience;
        this.packedItems = packedItems;
        carrier.setVisibleByDefault(false);
        sync();
    }

    public boolean sync() {
        if (!carrier.isValid()) {
            remove();
            return false;
        }
        var pose = carrier.getTransformation();
        var matrix = MachineGeometry.nativePose(pose);
        var nextLocation = carrier.getLocation();
        String nextName = ModelCarrierItems.modelName(carrier.getItemStack());
        boolean nativeNeeded = audience == null || audience.needed(false);
        boolean labelsNeeded = nativeNeeded || audience != null && audience.needed(true);
        boolean nameChanged = !Objects.equals(name, nextName);
        boolean rebuilt = nameChanged || nativeBuilt != nativeNeeded || labelsBuilt != labelsNeeded;
        if (rebuilt) {
            var model = ModelRepository.get("casino/" + nextName);
            if (nativeBuilt == nativeNeeded && labelsBuilt == labelsNeeded
                    && sameBlockGeometry(ModelRepository.get("casino/" + name), model)
                    && children.size() == model.blocks().size()
                    && children.stream().allMatch(part -> part.display instanceof BlockDisplay)) {
                for (int i = 0; i < children.size(); i++)
                    updateBlock((BlockDisplay) children.get(i).display, model.blocks().get(i));
                name = nextName;
            } else {
                removeChildren();
                name = nextName;
                carrier.setVisibleByDefault(model == null);
                if (model != null) build(model, matrix, nativeNeeded, labelsNeeded);
            }
            nativeBuilt = nativeNeeded;
            labelsBuilt = labelsNeeded;
        }
        boolean moved = !nextLocation.equals(location);
        boolean transformed = previous == null || !matrix.equals(previous) || (rebuilt && hasRigidAnimation());
        boolean wheelRotated = isWheelDisc()
                && previous != null && (!previousLeftRotation.equals(pose.getLeftRotation())
                || !previousRightRotation.equals(pose.getRightRotation()));
        boolean glowChanged = previous == null || glowing != carrier.isGlowing()
                || !Objects.equals(glowColor, carrier.getGlowColorOverride());
        syncPacked(nextLocation, nameChanged, moved, transformed, glowChanged);
        if (languageRevision != CasinoMessages.revision()) {
            for (var label : labels) {
                updateLabel(label);
                applyPose(label.display, new Matrix4f(matrix).mul(label.local));
            }
            languageRevision = CasinoMessages.revision();
        }
        if (!moved && !transformed && !glowChanged) return true;
        boolean singular = matrix.determinant3x3() == 0 || previous != null && previous.determinant3x3() == 0;
        var size = pose.getScale();
        Quaternionf rigidRotation = hasRigidAnimation() && size.x > 0 && size.x == size.y && size.x == size.z
                ? new Quaternionf(pose.getLeftRotation()).mul(pose.getRightRotation()).rotateY((float) Math.PI).normalize()
                : null;
        // At 0.12 radians the corner's straight interpolation stays within 0.002 blocks of its arc.
        boolean slowWheel = wheelRotated && rigidRotation != null && previousRigidRotation != null
                && Math.abs(rotationDot(rigidRotation, previousRigidRotation)) >= Math.cos(.12 / 2);
        for (var part : children) {
            if (moved) DisplayMovement.move(part.display, nextLocation);
            if (transformed) {
                boolean rigidBlock = part.rigidLocal != null && rigidRotation != null;
                boolean poseBasisChanged = part.rigidLocal != null && (rigidRotation == null) != (previousRigidRotation == null);
                boolean snap = singular || poseBasisChanged || (rigidBlock && rebuilt)
                        || (wheelRotated && !(rigidBlock && slowWheel));
                part.display.setInterpolationDuration(snap ? 0 : 1);
                if (rigidBlock) {
                    // Compose the authored axes directly: an SVD can swap the scale axes between ticks.
                    var block = part.rigidLocal;
                    part.display.setTransformation(new Transformation(
                            matrix.transformPosition(new Vector3f(block.getTranslation())),
                            new Quaternionf(rigidRotation).mul(block.getLeftRotation()),
                            new Vector3f(block.getScale()).mul(size.x), new Quaternionf()));
                } else applyPose(part.display, new Matrix4f(matrix).mul(part.local));
                part.display.setInterpolationDelay(0);
            }
            if (glowChanged) {
                part.display.setGlowing(carrier.isGlowing());
                part.display.setGlowColorOverride(carrier.getGlowColorOverride());
            }
        }
        previous = matrix;
        previousRigidRotation = rigidRotation;
        previousLeftRotation.set(pose.getLeftRotation());
        previousRightRotation.set(pose.getRightRotation());
        location = nextLocation;
        glowing = carrier.isGlowing();
        glowColor = carrier.getGlowColorOverride();
        return true;
    }

    private boolean hasRigidAnimation() {
        return switch (name) {
            case "money_wheel_disc", "wheel_of_fortune_disc", "money_wheel_pointer",
                    "wheel_of_fortune_pointer", "penguin_cross_penguin", "crash_rocket",
                    "duck_race_duck_1", "duck_race_duck_2", "duck_race_duck_3", "duck_race_duck_4",
                    "dragon_tile_safe", "dragon_tile_trap", "dragon_tile_hidden" -> true;
            case null, default -> false;
        };
    }

    private static float rotationDot(Quaternionf a, Quaternionf b) {
        // JOML 1.10.8 and 1.10.9 expose different binary signatures for Quaternionf.dot.
        return a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w;
    }

    private boolean isWheelDisc() {
        return "wheel_of_fortune_disc".equals(name) || "money_wheel_disc".equals(name);
    }

    private void syncPacked(Location at, boolean nameChanged, boolean moved, boolean transformed,
                            boolean glowChanged) {
        if (audience == null || !audience.needed(true) || ModelRepository.get("casino/" + name) == null) {
            removePacked();
            return;
        }
        if (packed == null) {
            packed = DisplayPlacement.spawn(at, ItemDisplay.class, display -> {
                display.setVisibleByDefault(false);
                register.accept(display);
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                display.setItemStack(packedItems.apply(name));
                display.setTransformation(packedPose());
                display.setInterpolationDuration(1);
                display.setGlowing(carrier.isGlowing());
                display.setGlowColorOverride(carrier.getGlowColorOverride());
                audience.add(display, true);
            });
            return;
        }
        if (nameChanged) packed.setItemStack(packedItems.apply(name));
        if (moved) DisplayMovement.move(packed, at);
        if (transformed) {
            packed.setTransformation(packedPose());
            packed.setInterpolationDelay(0);
        }
        if (glowChanged) {
            packed.setGlowing(carrier.isGlowing());
            packed.setGlowColorOverride(carrier.getGlowColorOverride());
        }
    }

    private Transformation packedPose() {
        var pose = carrier.getTransformation();
        return new Transformation(new Vector3f(pose.getTranslation()), new Quaternionf(pose.getLeftRotation()),
                new Vector3f(pose.getScale()), new Quaternionf(pose.getRightRotation()).rotateY((float) Math.PI));
    }

    static boolean sameBlockGeometry(ModelDefinition first, ModelDefinition second) {
        if (first == null || second == null || !first.labels().isEmpty()
                || !second.labels().isEmpty() || first.blocks().size() != second.blocks().size()) return false;
        for (int i = 0; i < first.blocks().size(); i++) {
            var a = first.blocks().get(i);
            var b = second.blocks().get(i);
            if (!a.position().equals(b.position()) || !a.size().equals(b.size())
                    || !a.rotation().equals(b.rotation())) return false;
        }
        return true;
    }

    private static void applyPose(Display display, Matrix4f matrix) {
        float length = matrix.getScale(new Vector3f()).length();
        float factor = length == 0 ? 1 : 64f / length;
        display.setTransformationMatrix(new Matrix4f(matrix).scale(factor));
        var pose = display.getTransformation();
        pose.getScale().div(factor);
        display.setTransformation(pose);
    }

    private void initialize(Display display, Matrix4f pose, boolean common) {
        register.accept(display);
        if (audience != null) {
            if (common) audience.common(display);
            else audience.add(display, false);
        }
        applyPose(display, pose);
        display.setInterpolationDuration(0);
        display.setGlowing(carrier.isGlowing());
        display.setGlowColorOverride(carrier.getGlowColorOverride());
    }

    private void updateBlock(BlockDisplay display, ModelDefinition.Block box) {
        display.setBlock(box.material().createBlockData());
        display.setBrightness(box.brightness() != null
                ? new Display.Brightness(box.brightness(), box.brightness()) : carrier.getBrightness());
    }

    private void build(ModelDefinition model, Matrix4f parent, boolean nativeNeeded, boolean labelsNeeded) {
        String face = cardFace(name);
        if (nativeNeeded && face != null) buildCard(face, parent);
        else if (nativeNeeded) for (var box : model.blocks()) {
            var local = ModelGeometry.matrix(box);
            var display = DisplayPlacement.spawn(carrier.getLocation(), BlockDisplay.class, d -> {
                initialize(d, new Matrix4f(parent).mul(local), false);
                updateBlock(d, box);
            });
            children.add(new Part(display, local, hasRigidAnimation() ? ModelGeometry.pose(box) : null));
        }
        if (labelsNeeded) for (int index = 0; index < model.labels().size(); index++) {
            var label = model.labels().get(index);
            var local = new Matrix4f();
            int labelIndex = index;
            var display = DisplayPlacement.spawn(carrier.getLocation(), TextDisplay.class, d -> {
                updateLabel(new LabelPart(d, local, label, labelIndex));
                initialize(d, new Matrix4f(parent).mul(local), true);
                d.setBillboard(Display.Billboard.FIXED);
                d.setDefaultBackground(false);
                d.setBackgroundColor(Color.fromARGB(0));
                d.setLineWidth(1000);
            });
            children.add(new Part(display, local));
            labels.add(new LabelPart(display, local, label, index));
        }
        languageRevision = CasinoMessages.revision();
    }

    private static String cardFace(String name) {
        if (!name.startsWith("card_") || name.equals("card_52")) return null;
        int card = Integer.parseInt(name.substring(5));
        String suit = List.of("spades", "hearts", "clubs", "diamonds").get(card / 13);
        String rank = List.of("ace", "2", "3", "4", "5", "6", "7", "8", "9", "10",
                "jack", "queen", "king").get(card % 13);
        return suit + "_" + rank;
    }

    private void buildCard(String face, Matrix4f parent) {
        var textures = PlayingCardHeads.tiles(face);
        for (int tile = 0; tile < textures.size(); tile++) {
            var pose = PlayingCardHeads.pose(1, 4.0 / 3, true, tile, 4, 6);
            var local = new Matrix4f().translate(0, -2f / 3, 0)
                    .translate(pose.getTranslation()).rotate(pose.getLeftRotation())
                    .scale(pose.getScale()).rotate(pose.getRightRotation());
            int index = tile;
            var display = DisplayPlacement.spawn(carrier.getLocation(), ItemDisplay.class, d -> {
                initialize(d, new Matrix4f(parent).mul(local), false);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setItemStack(PlayingCardHeads.item(textures.get(index)));
            });
            children.add(new Part(display, local));
        }
    }

    private void updateLabel(LabelPart part) {
        var label = part.label;
        var fit = LabelLayout.fit(CasinoMessages.modelComponent(name, part.index, label.text()), label.width(), label.height());
        var p = label.position();
        part.local.translation((float)p.x(), (float)p.y() - .125f * fit.scale(), (float)p.z()).scale(fit.scale());
        PlatformMessages.text(part.display, fit.text().colorIfAbsent(TextColor.color(label.color() == null ? 0xf6edcf : label.color())));
    }

    private void removeChildren() {
        for (var part : children) {
            ownerParts.remove(part.display);
            if (audience != null) audience.remove(part.display);
            else if (!DisplayMovement.retire(part.display)) part.display.remove();
        }
        children.clear();
        labels.clear();
    }

    private void removePacked() {
        if (packed == null) return;
        ownerParts.remove(packed);
        audience.remove(packed);
        packed = null;
    }

    private void remove() {
        removeChildren();
        removePacked();
    }
}
