package de.timmi6790.statsbotdiscord.modules.mineplexstats.statsapi;

import com.google.gson.Gson;
import de.timmi6790.statsbotdiscord.modules.mineplexstats.statsapi.models.ResponseModel;
import de.timmi6790.statsbotdiscord.modules.mineplexstats.statsapi.models.errors.ErrorModel;
import de.timmi6790.statsbotdiscord.modules.mineplexstats.statsapi.models.java.*;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class MpStatsRestApiClient {
    private static final String BASE_URL = "http://127.0.0.1:8001/"; // "https://mpstats2.timmi6790.de/";

    private static final int TIMEOUT = 6_000;
    private static final String USER_AGENT = "MpStatsRestApiClient-Java";
    private final static ErrorModel UNKNOWN_ERROR_RESPONSE_MODEL = new ErrorModel(-1, "Unknown Error");

    private final Gson gson = new Gson();

    public ResponseModel parseHttpResponse(final HttpResponse<JsonNode> response, final Class<? extends ResponseModel> clazz) {
        if (!response.isSuccess()) {
            return UNKNOWN_ERROR_RESPONSE_MODEL;
        }

        final JSONObject jsonObject = response.getBody().getObject();
        if (!jsonObject.getBoolean("success")) {
            return this.gson.fromJson(jsonObject.toString(), ErrorModel.class);
        }

        return this.gson.fromJson(jsonObject.toString(), clazz);
    }

    public ResponseModel getJavaGames() {
        final HttpResponse<JsonNode> response = Unirest.get(MpStatsRestApiClient.BASE_URL + "java/leaderboards/games")
                .header("User-Agent", USER_AGENT)
                .connectTimeout(TIMEOUT)
                .asJson();

        if (!response.isSuccess()) {
            return UNKNOWN_ERROR_RESPONSE_MODEL;
        }

        final JSONObject jsonObject = response.getBody().getObject();
        if (!jsonObject.getBoolean("success")) {
            return this.gson.fromJson(jsonObject.toString(), ErrorModel.class);
        }

        final Map<String, JavaGame> parsedGames = new HashMap<>();

        final JSONObject gamesObject = jsonObject.getJSONObject("games");
        for (final Iterator<String> it = gamesObject.keys(); it.hasNext(); ) {
            final String gameName = it.next();
            final JSONObject game = gamesObject.getJSONObject(gameName);

            final Map<String, JavaStat> stats = new HashMap<>();

            final JSONObject statsObject = game.getJSONObject("stats");
            for (final Iterator<String> iter = statsObject.keys(); iter.hasNext(); ) {
                final String statName = iter.next();
                final JSONObject stat = statsObject.getJSONObject(statName);

                final Map<String, JavaBoard> boards = new HashMap<>();

                final JSONObject boardsObject = stat.getJSONObject("boards");
                for (final Iterator<String> iterator = boardsObject.keys(); iterator.hasNext(); ) {
                    final String boardName = iterator.next();
                    final JSONObject board = boardsObject.getJSONObject(boardName);

                    boards.put(
                            boardName.toLowerCase(),
                            new JavaBoard(
                                    board.getString("board"),
                                    this.gson.fromJson(board.getJSONArray("aliasNames").toString(), String[].class)
                            )
                    );

                }

                stats.put(
                        statName.toLowerCase(),
                        new JavaStat(
                                stat.getString("stat"),
                                this.gson.fromJson(stat.getJSONArray("aliasNames").toString(), String[].class),
                                stat.getString("prettyStat"),
                                stat.getString("description"),
                                boards
                        )
                );
            }

            parsedGames.put(
                    gameName.toLowerCase(),
                    new JavaGame(
                            game.getString("game"),
                            this.gson.fromJson(game.getJSONArray("aliasNames").toString(), String[].class),
                            game.getString("category"),
                            game.getString("wikiUrl"),
                            game.getString("description"),
                            stats
                    )
            );
        }

        return new JavaGamesModel(parsedGames);
    }

    public ResponseModel getJavaPlayerStats(final String player, final String game, final String board) {
        final HttpResponse<JsonNode> response = Unirest.get(MpStatsRestApiClient.BASE_URL + "java/leaderboards/player")
                .queryString("player", player)
                .queryString("game", game)
                .queryString("board", board.toLowerCase())
                .header("User-Agent", USER_AGENT)
                .connectTimeout(TIMEOUT)
                .asJson();

        return this.parseHttpResponse(response, JavaPlayerStats.class);
    }

    public ResponseModel getJavaLeaderboard(final String game, final String stat, final String board, final int startPos, final int endPos, final long time) {
        final HttpResponse<JsonNode> response = Unirest.get(MpStatsRestApiClient.BASE_URL + "java/leaderboards/player")
                .queryString("game", game)
                .queryString("board", board.toLowerCase())
                .header("User-Agent", USER_AGENT)
                .connectTimeout(TIMEOUT)
                .asJson();

        return this.parseHttpResponse(response, JavaPlayerStats.class);
    }

    public ResponseModel getGroups() {
        final HttpResponse<JsonNode> response = Unirest.get(MpStatsRestApiClient.BASE_URL + "java/leaderboards/group/groups")
                .header("User-Agent", USER_AGENT)
                .connectTimeout(TIMEOUT)
                .asJson();

        if (!response.isSuccess()) {
            return UNKNOWN_ERROR_RESPONSE_MODEL;
        }

        final JSONObject jsonObject = response.getBody().getObject();
        if (!jsonObject.getBoolean("success")) {
            return this.gson.fromJson(jsonObject.toString(), ErrorModel.class);
        }

        return this.gson.fromJson(jsonObject.toString(), JavaGroupsGroups.class);
    }

    public ResponseModel getPlayerGroup(final String player, final String group, final String stat, final String board) {
        final HttpResponse<JsonNode> response = Unirest.get(MpStatsRestApiClient.BASE_URL + "java/leaderboards/group/player")
                .queryString("player", player)
                .queryString("group", group)
                .queryString("stat", stat)
                .queryString("board", board.toLowerCase())
                .header("User-Agent", USER_AGENT)
                .connectTimeout(TIMEOUT)
                .asJson();

        return this.parseHttpResponse(response, JavaGroupsPlayer.class);
    }
}
