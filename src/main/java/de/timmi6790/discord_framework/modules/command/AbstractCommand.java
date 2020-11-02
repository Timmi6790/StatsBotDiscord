package de.timmi6790.discord_framework.modules.command;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import de.timmi6790.commons.utilities.EnumUtilities;
import de.timmi6790.commons.utilities.StringUtilities;
import de.timmi6790.discord_framework.DiscordBot;
import de.timmi6790.discord_framework.datatypes.builders.MultiEmbedBuilder;
import de.timmi6790.discord_framework.modules.AbstractModule;
import de.timmi6790.discord_framework.modules.ModuleManager;
import de.timmi6790.discord_framework.modules.command.commands.HelpCommand;
import de.timmi6790.discord_framework.modules.command.events.CommandExecutionEvent;
import de.timmi6790.discord_framework.modules.command.exceptions.CommandReturnException;
import de.timmi6790.discord_framework.modules.command.property.CommandProperty;
import de.timmi6790.discord_framework.modules.command.property.properties.ExampleCommandsCommandProperty;
import de.timmi6790.discord_framework.modules.command.property.properties.MinArgCommandProperty;
import de.timmi6790.discord_framework.modules.command.property.properties.RequiredDiscordBotPermsCommandProperty;
import de.timmi6790.discord_framework.modules.database.DatabaseModule;
import de.timmi6790.discord_framework.modules.emote_reaction.emotereactions.AbstractEmoteReaction;
import de.timmi6790.discord_framework.modules.emote_reaction.emotereactions.CommandEmoteReaction;
import de.timmi6790.discord_framework.modules.event.EventModule;
import de.timmi6790.discord_framework.modules.permisssion.PermissionsModule;
import de.timmi6790.discord_framework.modules.rank.Rank;
import de.timmi6790.discord_framework.modules.rank.RankModule;
import de.timmi6790.discord_framework.modules.user.UserDb;
import de.timmi6790.discord_framework.utilities.DataUtilities;
import de.timmi6790.discord_framework.utilities.discord.DiscordEmotes;
import de.timmi6790.discord_framework.utilities.discord.DiscordMessagesUtilities;
import de.timmi6790.discord_framework.utilities.sentry.BreadcrumbBuilder;
import de.timmi6790.discord_framework.utilities.sentry.SentryEventBuilder;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jdbi.v3.core.Jdbi;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Data
public abstract class AbstractCommand {
    protected static final EnumSet<Permission> MINIMUM_DISCORD_PERMISSIONS = EnumSet.of(Permission.MESSAGE_WRITE, Permission.MESSAGE_EMBED_LINKS);
    private static final String ERROR = "Error";
    private static final Pattern DISCORD_USER_ID_PATTERN = Pattern.compile("^(<@[!&])?(\\d*)>?$");
    @Getter
    private static final int COMMAND_USER_RATE_LIMIT = 10;
    @Getter
    private static final LoadingCache<Long, AtomicInteger> commandSpamCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build(key -> new AtomicInteger(0));

    @Getter(lazy = true)
    private static final Jdbi database = getModuleManager().getModuleOrThrow(DatabaseModule.class).getJdbi();
    @Getter(lazy = true)
    private static final CommandModule commandModule = getModuleManager().getModuleOrThrow(CommandModule.class);
    @Getter(lazy = true)
    private static final PermissionsModule permissionsModule = getModuleManager().getModuleOrThrow(PermissionsModule.class);
    @Getter(lazy = true)
    private static final EventModule eventModule = getModuleManager().getModuleOrThrow(EventModule.class);
    @Getter(lazy = true)
    private static final RankModule rankModule = getModuleManager().getModuleOrThrow(RankModule.class);
    @Getter(lazy = true)
    private static final JDA discord = DiscordBot.getInstance().getDiscord();

    private final String name;
    private final String syntax;
    private final String[] aliasNames;
    private Map<Class<? extends CommandProperty<?>>, CommandProperty<?>> propertiesMap = new HashMap<>();
    private int dbId = -1;
    private Class<? extends AbstractModule> registeredModule;
    private int permissionId = -1;
    private String category;
    private String description;

    protected AbstractCommand(@NonNull final String name,
                              @NonNull final String category,
                              @NonNull final String description,
                              @NonNull final String syntax,
                              final String... aliasNames) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.syntax = syntax;
        this.aliasNames = aliasNames.clone();
    }

    protected static ModuleManager getModuleManager() {
        return DiscordBot.getInstance().getModuleManager();
    }

    // Other
    public static boolean hasRequiredDiscordPerms(@NonNull final CommandParameters commandParameters,
                                                  @NonNull final Set<Permission> requiredPermissions) {
        if (commandParameters.isGuildCommand()) {
            final EnumSet<Permission> wantedDiscordPerms = EnumSet.copyOf(MINIMUM_DISCORD_PERMISSIONS);
            wantedDiscordPerms.addAll(requiredPermissions);

            final Set<Permission> permissions = commandParameters.getDiscordPermissions();
            wantedDiscordPerms.removeIf(permissions::contains);

            if (!wantedDiscordPerms.isEmpty()) {
                final StringJoiner perms = new StringJoiner(",");
                for (final Permission permission : wantedDiscordPerms) {
                    perms.add(MarkdownUtil.monospace(permission.getName()));
                }

                DiscordMessagesUtilities.sendPrivateMessage(
                        commandParameters.getUser(),
                        DiscordMessagesUtilities.getEmbedBuilder(commandParameters)
                                .setTitle("Missing Permission")
                                .setDescription("The bot is missing " + perms + " permission(s).")
                );

                return false;
            }
        }

        return true;
    }

    public static boolean isUserBanned(@NonNull final CommandParameters commandParameters) {
        if (commandParameters.getUserDb().isBanned()) {
            DiscordMessagesUtilities.sendPrivateMessage(
                    commandParameters.getUser(),
                    DiscordMessagesUtilities.getEmbedBuilder(commandParameters)
                            .setTitle("You are banned")
                            .setDescription("You are banned from using this bot.")
            );
            return true;
        }
        return false;
    }

    public static boolean isServerBanned(@NonNull final CommandParameters commandParameters) {
        if (commandParameters.getChannelDb().getGuildDb().isBanned()) {
            DiscordMessagesUtilities.sendMessageTimed(
                    commandParameters.getLowestMessageChannel(),
                    DiscordMessagesUtilities.getEmbedBuilder(commandParameters)
                            .setTitle("Banned Server")
                            .setDescription("This server is banned from using this bot."),
                    90
            );

            return true;
        }
        return false;
    }

    public static void sendEmoteMessage(@NonNull final CommandParameters commandParameters,
                                        @NonNull final MultiEmbedBuilder embedBuilder,
                                        @NonNull final Map<String, AbstractEmoteReaction> emotes) {
        DiscordMessagesUtilities.sendEmoteMessage(commandParameters, embedBuilder, emotes);
    }

    protected static void sendEmoteMessage(@NonNull final CommandParameters commandParameters,
                                           @NonNull final String title,
                                           @NonNull final String description,
                                           @NonNull final Map<String, AbstractEmoteReaction> emotes) {
        sendEmoteMessage(
                commandParameters,
                DiscordMessagesUtilities.getEmbedBuilder(commandParameters)
                        .setTitle(title)
                        .setDescription(description),
                emotes
        );
    }

    protected MultiEmbedBuilder getEmbedBuilder(@NonNull final CommandParameters commandParameters) {
        return DiscordMessagesUtilities.getEmbedBuilder(commandParameters);
    }

    protected void sendMissingPermissionMessage(@NonNull final CommandParameters commandParameters) {
        this.sendTimedMessage(
                commandParameters,
                this.getEmbedBuilder(commandParameters)
                        .setTitle("Missing perms")
                        .setDescription("You don't have the permissions to run this command."),
                90
        );
    }

    protected void sendTimedMessage(@NonNull final CommandParameters commandParameters,
                                    @NonNull final MultiEmbedBuilder embedBuilder,
                                    final int deleteTime) {
        DiscordMessagesUtilities.sendMessageTimed(commandParameters.getLowestMessageChannel(), embedBuilder, deleteTime);
    }

    protected void sendMessage(@NonNull final CommandParameters commandParameters,
                               @NonNull final MultiEmbedBuilder embedBuilder) {
        DiscordMessagesUtilities.sendMessage(commandParameters.getLowestMessageChannel(), embedBuilder);
    }

    protected void sendMessage(@NonNull final CommandParameters commandParameters,
                               @NonNull final MultiEmbedBuilder embedBuilder,
                               @NonNull final Consumer<Message> success) {
        DiscordMessagesUtilities.sendMessage(commandParameters.getLowestMessageChannel(), embedBuilder, success);
    }

    protected void throwInvalidArg(@NonNull final CommandParameters commandParameters,
                                   final int argPos,
                                   @NonNull final String argName) {
        this.sendTimedMessage(
                commandParameters,
                this.getEmbedBuilder(commandParameters)
                        .setTitle("Invalid " + argName)
                        .setDescription(MarkdownUtil.monospace(commandParameters.getArgs()[argPos]) + " is not a valid " + MarkdownUtil.bold(argName.toLowerCase()) + "."),
                120
        );

        throw new CommandReturnException(CommandResult.INVALID_ARGS);
    }

    protected abstract CommandResult onCommand(CommandParameters commandParameters);

    private CommandResult executeSave(@NonNull final CommandParameters commandParameters) {
        try {
            return this.onCommand(commandParameters);
        } catch (final CommandReturnException e) {
            e.getEmbedBuilder().ifPresent(embedBuilder -> this.sendTimedMessage(commandParameters, embedBuilder, 90));
            return e.getCommandResult();

        } catch (final Exception e) {
            DiscordBot.getLogger().error(e);
            this.sendErrorMessage(commandParameters, "Unknown");

            // Sentry error
            Sentry.captureEvent(new SentryEventBuilder()
                    .addBreadcrumb(new BreadcrumbBuilder()
                            .setCategory("Command")
                            .setData("channelId", String.valueOf(commandParameters.getChannelDb().getDatabaseId()))
                            .setData("userId", String.valueOf(commandParameters.getUserDb().getDatabaseId()))
                            .setData("args", Arrays.toString(commandParameters.getArgs()))
                            .setData("command", this.name)
                            .build())
                    .setLevel(SentryLevel.ERROR)
                    .setMessage("Command Exception")
                    .setLogger(AbstractCommand.class.getName())
                    .setThrowable(e)
                    .build());

            return CommandResult.ERROR;
        }
    }

    public void runCommand(final @NonNull CommandParameters commandParameters) {
        if (getCommandSpamCache().get(commandParameters.getUserDb().getDiscordId()).get() > COMMAND_USER_RATE_LIMIT) {
            return;
        }

        // Ban checks
        if (isUserBanned(commandParameters) || isServerBanned(commandParameters)) {
            return;
        }

        final EnumSet<Permission> requiredDiscordPerms = this.getPropertyValueOrDefault(RequiredDiscordBotPermsCommandProperty.class, EnumSet.noneOf(Permission.class));
        if (!hasRequiredDiscordPerms(commandParameters, requiredDiscordPerms)) {
            return;
        }

        if (!this.hasPermission(commandParameters)) {
            this.sendMissingPermissionMessage(commandParameters);
            return;
        }

        // Property checks
        for (final CommandProperty<?> commandProperty : this.getPropertiesMap().values()) {
            if (!commandProperty.onCommandExecution(this, commandParameters)) {
                return;
            }
        }

        // Command pre event
        AbstractCommand.getEventModule().executeEvent(new CommandExecutionEvent.Pre(this, commandParameters));

        // Run command
        getCommandSpamCache().get(commandParameters.getUserDb().getDiscordId()).incrementAndGet();
        final CommandResult commandResult = this.executeSave(commandParameters);

        // Command post event
        AbstractCommand.getEventModule().executeEvent(new CommandExecutionEvent.Post(
                this,
                commandParameters,
                commandResult == null ? CommandResult.MISSING : commandResult
        ));
    }

    public boolean hasPermission(@NonNull final CommandParameters commandParameters) {
        // Properties Check
        for (final CommandProperty<?> commandProperty : this.getPropertiesMap().values()) {
            if (!commandProperty.onPermissionCheck(this, commandParameters)) {
                return false;
            }
        }

        // Permission check
        return this.getPermissionId() == -1 || commandParameters.getUserDb().getAllPermissionIds().contains(this.getPermissionId());
    }

    protected void addProperty(final @NonNull CommandProperty<?> property) {
        this.getPropertiesMap().put((Class<? extends CommandProperty<?>>) property.getClass(), property);
    }

    protected void addProperties(final CommandProperty<?>... properties) {
        for (final CommandProperty<?> property : properties) {
            this.addProperty(property);
        }
    }

    public <V> V getPropertyValueOrDefault(@NonNull final Class<? extends CommandProperty<V>> propertyClass,
                                           @Nullable final V defaultValue) {
        final CommandProperty<V> property = (CommandProperty<V>) this.propertiesMap.get(propertyClass);
        if (property != null) {
            return property.getValue();
        }
        return defaultValue;
    }

    public String[] getAliasNames() {
        return this.aliasNames.clone();
    }

    protected void setPermission(@NonNull final String permission) {
        this.permissionId = AbstractCommand.getPermissionsModule().addPermission(permission);
    }


    // Old Stuff
    public List<String> getFormattedExampleCommands() {
        final String mainCommand = AbstractCommand.getCommandModule().getMainCommand();
        return Arrays.stream(this.getPropertyValueOrDefault(ExampleCommandsCommandProperty.class, new String[0]))
                .map(exampleCommand -> mainCommand + " " + this.name + " " + exampleCommand)
                .collect(Collectors.toList());
    }

    public void sendMissingArgsMessage(@NonNull final CommandParameters commandParameters) {
        this.sendMissingArgsMessage(commandParameters, this.getPropertyValueOrDefault(MinArgCommandProperty.class, 0));
    }

    protected void sendMissingArgsMessage(@NonNull final CommandParameters commandParameters,
                                          final int requiredSyntaxLength) {
        final String[] args = commandParameters.getArgs();
        final String[] splitSyntax = this.syntax.split(" ");

        final StringJoiner requiredSyntax = new StringJoiner(" ");
        for (int index = 0; Math.min(requiredSyntaxLength, splitSyntax.length) > index; index++) {
            requiredSyntax.add(args.length > index ? args[index] : MarkdownUtil.bold(splitSyntax[index]));
        }

        final String exampleCommands = String.join("\n", this.getFormattedExampleCommands());
        this.sendTimedMessage(
                commandParameters,
                this.getEmbedBuilder(commandParameters).setTitle("Missing Args")
                        .setDescription("You are missing a few required arguments.\nIt is required that you enter the bold arguments.")
                        .addField("Required Syntax", requiredSyntax.toString(), false)
                        .addField("Command Syntax", this.getSyntax(), false)
                        .addField("Example Commands", exampleCommands, false, !exampleCommands.isEmpty()),
                90
        );
    }

    protected void sendErrorMessage(@NonNull final CommandParameters commandParameters,
                                    @NonNull final String error) {
        this.sendTimedMessage(
                commandParameters,
                this.getEmbedBuilder(commandParameters).setTitle("Something went wrong")
                        .setDescription("Something went wrong while executing this command.")
                        .addField("Command", this.getName(), false)
                        .addField("Args", String.join(" ", commandParameters.getArgs()), false)
                        .addField(ERROR, error, false),
                90
        );
    }

    protected void sendHelpMessage(@NonNull final CommandParameters commandParameters,
                                   @NonNull final String userArg,
                                   final int argPos,
                                   @NonNull final String argName,
                                   @Nullable final AbstractCommand command,
                                   @Nullable final String[] newArgs,
                                   @NonNull final List<String> similarNames) {
        final Map<String, AbstractEmoteReaction> emotes = new LinkedHashMap<>();
        final StringBuilder helpDescription = new StringBuilder();
        helpDescription.append(MarkdownUtil.monospace(userArg)).append(" is not a valid ").append(argName).append(".\n");

        if (similarNames.isEmpty() && command != null) {
            helpDescription.append("Use the ").append(MarkdownUtil.bold(AbstractCommand.getCommandModule().getMainCommand() + " " + command.getName() + " " + String.join(" ", newArgs)))
                    .append(" command or click the ").append(DiscordEmotes.FOLDER.getEmote()).append(" emote to see all ").append(argName).append("s.");

        } else {
            helpDescription.append("Is it possible that you wanted to write?\n\n");

            for (int index = 0; similarNames.size() > index; index++) {
                final String emote = DiscordEmotes.getNumberEmote(index + 1).getEmote();

                helpDescription.append(emote).append(" ").append(MarkdownUtil.bold(similarNames.get(index))).append("\n");

                final String[] newArgsParameter = commandParameters.getArgs();
                newArgsParameter[argPos] = similarNames.get(index);
                final CommandParameters newCommandParameters = CommandParameters.of(commandParameters, newArgsParameter);
                emotes.put(emote, new CommandEmoteReaction(this, newCommandParameters));
            }

            if (command != null) {
                helpDescription.append("\n").append(DiscordEmotes.FOLDER.getEmote()).append(MarkdownUtil.bold("All " + argName + "s"));
            }
        }

        if (command != null) {
            final CommandParameters newCommandParameters = CommandParameters.of(commandParameters, newArgs);
            emotes.put(DiscordEmotes.FOLDER.getEmote(), new CommandEmoteReaction(command, newCommandParameters));
        }

        sendEmoteMessage(commandParameters, "Invalid " + StringUtilities.capitalize(argName), helpDescription.toString(), emotes);
    }

    // Checks
    protected void checkArgLength(@NonNull final CommandParameters commandParameters,
                                  final int length) {
        if (length > commandParameters.getArgs().length) {
            this.sendMissingArgsMessage(commandParameters, Math.max(this.getPropertyValueOrDefault(MinArgCommandProperty.class, length), length));
            throw new CommandReturnException(CommandResult.MISSING_ARGS);
        }
    }

    // Args
    public User getDiscordUserThrow(@NonNull final CommandParameters commandParameters,
                                    final int argPos) {
        final String discordUserName = commandParameters.getArgs()[argPos];
        final Matcher userIdMatcher = DISCORD_USER_ID_PATTERN.matcher(discordUserName);
        if (userIdMatcher.find()) {
            final User user = UserDb.getUSER_CACHE().get(Long.valueOf(userIdMatcher.group(2)));
            if (user != null) {
                return user;
            }
        }

        throw new CommandReturnException(
                DiscordMessagesUtilities.getEmbedBuilder(commandParameters)
                        .setTitle("Invalid User")
                        .setDescription(MarkdownUtil.monospace(discordUserName) + " is not a valid discord user.")
        );
    }

    public String getFromListIgnoreCaseThrow(@NonNull final CommandParameters commandParameters,
                                             final int argPos,
                                             @NonNull final List<String> possibleArguments) {
        final String userArg = commandParameters.getArgs()[argPos];
        final Optional<String> arg = possibleArguments.stream()
                .filter(possibleArg -> possibleArg.equalsIgnoreCase(userArg))
                .findAny();

        if (arg.isPresent()) {
            return arg.get();
        }

        AbstractCommand.this.sendHelpMessage(commandParameters, userArg, argPos, "argument", null, null, possibleArguments);
        throw new CommandReturnException();
    }

    public <E extends Enum> E getFromEnumIgnoreCaseThrow(@NonNull final CommandParameters commandParameters,
                                                         final int argPos,
                                                         @NonNull final E[] enumValue) {
        final String userArg = commandParameters.getArgs()[argPos];
        final Optional<E> arg = EnumUtilities.getIgnoreCase(userArg, enumValue);
        if (arg.isPresent()) {
            return arg.get();
        }

        this.sendHelpMessage(commandParameters,
                userArg,
                argPos,
                "argument",
                null,
                null,
                EnumUtilities.getPrettyNames(enumValue)
        );
        throw new CommandReturnException();
    }

    public AbstractCommand getCommandThrow(@NonNull final CommandParameters commandParameters,
                                           final int argPos) {
        final String commandName = commandParameters.getArgs()[argPos];
        final Optional<AbstractCommand> command = AbstractCommand.getCommandModule().getCommand(commandName);
        if (command.isPresent()) {
            return command.get();
        }

        final List<AbstractCommand> similarCommands = DataUtilities.getSimilarityList(
                commandName,
                AbstractCommand.getCommandModule().getCommandsWithPerms(commandParameters),
                AbstractCommand::getName,
                0.6,
                3
        );
        if (!similarCommands.isEmpty() && commandParameters.getUserDb().hasAutoCorrection()) {
            return similarCommands.get(0);
        }

        AbstractCommand.this.sendHelpMessage(
                commandParameters,
                commandName,
                argPos,
                "command",
                AbstractCommand.getCommandModule().getCommand(HelpCommand.class).orElse(null),
                new String[0],
                similarCommands.stream().map(AbstractCommand::getName).collect(Collectors.toList())
        );
        throw new CommandReturnException();
    }

    public Rank getRankThrow(@NonNull final CommandParameters commandParameters,
                             final int position) {
        final String userInput = commandParameters.getArgs()[position];

        return AbstractCommand.getRankModule().getRanks()
                .stream()
                .filter(rank -> rank.getName().equalsIgnoreCase(userInput))
                .findAny()
                .orElseThrow(() -> new CommandReturnException(
                        DiscordMessagesUtilities.getEmbedBuilder(commandParameters)
                                .setTitle(ERROR)
                                .setDescription(MarkdownUtil.monospace(userInput) + " is not a valid rank.")
                ));
    }

    public int getPermissionIdThrow(@NonNull final CommandParameters commandParameters,
                                    final int argPos) {
        final String permArg = commandParameters.getArgs()[argPos];
        final Optional<AbstractCommand> commandOpt = AbstractCommand.getCommandModule().getCommand(permArg);

        if (commandOpt.isPresent()) {
            final AbstractCommand command = commandOpt.get();
            if (command.getPermissionId() == -1) {
                throw new CommandReturnException(
                        DiscordMessagesUtilities.getEmbedBuilder(commandParameters)
                                .setTitle(ERROR)
                                .setDescription(MarkdownUtil.monospace(command.getName()) + " command has no permission.")
                );
            }

            return command.getPermissionId();
        } else {
            return AbstractCommand.getPermissionsModule().getPermissionId(permArg)
                    .orElseThrow(() -> new CommandReturnException(
                            DiscordMessagesUtilities.getEmbedBuilder(commandParameters)
                                    .setTitle(ERROR)
                                    .setDescription(MarkdownUtil.monospace(permArg) + " is not a valid permission.")
                    ));
        }
    }
}
