package de.timmi6790.discord_framework.modules.rank;

import de.timmi6790.discord_framework.AbstractIntegrationTest;
import de.timmi6790.discord_framework.DiscordBot;
import de.timmi6790.discord_framework.modules.ModuleManager;
import de.timmi6790.discord_framework.modules.achievement.AchievementModule;
import de.timmi6790.discord_framework.modules.command.CommandModule;
import de.timmi6790.discord_framework.modules.database.DatabaseModule;
import de.timmi6790.discord_framework.modules.event.EventModule;
import de.timmi6790.discord_framework.modules.permisssion.PermissionsModule;
import de.timmi6790.discord_framework.modules.user.UserDbModule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RankModuleTest {
    private static final AtomicInteger RANK_NAME_NUMBER = new AtomicInteger(0);

    private static final RankModule rankModule = spy(new RankModule());
    private static final PermissionsModule permissionsModule = spy(new PermissionsModule());
    private static final UserDbModule userDbModule = spy(new UserDbModule());

    private static String generateRankName() {
        return "RandomRank" + RANK_NAME_NUMBER.getAndIncrement();
    }

    @BeforeAll
    static void setup() {
        final ModuleManager moduleManager = mock(ModuleManager.class);

        final CommandModule commandModule = spy(new CommandModule());
        doNothing().when(commandModule).registerCommands(any(), any());

        final EventModule eventModule = mock(EventModule.class);
        when(moduleManager.getModuleOrThrow(EventModule.class)).thenReturn(eventModule);

        final AchievementModule achievementModule = new AchievementModule();

        when(moduleManager.getModuleOrThrow(PermissionsModule.class)).thenReturn(permissionsModule);
        doReturn(AbstractIntegrationTest.databaseModule).when(moduleManager).getModuleOrThrow(DatabaseModule.class);
        when(moduleManager.getModuleOrThrow(CommandModule.class)).thenReturn(commandModule);
        when(moduleManager.getModuleOrThrow(RankModule.class)).thenReturn(rankModule);
        doReturn(achievementModule).when(moduleManager).getModuleOrThrow(AchievementModule.class);

        try (final MockedStatic<DiscordBot> botMock = mockStatic(DiscordBot.class)) {
            final DiscordBot bot = mock(DiscordBot.class);
            when(bot.getModuleManager()).thenReturn(moduleManager);

            botMock.when(DiscordBot::getInstance).thenReturn(bot);

            permissionsModule.onInitialize();
            userDbModule.onInitialize();
            rankModule.onInitialize();
            achievementModule.onInitialize();
        }
    }

    @Test
    void hasRank() {
        final String rankName = generateRankName();
        rankModule.createRank(rankName);

        final Optional<Rank> rankByName = rankModule.getRank(rankName);
        assertThat(rankByName).isPresent();

        assertThat(rankModule.hasRank(rankByName.get().getDatabaseId())).isTrue();
    }

    @Test
    void getRankId() {
        final String rankName = generateRankName();
        rankModule.createRank(rankName);

        final Optional<Rank> rankByName = rankModule.getRank(rankName);
        assertThat(rankByName).isPresent();

        final Optional<Rank> rankById = rankModule.getRank(rankByName.get().getDatabaseId());
        assertThat(rankById)
                .isPresent()
                .hasValue(rankByName.get());
    }

    @Test
    void getRankByName() {
        final String rankName = generateRankName();
        rankModule.createRank(rankName);

        final Optional<Rank> rank = rankModule.getRank(rankName);
        assertThat(rank).isPresent();
        assertThat(rank.get().getName()).isEqualTo(rankName);
    }

    @Test
    void getRanks() {
        final List<String> rankNames = new ArrayList<>();
        for (int count = 0; 10 > count; count++) {
            rankNames.add(generateRankName());
        }

        for (final String rankName : rankNames) {
            rankModule.createRank(rankName);
        }

        final List<String> foundRankNames = rankModule.getRanks().stream()
                .map(Rank::getName)
                .collect(Collectors.toList());
        assertThat(foundRankNames).containsAll(rankNames);
    }

    @Test
    void createRank() {
        final String rankName = generateRankName();
        assertThat(rankModule.hasRank(rankName)).isFalse();

        final boolean rankCreated = rankModule.createRank(rankName);
        assertThat(rankCreated).isTrue();

        final boolean alreadyCreated = rankModule.createRank(rankName);
        assertThat(alreadyCreated).isFalse();

        final Optional<Rank> rank = rankModule.getRank(rankName);
        assertThat(rank).isPresent();
        assertThat(rank.get().getName()).isEqualTo(rankName);
    }

    @Test
    void deleteRankRank() {
        final String rankName = generateRankName();
        rankModule.createRank(rankName);
        final Optional<Rank> rank = rankModule.getRank(rankName);

        assertThat(rank).isPresent();
        assertThat(rankModule.deleteRank(rank.get())).isTrue();
        assertThat(rankModule.hasRank(rankName)).isFalse();
    }

    @Test
    void deleteRankId() {
        final String rankName = generateRankName();
        rankModule.createRank(rankName);
        final Optional<Rank> rank = rankModule.getRank(rankName);

        assertThat(rank).isPresent();
        assertThat(rankModule.deleteRank(rank.get().getDatabaseId())).isTrue();
        assertThat(rankModule.hasRank(rankName)).isFalse();
    }

    @Test
    void deleteDefaultRank() {
        assertThat(rankModule.deleteRank(1)).isFalse();
    }

    @Test
    void delete_missing_rank() {
        assertThat(rankModule.deleteRank(-1)).isFalse();
    }

    @Test
    void getRank_none_existing() {
        assertThat(rankModule.getRank(generateRankName())).isEmpty();
    }
}