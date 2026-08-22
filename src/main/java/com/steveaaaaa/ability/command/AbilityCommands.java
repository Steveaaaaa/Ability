package com.steveaaaaa.ability.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.steveaaaaa.ability.progress.ExperienceService;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class AbilityCommands {
    private static final DynamicCommandExceptionType UNKNOWN_SKILL = new DynamicCommandExceptionType(
            skill -> Component.literal("Unknown skill: " + skill)
    );

    private AbilityCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("ability")
                .then(Commands.literal("progress")
                        .then(Commands.argument("skill", ResourceLocationArgument.id())
                                .executes(context -> showProgress(
                                        context.getSource(),
                                        ResourceLocationArgument.getId(context, "skill")
                                ))))
                .then(Commands.literal("add_xp")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("skill", ResourceLocationArgument.id())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(context -> addExperience(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        ResourceLocationArgument.getId(context, "skill"),
                                                        IntegerArgumentType.getInteger(context, "amount")
                                                )))))));
    }

    private static int showProgress(CommandSourceStack source, ResourceLocation skillId)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (ExperienceService.findSkill(player, skillId).isEmpty()) {
            throw UNKNOWN_SKILL.create(skillId);
        }
        ExperienceService.ProgressView view = ExperienceService.view(player, skillId);
        source.sendSuccess(() -> Component.translatable(
                "command.ability.progress",
                view.skillId().toString(),
                view.level(),
                view.totalXp()
        ), false);
        return view.level();
    }

    private static int addExperience(
            CommandSourceStack source,
            Collection<ServerPlayer> targets,
            ResourceLocation skillId,
            int amount
    ) throws CommandSyntaxException {
        if (targets.stream().findFirst().flatMap(player -> ExperienceService.findSkill(player, skillId)).isEmpty()) {
            throw UNKNOWN_SKILL.create(skillId);
        }
        for (ServerPlayer player : targets) {
            ExperienceService.award(player, skillId, amount);
        }
        source.sendSuccess(() -> Component.translatable(
                "command.ability.add_xp.success",
                amount,
                skillId.toString(),
                targets.size()
        ), true);
        return targets.size();
    }
}
