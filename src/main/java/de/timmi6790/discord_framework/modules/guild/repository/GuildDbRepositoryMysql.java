package de.timmi6790.discord_framework.modules.guild.repository;

import de.timmi6790.discord_framework.modules.database.DatabaseModule;
import de.timmi6790.discord_framework.modules.guild.GuildDb;
import de.timmi6790.discord_framework.modules.guild.GuildDbModule;
import org.jdbi.v3.core.Jdbi;

import java.util.Optional;

public class GuildDbRepositoryMysql implements GuildDbRepository {
    private static final String GET_GUILD = "SELECT guild.id, discordId, banned, GROUP_CONCAT(alias.alias) aliases FROM guild " +
            "LEFT JOIN guild_command_alias alias ON alias.guild_id = guild.id " +
            "WHERE guild.discordId = :discordId " +
            "LIMIT 1;";

    private static final String INSERT_GUILD = "INSERT INTO guild(discordId) VALUES (:discordId);";

    private final Jdbi database;

    public GuildDbRepositoryMysql(final GuildDbModule module) {
        this.database = module.getModuleOrThrow(DatabaseModule.class).getJdbi();
        this.database.registerRowMapper(GuildDb.class, new GuildDbMapper());
    }

    @Override
    public GuildDb create(final long discordId) {
        this.database.useHandle(handle ->
                handle.createUpdate(INSERT_GUILD)
                        .bind("discordId", discordId)
                        .execute()
        );

        // Should never throw
        return this.get(discordId).orElseThrow(RuntimeException::new);
    }

    @Override
    public Optional<GuildDb> get(final long discordId) {
        return this.database.withHandle(handle ->
                handle.createQuery(GET_GUILD)
                        .bind("discordId", discordId)
                        .mapTo(GuildDb.class)
                        .findFirst()
        );
    }
}