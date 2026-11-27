package GoldenEggOVO.TDCasino.probe;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import GoldenEggOVO.TDCasino.TDCasinoPlugin;
import GoldenEggOVO.TDCasino.machine.definition.MachineDefinition;

/** Test-only plugin in a fresh loopback fixture. No real player connection is required. */
public final class CasinoStandaloneProbe extends JavaPlugin {
    @Override public void onEnable() { Bukkit.getScheduler().runTaskLater(this, this::runProbe, 100); }
    private void runProbe() {
        try {
            require(Bukkit.getIp().equals("127.0.0.1") && Bukkit.getPort()==25617,"loopback fixture");
            var plugin=(TDCasinoPlugin)Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("3DCasino"));
            require(plugin.isEnabled(),"Casino enabled");
            World world=Bukkit.getWorlds().getFirst();
            var suggestions = new LinkedHashSet<>(List.of("3dcasino", "3dcasino:3dcasino", "unrelated"));
            Bukkit.getPluginManager().callEvent(new org.bukkit.event.player.PlayerCommandSendEvent(
                    player(world, new AtomicInteger(), true, new UUID(0, 999)), suggestions));
            require(suggestions.equals(new LinkedHashSet<>(List.of("3dcasino", "unrelated"))),
                    "Casino root must appear once in client command suggestions");
            getLogger().info("CASINO_COMMAND_SUGGESTIONS_PASS namespace_hidden=true other_commands_retained=true");
            pictureMenuProbe(plugin,world);
            if(Boolean.getBoolean("boards.probe.craftengine"))craftEngineProbe(plugin,world);
            require(plugin.pack()!=null,"independent pack service initialized");
            casinoMenuProbe(plugin,world);
            casinoWheelProbe(world);
            minesCellInterpolationProbe(world);
            for(int index=0;index<12;index++) {
                var chunk=world.getChunkAt((index*24)>>4,0); chunk.load();
                world.addPluginChunkTicket(chunk.getX(),chunk.getZ(),this);
            }
            var manager=plugin.casino().machines();
            call(manager,"restoreLoaded",new Class<?>[0]);
            var machines=(Map<?,?>)field(manager,"machines");
            var placements=(Map<?,?>)field(manager,"placements");
            Path marker=getDataFolder().toPath().resolve("created.txt");
            if (Files.exists(marker)) {
                require(placements.size()==12 && machines.size()==12,"12 machines restored");
                if (Integer.getInteger("split.probe.boot",1)==3) {
                    var entities=new ArrayList<org.bukkit.entity.Entity>();
                    for(var machine:machines.values()) {
                        var type=machine.getClass();
                        while(type!=null) { try {var f=type.getDeclaredField("parts");f.setAccessible(true);entities.addAll((Collection<org.bukkit.entity.Entity>)f.get(machine));break;}catch(NoSuchFieldException ignored){type=type.getSuperclass();} }
                    }
                    manager.close();
                    require(!entities.isEmpty(),"cleanup exercises real entities");
                    require(entities.stream().noneMatch(org.bukkit.entity.Entity::isValid),"machine entities removed on close");
                    getLogger().info("CASINO_CLEANUP_PASS entities="+entities.size());
                }
                getLogger().info("CASINO_STANDALONE_RESTORE_PASS machines=12");
            } else {
                require(machines.isEmpty(),"fresh machine store");
                int index=0;
                for(String game:new TreeSet<>(MachineDefinition.games())) {
                    final int offset=index++;
                    Player base=player(world,new AtomicInteger(),true,new UUID(0,100+offset));
                    Player viewer=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->{
                        if(method.getName().equals("getLocation")||method.getName().equals("getEyeLocation"))return new Location(world,offset*24+.5,83,.5);
                        return Proxy.getInvocationHandler(base).invoke(base,method,args);
                    });
                    manager.command(viewer,new String[]{"create",game});
                    require(machines.size()==index,"machine created: "+game);
                }
                require(index==12 && placements.size()==12,"12 machine placements saved");
                Files.createDirectories(marker.getParent()); Files.writeString(marker,"12");
                getLogger().info("CASINO_STANDALONE_CREATE_PASS machines=12");
            }
            if(Boolean.getBoolean("split.probe.both")) companionProbe(plugin,world);
        }catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"CASINO_STANDALONE_PROBE_FAIL",error);}
    }
    private void craftEngineProbe(Plugin plugin,World world)throws Exception{
        world.getChunkAt(0,0).addPluginChunkTicket(this);
        Plugin ce=Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("CraftEngine"));
        require(ce.isEnabled(),"CraftEngine enabled");
        var registration=new HashMap<String,String>();
        try(var paths=Files.walk(ce.getDataFolder().toPath().resolve("resources/3dcasino/configuration"))){
            for(Path path:paths.filter(file->file.toString().endsWith(".yml")||file.toString().endsWith(".yaml")).toList()){
                var part=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(path.toFile());
                var items=part.getConfigurationSection("items");
                if(items==null)continue;
                for(String key:items.getKeys(false))
                    require(registration.putIfAbsent(key,items.getString(key+".item_model"))==null,"unique CE item: "+key);
            }
        }
        Class<?> type=Class.forName("GoldenEggOVO.TDCasino.Core.pack.CraftEngineModels",true,plugin.getClass().getClassLoader());
        var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);Object models=ctor.newInstance();
        require((boolean)call(models,"ready",new Class<?>[0]),"all CE models registered and buildable");
        Field ids=type.getDeclaredField("IDS");ids.setAccessible(true);int count=0;
        for(Object id:(List<?>)ids.get(null)){
            org.bukkit.inventory.ItemStack item=(org.bukkit.inventory.ItemStack)call(models,"item",new Class<?>[]{String.class},id);
            require(item.getType()==org.bukkit.Material.PAPER,"CE carrier");
            var itemModel=org.bukkit.NamespacedKey.fromString(registration.get("3dcasino:"+id));
            require(itemModel!=null && itemModel.equals(item.getItemMeta().getItemModel()),"item model component: "+id+" actual="+item.getItemMeta().getItemModel());
            org.bukkit.entity.ItemDisplay display=world.spawn(new Location(world,8,84,0),org.bukkit.entity.ItemDisplay.class,d->{
                d.setVisibleByDefault(false);d.setPersistent(false);d.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.FIXED);d.setItemStack(item);
            });
            require(display.isValid()&&!display.isVisibleByDefault(),"packed display hidden before audience");
            require(display.getItemDisplayTransform()==org.bukkit.entity.ItemDisplay.ItemDisplayTransform.FIXED,"client-readable model display transform");
            display.remove();count++;
        }
        Map<?,?> before = new HashMap<>((Map<?,?>)field(models,"models"));
        Field checked=type.getDeclaredField("checked");checked.setAccessible(true);checked.setLong(models,0);
        require((boolean)call(models,"ready",new Class<?>[0]),"CE models remain ready after registry poll");
        Map<?,?> after=(Map<?,?>)field(models,"models");
        for(var entry:before.entrySet())require(entry.getValue()==after.get(entry.getKey()),"unchanged CE definition reuses built model: "+entry.getKey());
        getLogger().info("CASINO_CRAFTENGINE_PASS models="+count+" cached_poll=true optional_api=true client_visual_test=false");
    }
    private void companionProbe(TDCasinoPlugin casino,World world)throws Exception {
        Plugin boards=Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("3DGames"));
        require(boards.isEnabled(),"companion enabled");
        require(boards.getClass().getClassLoader()!=casino.getClass().getClassLoader(),"distinct plugin loaders");
        Object a=call(boards,"sessions",new Class<?>[0]), b=casino.sessions();
        UUID id=UUID.randomUUID();
        require((boolean)call(a,"reserve",new Class<?>[]{UUID.class,String.class},id,"tabletop"),"reserve tabletop");
        require(!casino.sessions().reserve(id,"casino"),"tabletop blocks casino");
        require("tabletop".equals(casino.companion().reservation(id)),"reflective reservation lookup");
        casino.sessions().close();
        require("tabletop".equals(call(boards,"sessionReservation",new Class<?>[]{UUID.class},id)),"casino close preserves tabletop");
        call(a,"release",new Class<?>[]{UUID.class,String.class},id,"tabletop");
        require(casino.sessions().reserve(id,"casino"),"reserve casino");
        require(!(boolean)call(a,"reserve",new Class<?>[]{UUID.class,String.class},id,"tabletop"),"casino blocks tabletop");
        call(a,"close",new Class<?>[0]);
        require("casino".equals(casino.sessionReservation(id)),"tabletop close preserves casino");
        casino.sessions().release(id,"casino");
        require(!casino.companion().paymentBlocked(id),"no companion pending payment");
        Object peer=call(boards,"companion",new Class<?>[0]);
        require(!(boolean)call(peer,"paymentBlocked",new Class<?>[]{UUID.class},id),"no casino pending payment");
        Object boardLedger=field(call(boards,"integrations",new Class<?>[0]),"ledger");
        Object casinoLedger=casino.casino().payments();
        for(Object ledger:List.of(boardLedger,casinoLedger)) {
            Field failed=ledger.getClass().getDeclaredField("storageFailed"); failed.setAccessible(true);
            boolean saved=failed.getBoolean(ledger);
            try {
                failed.setBoolean(ledger,true);
                boolean blocked=ledger==boardLedger ? casino.companion().paymentBlocked(id)
                    : (boolean)call(peer,"paymentBlocked",new Class<?>[]{UUID.class},id);
                require(blocked,"companion payment fault blocks paid entry");
            }finally{failed.setBoolean(ledger,saved);}
        }
        packIsolationProbe(boards,casino,world);
        var viewer=player(world,new AtomicInteger(),true,id);
        casino.menus().main(viewer); casino.companion().forgetMenus(viewer);
        require(((Map<?,?>)field(casino.menus(),"sessions")).containsKey(id),"companion menu forget preserves local menu");
        casino.forgetMenus(viewer);
        getLogger().info("CASINO_COINSTALL_PASS loaders=distinct reservations=bidirectional local_close=isolated payment_lookup=bidirectional");
    }
    private void packIsolationProbe(Plugin boards,TDCasinoPlugin casino,World world)throws Exception {
        var added=new ArrayList<UUID>(); var removed=new ArrayList<UUID>();
        Player base=player(world,new AtomicInteger(),true,UUID.randomUUID());
        Player viewer=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->{
            if(method.getName().equals("addResourcePack")){added.add((UUID)args[0]);return null;}
            if(method.getName().equals("removeResourcePack")){removed.add((UUID)args[0]);return null;}
            if(method.getName().equals("removeResourcePacks"))throw new IllegalStateException("global pack removal forbidden");
            return Proxy.getInvocationHandler(base).invoke(base,method,args);
        });
        var services=new ArrayList<Object>();
        try {
            for(Plugin plugin:List.of(boards,casino)) {
                var config=plugin.getConfig();
                var keys=List.of("rendering.mode","rendering.resource-pack.source","rendering.resource-pack.url");
                var saved=new ArrayList<Object>(); for(String key:keys)saved.add(config.get(key));
                try {
                    config.set(keys.get(0),"resource-pack");config.set(keys.get(1),"external");config.set(keys.get(2),"https://example.invalid/probe.zip");
                    Class<?> type=Class.forName(plugin==boards?"GoldenEggOVO.TDGames.Core.pack.ResourcePackService":"GoldenEggOVO.TDCasino.Core.pack.ResourcePackService",true,plugin.getClass().getClassLoader());
                    Object service=type.getConstructor(plugin.getClass(),java.util.function.BooleanSupplier.class,java.util.function.Function.class)
                        .newInstance(plugin,(java.util.function.BooleanSupplier)()->true,(java.util.function.Function<String,org.bukkit.inventory.ItemStack>)name->null);
                    services.add(service); call(service,"request",new Class<?>[]{Player.class},viewer);
                }finally{for(int i=0;i<keys.size();i++)config.set(keys.get(i),saved.get(i));}
            }
            require(added.size()==2&&!added.get(0).equals(added.get(1)),"independent pack ids");
            require(removed.isEmpty(),"second pack request preserves first");
            call(services.get(0),"remove",new Class<?>[]{Player.class},viewer);
            require(removed.equals(List.of(added.get(0))),"tabletop removal scoped to its own pack");
            require(added.get(1).equals(call(services.get(1),"requestId",new Class<?>[]{Player.class},viewer)),"casino request remains live");
            call(services.get(1),"remove",new Class<?>[]{Player.class},viewer);
            require(removed.equals(added),"casino removal scoped to its own pack");
            getLogger().info("CASINO_PACK_ISOLATION_PASS independent_ids=true remove_scoped=true client_download=false");
        }finally{for(Object service:services)call(service,"close",new Class<?>[0]);}
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void casinoMenuProbe(Plugin plugin, World world) throws Exception {
        AtomicInteger dialogs = new AtomicInteger();
        Player viewer = player(world, dialogs, true, UUID.randomUUID());
        Object casino = call(plugin, "casino", new Class<?>[0]);
        require(casino != null, "Casino initialized");
        Object menus = call(casino, "menus", new Class<?>[0]);
        call(menus, "main", new Class<?>[]{Player.class}, viewer);
        require(dialogs.get() == 1, "native Casino catalog built");
        call(menus, "forget", new Class<?>[]{Player.class}, viewer);
        require(!((Map<?, ?>) field(menus, "sessions")).containsKey(viewer.getUniqueId()), "Casino page released");
        getLogger().info("CASINO_CASINO_MENU_PASS native_dialog=true namespace=3dcasino client_visual_test=false");
    }

    private void casinoWheelProbe(World world) throws Exception {
        boolean stable = true;
        var travel = GoldenEggOVO.TDCasino.machine.WheelDisplay.class.getDeclaredMethod("spinTravel", double.class);
        travel.setAccessible(true);
        for (String name : List.of("wheel_of_fortune_disc", "money_wheel_disc")) {
            var parts = new ArrayList<org.bukkit.entity.Entity>();
            var at = new Location(world, 0, 83, 0);
            at.getChunk().load();
            require(at.getChunk().isLoaded(), "Wheel fixture chunk loaded");
            var item = GoldenEggOVO.TDCasino.render.model.ModelCarrierItems.resolve("3dcasino:" + name);
            require(name.equals(GoldenEggOVO.TDCasino.render.model.ModelCarrierItems.modelName(item)),
                    "Wheel fixture model marked: " + name);
            var carrier = world.spawn(at, org.bukkit.entity.ItemDisplay.class,
                    display -> {
                        display.setItemStack(item);
                        display.setTransformation(GoldenEggOVO.TDCasino.render.MachineGeometry.itemPose(4, 0, 0));
                    });
            parts.add(carrier);
            try {
                getLogger().info("CASINO_CASINO_WHEEL_FIXTURE model=" + name + " valid=" + carrier.isValid()
                        + " chunk=" + carrier.getChunk().isLoaded()
                        + " tag=" + GoldenEggOVO.TDCasino.render.model.ModelCarrierItems.modelName(carrier.getItemStack()));
                require(carrier.isValid(), "Wheel fixture carrier valid before model construction");
                var group = new GoldenEggOVO.TDCasino.render.MachineModelDisplay(carrier, parts::add, parts);
                var children = parts.stream().filter(org.bukkit.entity.BlockDisplay.class::isInstance)
                        .map(org.bukkit.entity.BlockDisplay.class::cast).toList();
                require(!children.isEmpty(), "Wheel regression must exercise real native displays");
                float maxRadius = 0, maxEndpointRadius = 0, maxEdgeError = 0;
                int interpolatedFrames = 0;
                String witness = "";
                for (int frame = 1; frame <= 80; frame++) {
                    var before = children.stream().map(org.bukkit.entity.Display::getTransformation).toList();
                    double progress = frame / 80.0;
                    var pose = GoldenEggOVO.TDCasino.render.MachineGeometry.itemPose(4, 0, 0);
                    pose.getLeftRotation().rotateZ((float) ((10 * Math.PI + .7) * (double) travel.invoke(null, progress)));
                    carrier.setTransformation(pose);
                    group.sync();
                    if (children.getFirst().getInterpolationDuration() == 1) interpolatedFrames++;
                    for (int i = 0; i < children.size(); i++) {
                        var from = before.get(i);
                        var to = children.get(i).getTransformation();
                        var start = displayMatrix(from);
                        for (float alpha : new float[] {0, .25f, .5f, .75f, 1}) {
                            float progressAlpha = children.get(i).getInterpolationDuration() == 0 ? 1 : alpha;
                            var interpolated = new org.bukkit.util.Transformation(
                                    new org.joml.Vector3f(from.getTranslation()).lerp(to.getTranslation(), progressAlpha),
                                    new org.joml.Quaternionf(from.getLeftRotation()).slerp(to.getLeftRotation(), progressAlpha),
                                    new org.joml.Vector3f(from.getScale()).lerp(to.getScale(), progressAlpha),
                                    new org.joml.Quaternionf(from.getRightRotation()).slerp(to.getRightRotation(), progressAlpha));
                            var matrix = displayMatrix(interpolated);
                            for (int axis = 0; axis < 3; axis++) {
                                var edge = new org.joml.Vector3f(axis == 0 ? 1 : 0, axis == 1 ? 1 : 0, axis == 2 ? 1 : 0);
                                float length = start.transformDirection(new org.joml.Vector3f(edge)).length();
                                maxEdgeError = Math.max(maxEdgeError, Math.abs(matrix.transformDirection(edge).length() - length));
                            }
                            for (int corner = 0; corner < 8; corner++) {
                                var point = matrix.transformPosition(new org.joml.Vector3f(corner & 1, (corner >> 1) & 1, (corner >> 2) & 1));
                                float radius = (float) Math.hypot(point.x, point.y);
                                if (alpha == 0 || alpha == 1) maxEndpointRadius = Math.max(maxEndpointRadius, radius);
                                if (radius > maxRadius) {
                                    maxRadius = radius;
                                    witness = " frame=" + frame + " part=" + i + " alpha=" + alpha;
                                }
                            }
                        }
                    }
                }
                getLogger().info("CASINO_CASINO_WHEEL_INTERPOLATION model=" + name
                        + " radius=" + maxRadius + " endpoint_radius=" + maxEndpointRadius
                        + " edge_error=" + maxEdgeError + " interpolated_frames=" + interpolatedFrames + witness);
                stable &= maxRadius < .9f && maxEdgeError < .0001f && interpolatedFrames > 1 && interpolatedFrames < 80;
            } finally {
                parts.forEach(org.bukkit.entity.Entity::remove);
            }
        }
        require(stable, "Wheels must remain within the frame during interpolation");
    }

    private void minesCellInterpolationProbe(World world) throws Exception {
        var anchor = new Location(world, 184, 90, 184);
        var chunk = anchor.getChunk();
        var scope = new org.bukkit.NamespacedKey(this, "mines_subtick_probe");
        chunk.load();
        require(chunk.isLoaded(), "Mines scope chunk is loaded");
        boolean ticketAdded = world.addPluginChunkTicket(chunk.getX(), chunk.getZ(), this);
        clearMinesScopeChunk(chunk, scope);
        var entities = new ArrayList<org.bukkit.entity.Entity>();
        try {
            var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            var machineClass = GoldenEggOVO.TDCasino.game.mines.MinesMachine.class;
            var machine = unsafe.allocateInstance(machineClass);
            var refined = machineClass.getDeclaredField("refined");
            refined.setAccessible(true);
            refined.setBoolean(machine, true);
            var cellPose = machineClass.getDeclaredMethod("cellPose", double.class);
            cellPose.setAccessible(true);
            var flipPitch = machineClass.getDeclaredMethod("flipPitch", double.class, boolean.class);
            flipPitch.setAccessible(true);
            float scale = (float) (2.0 / 3.5);
            double minimum = Double.POSITIVE_INFINITY;
            int samples = 0;
            for (var material : List.of(org.bukkit.Material.POLISHED_DEEPSLATE,
                        org.bukkit.Material.EMERALD_BLOCK, org.bukkit.Material.TNT)) {
                    var carrier = world.spawn(anchor.clone().add(0, 1.115 * (2.0 / 3.5), 0),
                            org.bukkit.entity.ItemDisplay.class, display -> {
                                display.setPersistent(false);
                                display.setGravity(false);
                                display.setVisibleByDefault(false);
                                display.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.NONE);
                                display.setItemStack(new org.bukkit.inventory.ItemStack(material));
                                display.setInterpolationDuration(1);
                                display.getPersistentDataContainer().set(scope,
                                        org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                            });
                    entities.add(carrier);
                    require(carrier.isValid(), "Mines carrier is valid before round-trip");
                    require(carrier.getPersistentDataContainer().has(scope,
                            org.bukkit.persistence.PersistentDataType.BYTE), "Mines carrier scope tag");
                    require(GoldenEggOVO.TDCasino.render.model.ModelCarrierItems
                            .modelName(carrier.getItemStack()) == null, "Default Mines cell has no named model children");
                    var nodes = new org.bukkit.util.Transformation[9];
                    for (int frame = 0; frame <= 8; frame++) {
                        var local = (org.bukkit.util.Transformation) cellPose.invoke(machine,
                                flipPitch.invoke(null, frame / 8.0, false));
                        carrier.setTransformation(new org.bukkit.util.Transformation(
                                new org.joml.Vector3f(local.getTranslation()).mul(scale),
                                new org.joml.Quaternionf(local.getLeftRotation()),
                                new org.joml.Vector3f(local.getScale()).mul(scale),
                                new org.joml.Quaternionf(local.getRightRotation())));
                        carrier.setInterpolationDelay(0);
                        // Read real Paper state back after setting every tick endpoint.
                        nodes[frame] = carrier.getTransformation();
                    }
                    for (int segment = 0; segment < 8; segment++) {
                        var first = nodes[segment]; var last = nodes[segment + 1];
                        for (int step = 0; step <= 41; step++) {
                            float t = step / 41f;
                            var interpolated = new org.bukkit.util.Transformation(
                                    new org.joml.Vector3f(first.getTranslation()).lerp(last.getTranslation(), t),
                                    new org.joml.Quaternionf(first.getLeftRotation()).slerp(last.getLeftRotation(), t),
                                    new org.joml.Vector3f(first.getScale()).lerp(last.getScale(), t),
                                    new org.joml.Quaternionf(first.getRightRotation()).slerp(last.getRightRotation(), t));
                            var matrix = displayMatrix(interpolated).rotateY((float) Math.PI);
                            double bottom = Double.POSITIVE_INFINITY;
                            for (int corner = 0; corner < 8; corner++) {
                                var point = matrix.transformPosition(new org.joml.Vector3f(
                                        (corner & 1) == 0 ? -.5f : .5f,
                                        (corner & 2) == 0 ? -.5f : .5f,
                                        (corner & 4) == 0 ? -.5f : .5f));
                                bottom = Math.min(bottom, carrier.getLocation().getY() - anchor.getY() + point.y);
                            }
                            minimum = Math.min(minimum, bottom); samples++;
                            require(bottom >= .6 - 2e-6, "Actual Paper Mines pose enters table: "
                                    + material + " segment=" + segment + " step=" + step + " y=" + bottom);
                        }
                    }
            }
            getLogger().info("CASINO_MINES_INTERPOLATION_PASS samples=" + samples
                    + " minimum_y=" + minimum + " baseline_y=0.6 real_paper_roundtrip=true"
                    + " carrier_path=common_generic_itemdisplay native_children=false client_visual_test=false");
        } finally {
            entities.forEach(org.bukkit.entity.Entity::remove);
            clearMinesScopeChunk(chunk, scope);
            require(Arrays.stream(chunk.getEntities()).noneMatch(entity -> entity.getPersistentDataContainer()
                    .has(scope, org.bukkit.persistence.PersistentDataType.BYTE)), "Mines probe scope is clean");
            if (ticketAdded) world.removePluginChunkTicket(chunk.getX(), chunk.getZ(), this);
        }
    }

    private static void clearMinesScopeChunk(org.bukkit.Chunk chunk, org.bukkit.NamespacedKey scope) {
        for (var entity : chunk.getEntities())
            if (entity.getPersistentDataContainer().has(scope, org.bukkit.persistence.PersistentDataType.BYTE))
                entity.remove();
    }

    private static org.joml.Matrix4f displayMatrix(org.bukkit.util.Transformation pose) {
        return new org.joml.Matrix4f().translation(pose.getTranslation()).rotate(pose.getLeftRotation())
                .scale(pose.getScale()).rotate(pose.getRightRotation());
    }

    private Player player(World world, AtomicInteger dialogs, boolean allowed) {
        UUID id = allowed ? UUID.fromString("00d14a82-6b6c-46ce-b7f8-ecc56a133420") : UUID.randomUUID();
        return player(world,dialogs,allowed,id);
    }
    private Player player(World world,AtomicInteger dialogs,boolean allowed,UUID id) {
        Location location = new Location(world, 0.5, 83, 0.5);
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> "BoardsProbe";
                case "getWorld" -> world;
                case "getLocation", "getEyeLocation" -> location.clone();
                case "getPersistentDataContainer" -> world.getPersistentDataContainer();
                case "isInsideVehicle", "isFlying", "isSneaking" -> false;
                case "getPose" -> org.bukkit.entity.Pose.STANDING;
                case "getServer" -> getServer();
                case "isOnline", "isValid", "isPermissionSet" -> true;
                case "isDead", "isOp" -> false;
                case "hasPermission" -> allowed;
                case "getEffectivePermissions" -> java.util.Set.of();
                case "teleport" -> true;
                case "showDialog" -> { require(args[0] != null, "native dialog built"); dialogs.incrementAndGet(); yield null; }
                case "sendMessage", "sendActionBar" -> null;
                case "hashCode" -> id.hashCode();
                case "equals" -> proxy == args[0];
                case "toString" -> "BoardsProbe";
                default -> null;
            });
    }


    @SuppressWarnings({"unchecked", "rawtypes"})
    private void pictureMenuProbe(Plugin plugin, World world) throws Exception {
        Object pack = call(plugin, "pack", new Class<?>[0]);
        Field mode = pack.getClass().getDeclaredField("mode"); mode.setAccessible(true);
        Field available = pack.getClass().getDeclaredField("available"); available.setAccessible(true);
        Object oldMode = mode.get(pack), oldAvailable = available.get(pack);
        AtomicInteger shown = new AtomicInteger();
        Player viewer = player(world, shown, true, new UUID(0, 998));
        var preference = (org.bukkit.NamespacedKey) field(pack, "preference");
        Byte oldPreference = viewer.getPersistentDataContainer().get(preference, org.bukkit.persistence.PersistentDataType.BYTE);
        Map<UUID,Object> requests = (Map<UUID,Object>) field(pack, "requests");
        Object oldRequest = requests.get(viewer.getUniqueId());
        try {
            mode.set(pack, Enum.valueOf((Class) mode.getType(), "MIXED"));
            available.set(pack, (java.util.function.BooleanSupplier) () -> true);
            viewer.getPersistentDataContainer().set(preference, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            ClassLoader loader = pack.getClass().getClassLoader();
            Class<?> stage = Class.forName(pack.getClass().getName()+"$Stage", true, loader);
            Class<?> request = Class.forName(pack.getClass().getName()+"$Request", true, loader);
            var constructor = request.getDeclaredConstructor(UUID.class, stage); constructor.setAccessible(true);
            requests.put(viewer.getUniqueId(), constructor.newInstance(UUID.randomUUID(), Enum.valueOf((Class)stage,"READY")));
            require((boolean)call(pack,"packed",new Class<?>[]{Player.class},viewer),"picture menu fixture is packed");
            Object menus = call(plugin, "menus", new Class<?>[0]);
            call(menus, "main", new Class<?>[]{Player.class}, viewer);
            call(menus, "preferences", new Class<?>[]{Player.class}, viewer);
            call(menus, "game", new Class<?>[]{Player.class, String.class}, viewer, "blackjack");
            var submit = new GoldenEggOVO.TDCasino.Core.menu.MenuView.Button(Component.text("Save"), null, 300, "save");
            var back = new GoldenEggOVO.TDCasino.Core.menu.MenuView.Button(Component.text("Back"), null, 300, "back");
            var exit = new GoldenEggOVO.TDCasino.Core.menu.MenuView.Button(Component.text("Close"), null, 300, null, "close");
            var form = new GoldenEggOVO.TDCasino.Core.menu.MenuView(Component.text("Amount"), List.of(),
                    List.of(new GoldenEggOVO.TDCasino.Core.menu.MenuView.Input("amount", Component.text("Amount"), "0", 16)),
                    List.of(submit, back), exit, 1);
            GoldenEggOVO.TDCasino.Core.menu.DialogMenus.open(plugin, viewer, form, (action, values) -> {});
            var confirmation = new GoldenEggOVO.TDCasino.Core.menu.MenuView(Component.text("Confirm"), List.of(), List.of(),
                    List.of(new GoldenEggOVO.TDCasino.Core.menu.MenuView.Button(Component.text("Confirm"), null, 300, "confirm"), back), exit, 1);
            GoldenEggOVO.TDCasino.Core.menu.DialogMenus.open(plugin, viewer, confirmation, (action, values) -> {});
            require(shown.get()==5,"picture menus and native input construct real Paper dialogs");
            getLogger().info("PICTURE_MENU_PASS real_paper_builder=true synthetic_ready=true pages=5 input_form=true client_visual_test=false");
        } finally {
            mode.set(pack,oldMode); available.set(pack,oldAvailable);
            if(oldPreference==null)viewer.getPersistentDataContainer().remove(preference);
            else viewer.getPersistentDataContainer().set(preference,org.bukkit.persistence.PersistentDataType.BYTE,oldPreference);
            if(oldRequest==null)requests.remove(viewer.getUniqueId());else requests.put(viewer.getUniqueId(),oldRequest);
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
