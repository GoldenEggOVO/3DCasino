package GoldenEggOVO.TDCasino.Core.pack;



import GoldenEggOVO.TDCasino.Core.render.model.ModelRepository;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.*;

public final class CraftEngineModels {
    public static final List<String> IDS = ids();
    private record Model(Object definition, ItemStack item) {}

    private final Map<String, Model> models = new HashMap<>();
    private long checked;
    private Plugin engine;

    synchronized boolean ready() {
        Plugin current = Bukkit.getPluginManager().getPlugin("CraftEngine");
        if (current == null || !current.isEnabled()) {
            models.clear();
            engine = null;
            return false;
        }
        long now = System.currentTimeMillis();
        if (current == engine && now - checked < 2000) return models.size() == IDS.size();
        if (current != engine) models.clear();
        engine = current;
        checked = now;
        try {
            ClassLoader loader = engine.getClass().getClassLoader();
            Class<?> api =
                    Class.forName(
                            "net.momirealms.craftengine.bukkit.api.CraftEngineItems", true, loader);
            Class<?> context =
                    Class.forName(
                            "net.momirealms.craftengine.core.item.ItemBuildContext", true, loader);
            Object empty = context.getMethod("empty").invoke(null);
            Method find = api.getMethod("byId", String.class);
            Map<String, Model> next = new HashMap<>();
            Map<Class<?>, Method> builders = new HashMap<>(), converters = new HashMap<>();
            for (String id : IDS) {
                Object definition = find.invoke(null, "3dcasino:" + id);
                if (definition == null) {
                    models.clear();
                    return false;
                }
                Model cached = models.get(id);
                if (cached == null || definition != cached.definition()) {
                    Class<?> type = definition.getClass();
                    Method build = builders.get(type);
                    if (build == null) {
                        build = type.getMethod("buildItem", context, int.class);
                        builders.put(type, build);
                    }
                    Object item = build.invoke(definition, empty, 1);
                    type = item.getClass();
                    Method convert = converters.get(type);
                    if (convert == null) {
                        convert = type.getMethod("platformItem");
                        converters.put(type, convert);
                    }
                    cached = new Model(definition, ((ItemStack) convert.invoke(item)).clone());
                }
                next.put(id, cached);
            }
            models.clear();
            models.putAll(next);
            return true;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException exception) {
            models.clear();
            return false;
        }
    }

    synchronized ItemStack item(String id) {
        if (!ready()) throw new IllegalStateException("CraftEngine casino models are not registered");
        Model model = models.get(id);
        if (model == null) throw new IllegalArgumentException("Unknown casino model: " + id);
        return model.item().clone();
    }

    private static List<String> ids() {
        return ModelRepository.ids("casino");
    }
}
