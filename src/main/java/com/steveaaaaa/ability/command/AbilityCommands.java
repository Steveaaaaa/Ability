package com.steveaaaaa.ability.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.steveaaaaa.ability.ability.AbilityService;
import com.steveaaaaa.ability.data.validation.DataDefinitionValidator;
import com.steveaaaaa.ability.data.validation.DataValidationReport;
import com.steveaaaaa.ability.progress.ExperienceService;
import com.steveaaaaa.ability.presentation.AbilityCue;
import com.steveaaaaa.ability.presentation.AbilityPresentationService;
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
    private static final int MAX_VALIDATION_DIAGNOSTICS = 20;
    private static final DynamicCommandExceptionType UNKNOWN_SKILL = new DynamicCommandExceptionType(
            skill -> Component.literal("Unknown skill: " + skill)
    );
    private static final DynamicCommandExceptionType UNKNOWN_ABILITY = new DynamicCommandExceptionType(
            ability -> Component.literal("Unknown ability: " + ability)
    );

    private AbilityCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("fantasypower")
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
                                                ))))))
                .then(Commands.literal("purchase")
                        .then(Commands.argument("ability", ResourceLocationArgument.id())
                                .executes(context -> purchaseAbility(
                                        context.getSource(),
                                        ResourceLocationArgument.getId(context, "ability")
                                ))))
                .then(Commands.literal("rank")
                        .then(Commands.argument("ability", ResourceLocationArgument.id())
                                .executes(context -> showRank(
                                        context.getSource(),
                                        ResourceLocationArgument.getId(context, "ability")
                                ))))
                .then(Commands.literal("validate")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> validateData(context.getSource())))
                .then(Commands.literal("presentation_preview")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("ability", ResourceLocationArgument.id())
                                .then(Commands.argument("cue", ResourceLocationArgument.id())
                                        .executes(context -> previewPresentation(
                                                context.getSource(),
                                                ResourceLocationArgument.getId(context, "ability"),
                                                ResourceLocationArgument.getId(context, "cue")
                                        ))))));
    }

    private static int showProgress(CommandSourceStack source, ResourceLocation skillId)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (ExperienceService.findSkill(player, skillId).isEmpty()) {
            throw UNKNOWN_SKILL.create(skillId);
        }
        ExperienceService.ProgressView view = ExperienceService.view(player, skillId);
        source.sendSuccess(() -> Component.translatable(
                "command.fantasypower.progress",
                view.skillId().toString(),
                view.level(),
                view.totalXp(),
                view.availableSkillPoints()
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
                "command.fantasypower.add_xp.success",
                amount,
                skillId.toString(),
                targets.size()
        ), true);
        return targets.size();
    }

    private static int purchaseAbility(CommandSourceStack source, ResourceLocation abilityId)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AbilityService.PurchaseResult result = AbilityService.purchase(player, abilityId);
        switch (result.status()) {
            case SUCCESS -> source.sendSuccess(() -> Component.translatable(
                    "command.fantasypower.purchase.success",
                    result.abilityId().toString(),
                    result.required(),
                    result.actual()
            ), false);
            case UNKNOWN_ABILITY -> throw UNKNOWN_ABILITY.create(abilityId);
            case ALREADY_PURCHASED -> source.sendFailure(Component.translatable(
                    "command.fantasypower.purchase.already_purchased",
                    abilityId.toString()
            ));
            case MAX_RANK -> source.sendFailure(Component.translatable(
                    "command.fantasypower.purchase.max_rank",
                    abilityId.toString()
            ));
            case SKILL_LEVEL_TOO_LOW -> source.sendFailure(Component.translatable(
                    "command.fantasypower.purchase.skill_level_too_low",
                    result.required(),
                    result.actual()
            ));
            case NOT_ENOUGH_SKILL_POINTS -> source.sendFailure(Component.translatable(
                    "command.fantasypower.purchase.not_enough_points",
                    result.required(),
                    result.actual()
            ));
            case REQUIREMENT_NOT_MET -> source.sendFailure(Component.translatable(
                    "command.fantasypower.purchase.requirement_not_met",
                    result.detail()
            ));
            case INVALID_DEFINITION -> source.sendFailure(Component.translatable(
                    "command.fantasypower.purchase.invalid_definition",
                    result.detail()
            ));
        }
        return result.successful() ? 1 : 0;
    }

    private static int showRank(CommandSourceStack source, ResourceLocation abilityId)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (AbilityService.findAbility(player, abilityId).isEmpty()) {
            throw UNKNOWN_ABILITY.create(abilityId);
        }
        AbilityService.RankView view = AbilityService.rank(player, abilityId);
        source.sendSuccess(() -> Component.translatable(
                "command.fantasypower.rank",
                view.abilityId().toString(),
                view.rank(),
                view.maxRank(),
                view.skillLevel(),
                view.purchased(),
                view.active()
        ), false);
        return view.rank();
    }

    private static int validateData(CommandSourceStack source) {
        DataValidationReport report = DataDefinitionValidator.validate(source.getServer());
        if (report.valid()) {
            source.sendSuccess(() -> Component.translatable("command.fantasypower.validate.success"), false);
            return 1;
        }

        source.sendFailure(Component.translatable(
                "command.fantasypower.validate.failure",
                report.errorCount()
        ));
        report.diagnostics().stream().limit(MAX_VALIDATION_DIAGNOSTICS).forEach(diagnostic ->
                source.sendFailure(Component.literal("- " + diagnostic.displayText()))
        );
        int omitted = report.errorCount() - MAX_VALIDATION_DIAGNOSTICS;
        if (omitted > 0) {
            source.sendFailure(Component.translatable("command.fantasypower.validate.truncated", omitted));
        }
        return 0;
    }

    private static int previewPresentation(
            CommandSourceStack source,
            ResourceLocation abilityId,
            ResourceLocation cueId
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AbilityPresentationService.sendToPlayer(player, AbilityCue.pulse(
                abilityId,
                cueId,
                player.getId(),
                -1,
                player.position().add(0.0D, player.getBbHeight() * 0.5D, 0.0D),
                player.getLookAngle(),
                0,
                player.getRandom().nextLong()
        ));
        source.sendSuccess(() -> Component.translatable(
                "command.fantasypower.presentation_preview",
                abilityId.toString(),
                cueId.toString()
        ), false);
        return 1;
    }
}
