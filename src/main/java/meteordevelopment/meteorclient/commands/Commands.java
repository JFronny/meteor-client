/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.commands.*;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.utils.PostInit;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.server.permissions.PermissionSet;

import java.util.*;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class Commands {
    public static final FabricClientCommandSource COMMAND_SOURCE = (FabricClientCommandSource) new ClientSuggestionProvider(null, mc, PermissionSet.ALL_PERMISSIONS);
    public static final List<Command> COMMANDS = new ArrayList<>();
    public static CommandDispatcher<FabricClientCommandSource> DISPATCHER = new CommandDispatcher<>();

    @PostInit(dependencies = PathManagers.class)
    public static void init() {
        add(new VClipCommand());
        add(new HClipCommand());
        add(new DismountCommand());
        add(new DisconnectCommand());
        add(new DamageCommand());
        add(new DropCommand());
        add(new EnchantCommand());
        add(new FakePlayerCommand());
        add(new FriendsCommand());
        add(new CommandsCommand());
        add(new InventoryCommand());
        add(new NbtCommand());
        add(new NotebotCommand());
        add(new PeekCommand());
        add(new EnderChestCommand());
        add(new ProfilesCommand());
        add(new ReloadCommand());
        add(new ResetCommand());
        add(new SayCommand());
        add(new ServerCommand());
        add(new SwarmCommand());
        add(new ToggleCommand());
        add(new SettingCommand());
        add(new SpectateCommand());
        add(new GamemodeCommand());
        add(new SaveMapCommand());
        add(new MacroCommand());
        add(new ModulesCommand());
        add(new BindsCommand());
        add(new GiveCommand());
        add(new BindCommand());
        add(new FovCommand());
        add(new RotationCommand());
        add(new WaypointCommand());
        add(new InputCommand());
        add(new WaspCommand());
        add(new LocateCommand());
        add(new HelpCommand());

        COMMANDS.sort(Comparator.comparing(Command::getName));

        MeteorClient.EVENT_BUS.subscribe(Commands.class);

        ClientCommandRegistrationCallback.EVENT.register(Commands::onJoin);
    }

    private static void copyChildren(CommandNode<FabricClientCommandSource> origin,
                                     CommandNode<FabricClientCommandSource> target,
                                     Map<CommandNode<FabricClientCommandSource>, CommandNode<FabricClientCommandSource>> originalToCopy) {
        for (CommandNode<FabricClientCommandSource> child : origin.getChildren()) {
            CommandNode<FabricClientCommandSource> result = child.createBuilder().build();
            originalToCopy.put(child, result);
            target.addChild(result);

            if (!child.getChildren().isEmpty()) {
                copyChildren(child, result, originalToCopy);
            }
        }
    }

    public static void add(Command command) {
        COMMANDS.removeIf(existing -> existing.getName().equals(command.getName()));
        COMMANDS.add(command);
    }

    public static void dispatch(String message) throws CommandSyntaxException {
        DISPATCHER.execute(message, COMMAND_SOURCE);
    }

    public static Command get(String name) {
        for (Command command : COMMANDS) {
            if (command.getName().equals(name)) {
                return command;
            }
        }

        return null;
    }

    /**
     * Argument types that rely on Minecraft registries access those registries through a {@link CommandBuildContext}
     * object. Since dynamic registries are specific to each server, we need to make a new CommandBuildContext object
     * every time we join a server.
     * <p>
     * The command tree and by extension the {@link CommandDispatcher} also have to be rebuilt because:
     * <ol>
     * <li>Argument types that require registries use a registry wrapper object that is created and stored in the
     *     argument type objects when the command tree is built.
     * <li>Registry entries and keys are compared using referential equality. Even if the data encoded is the same,
     *     registry wrapper objects' dynamic data becomes stale after joining another server.
     * <li>The CommandDispatcher's node merging only adds missing children, it cannot replace stale argument type
     *     objects.
     * </ol>
     *
     * @author Crosby
     */
    private static void onJoin(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess) {
        DISPATCHER = dispatcher;
        Command.REGISTRY_ACCESS = registryAccess;
        for (Command command : COMMANDS) {
            command.registerTo((CommandDispatcher) dispatcher);
        }
    }
}
