package de.timmi6790.discord_framework.modules.achievement;

import de.timmi6790.discord_framework.DiscordBot;
import de.timmi6790.discord_framework.modules.database.DatabaseModule;
import de.timmi6790.discord_framework.modules.user.UserDb;
import lombok.Data;

@Data
public abstract class AbstractAchievement {
    private static final String ACHIEVEMENT_NAME = "achievementName";

    private static final String GET_ACHIEVEMENT_ID = "SELECT id FROM `achievement` WHERE achievement_name = :achievementName LIMIT 1;";
    private static final String INSERT_NEW_ACHIEVEMENT = "INSERT INTO achievement(achievement_name) VALUES(:achievementName);";

    private final int databaseId;
    private final String name;
    private final String internalName;

    public AbstractAchievement(final String name, final String internalName) {
        this.name = name;
        this.internalName = internalName;
        this.databaseId = this.getStatDbId();
    }

    private int getStatDbId() {
        return DiscordBot.getInstance().getModuleManager().getModuleOrThrow(DatabaseModule.class).getJdbi().withHandle(handle ->
                handle.createQuery(GET_ACHIEVEMENT_ID)
                        .bind(ACHIEVEMENT_NAME, this.getInternalName())
                        .mapTo(int.class)
                        .findFirst()
                        .orElseGet(() -> {
                            handle.createUpdate(INSERT_NEW_ACHIEVEMENT)
                                    .bind(ACHIEVEMENT_NAME, this.getInternalName())
                                    .execute();

                            return handle.createQuery(GET_ACHIEVEMENT_ID)
                                    .bind(ACHIEVEMENT_NAME, this.getInternalName())
                                    .mapTo(int.class)
                                    .first();
                        })
        );
    }

    public void unlockAchievement(final UserDb userDb) {
        userDb.grantAchievement(this);
    }

    public abstract void onUnlock(UserDb userDb);
}
