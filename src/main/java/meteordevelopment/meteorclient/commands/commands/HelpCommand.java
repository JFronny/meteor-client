/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.commands.arguments.CommandArgumentType;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Objects;

public class HelpCommand extends Command {
    public HelpCommand() {
        super("help", "Shows you what a command does.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(argument("command", CommandArgumentType.create()).executes(context -> {
            showHelp(CommandArgumentType.get(context));
            return SINGLE_SUCCESS;
        }));

        builder.executes(_ -> {
            showHelp(this);
            return SINGLE_SUCCESS;
        });
    }

    private void showHelp(Command cmd) {
        MutableComponent msg = Component.literal("");
        msg.append(Component.literal("Help for ").withStyle(ChatFormatting.GRAY).append(Component.literal(cmd.getName()).withStyle(ChatFormatting.YELLOW)));
        msg.append(Component.literal("\n ")).append(Component.literal("Description: ").withStyle(ChatFormatting.GRAY).append(Component.literal(cmd.getDescription()).withStyle(ChatFormatting.WHITE)));

        if (!cmd.getAliases().isEmpty()) {
            msg.append(Component.literal("\n ")).append(Component.literal("Aliases: ").withStyle(ChatFormatting.GRAY));
            msg.append(Component.literal(String.join(", ", cmd.getAliases())).withStyle(ChatFormatting.AQUA));
        }

        msg.append(getUsageText(cmd));
        ChatUtils.sendMsg(msg);
    }

    private MutableComponent getUsageText(Command cmd) {
        var source = Objects.requireNonNull(mc.getConnection()).getSuggestionsProvider();
        var root = Commands.DISPATCHER.getRoot();
        var node = root.getChild(cmd.getName());

        MutableComponent usagesText = Component.literal("");

        if (node != null) {
            var usages = Commands.DISPATCHER.getSmartUsage(node, (FabricClientCommandSource) source);

            for (String usage : usages.values()) {
                usagesText.append(Component.literal("\n " + cmd + " ").withStyle(ChatFormatting.GREEN)).append(Component.literal(usage).withStyle(ChatFormatting.GREEN));
            }
        }

        if (usagesText.getString().isEmpty()) {
            usagesText.append(Component.literal("\n " + cmd).withStyle(ChatFormatting.GREEN));
        }

        return Component.literal("\n Usage:").withStyle(ChatFormatting.GRAY).append(usagesText);
    }
}
