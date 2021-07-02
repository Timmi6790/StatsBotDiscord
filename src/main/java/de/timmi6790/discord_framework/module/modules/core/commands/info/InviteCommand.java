package de.timmi6790.discord_framework.module.modules.core.commands.info;

import de.timmi6790.discord_framework.module.modules.command_old.AbstractCommand;
import de.timmi6790.discord_framework.module.modules.command_old.CommandParameters;
import de.timmi6790.discord_framework.module.modules.command_old.CommandResult;
import lombok.EqualsAndHashCode;

/**
 * Invite command.
 */
@EqualsAndHashCode(callSuper = true)
public class InviteCommand extends AbstractCommand {
    /**
     * The Invite url.
     */
    private final String inviteUrl;

    /**
     * Instantiates a new Invite command.
     *
     * @param inviteUrl the invite url
     */
    public InviteCommand(final String inviteUrl) {
        super("invite", "Info", "Invite me.", "", "iv");

        this.inviteUrl = inviteUrl;
    }

    @Override
    protected CommandResult onCommand(final CommandParameters commandParameters) {
        this.sendTimedMessage(
                commandParameters,
                "Invite Link",
                "[Click Me!](" + this.inviteUrl + ")"
        );
        return CommandResult.SUCCESS;
    }
}
