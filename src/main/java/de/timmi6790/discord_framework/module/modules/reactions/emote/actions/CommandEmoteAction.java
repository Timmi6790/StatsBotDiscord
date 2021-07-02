package de.timmi6790.discord_framework.module.modules.reactions.emote.actions;

import de.timmi6790.discord_framework.DiscordBot;
import de.timmi6790.discord_framework.module.ModuleManager;
import de.timmi6790.discord_framework.module.modules.channel.ChannelDbModule;
import de.timmi6790.discord_framework.module.modules.command_old.AbstractCommand;
import de.timmi6790.discord_framework.module.modules.command_old.CommandCause;
import de.timmi6790.discord_framework.module.modules.command_old.CommandModule;
import de.timmi6790.discord_framework.module.modules.command_old.CommandParameters;
import de.timmi6790.discord_framework.module.modules.user.UserDbModule;
import lombok.Data;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;

@Data
public class CommandEmoteAction implements EmoteAction {
    private final Class<? extends AbstractCommand> commandClass;
    private final Values values;

    public CommandEmoteAction(final Class<? extends AbstractCommand> commandClass, final CommandParameters commandParameters) {
        this.commandClass = commandClass;
        this.values = new Values(
                commandParameters.getArgs(),
                commandParameters.isGuildCommand(),
                commandParameters.getChannelDb().getDiscordId(),
                commandParameters.getGuildDb().getDiscordId(),
                commandParameters.getUserDb().getDiscordId()
        );
    }

    @Override
    public void onEmote(final MessageReactionAddEvent reactionAddEvent) {
        DiscordBot.getInstance().getModuleManager().getModuleOrThrow(CommandModule.class)
                .getCommand(this.commandClass)
                .ifPresent(command -> command.runCommand(this.values.getCommandParameters()));
    }

    @Data
    private static class Values {
        private final String[] args;
        private final boolean guildCommand;
        private final long channelDiscordId;
        private final long guildDiscordId;
        private final long userDiscordId;

        public CommandParameters getCommandParameters() {
            final ModuleManager moduleManager = DiscordBot.getInstance().getModuleManager();
            return new CommandParameters(
                    String.join(" ", this.args),
                    this.args,
                    this.guildCommand,
                    CommandCause.EMOTES,
                    moduleManager.getModuleOrThrow(ChannelDbModule.class).getOrCreate(this.channelDiscordId, this.guildDiscordId),
                    moduleManager.getModuleOrThrow(UserDbModule.class).getOrCreate(this.userDiscordId)
            );
        }
    }
}
