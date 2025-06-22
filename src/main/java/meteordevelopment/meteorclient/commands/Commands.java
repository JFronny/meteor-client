/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import meteordevelopment.meteorclient.commands.commands.*;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.utils.PostInit;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.command.CommandSource;

import java.util.*;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class Commands {
    public static final CommandDispatcher<FabricClientCommandSource> DISPATCHER = new CommandDispatcher<>();
    public static final FabricClientCommandSource COMMAND_SOURCE = (FabricClientCommandSource) new ClientCommandSource(null, mc, true);
    public static final List<Command> COMMANDS = new ArrayList<>();

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

        COMMANDS.sort(Comparator.comparing(Command::getName));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            Map<CommandNode<FabricClientCommandSource>, CommandNode<FabricClientCommandSource>> originalToCopy = new HashMap<>();
            originalToCopy.put(DISPATCHER.getRoot(), dispatcher.getRoot());
            copyChildren(DISPATCHER.getRoot(), dispatcher.getRoot(), originalToCopy);
        });
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
        command.registerTo((CommandDispatcher<CommandSource>) (Object) DISPATCHER);
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
}
