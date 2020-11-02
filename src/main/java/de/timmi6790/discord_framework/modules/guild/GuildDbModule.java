package de.timmi6790.discord_framework.modules.guild;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.timmi6790.discord_framework.modules.AbstractModule;
import de.timmi6790.discord_framework.modules.database.DatabaseModule;
import de.timmi6790.discord_framework.modules.guild.repository.GuildDbRepository;
import de.timmi6790.discord_framework.modules.guild.repository.GuildDbRepositoryMysql;
import de.timmi6790.discord_framework.modules.permisssion.PermissionsModule;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@EqualsAndHashCode(callSuper = true)
@Getter
public class GuildDbModule extends AbstractModule {
    private final Cache<Long, GuildDb> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    private GuildDbRepository guildDbRepository;

    public GuildDbModule() {
        super("Guild");

        this.addDependenciesAndLoadAfter(
                DatabaseModule.class,
                PermissionsModule.class
        );
    }

    @Override
    public void onInitialize() {
        this.guildDbRepository = new GuildDbRepositoryMysql(this);
    }

    protected GuildDb create(final long discordId) {
        // Make sure that the guild is not present
        final Optional<GuildDb> guildDbOpt = this.get(discordId);
        if (guildDbOpt.isPresent()) {
            return guildDbOpt.get();
        }

        final GuildDb guildDb = this.getGuildDbRepository().create(discordId);
        this.getCache().put(discordId, guildDb);
        return guildDb;
    }

    public Optional<GuildDb> get(final long discordId) {
        final GuildDb guildDbCache = this.getCache().getIfPresent(discordId);
        if (guildDbCache != null) {
            return Optional.of(guildDbCache);
        }

        final Optional<GuildDb> guildDbOpt = this.getGuildDbRepository().get(discordId);
        guildDbOpt.ifPresent(userDb -> this.getCache().put(discordId, userDb));

        return guildDbOpt;
    }

    public GuildDb getOrCreate(final long discordId) {
        return this.get(discordId).orElseGet(() -> this.create(discordId));
    }
}
