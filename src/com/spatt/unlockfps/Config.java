package com.spatt.unlockfps;

/**
 * Every outside address the app can use. If you create the repo under a different name,
 * change only REPO below. An empty value switches the matching feature off.
 *
 * The repo must be PUBLIC, otherwise GitHub answers 404 to these requests.
 */
final class Config {
    private static final String OWNER = "JrSpatt";
    private static final String REPO = "UnlockFPS";
    private static final String BRANCH = "main";

    /** Online game list, downloaded once a day. It is used only if its "version" is higher than the bundled one. */
    static final String GAMES_JSON_URL =
        "https://raw.githubusercontent.com/" + OWNER + "/" + REPO + "/" + BRANCH + "/data/games.json";

    /** "Latest release" API used by the update check (needs at least one published release). */
    static final String RELEASES_API_URL =
        "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest";

    /** Page opened when an update is available or the check fails. */
    static final String RELEASES_PAGE_URL =
        "https://github.com/" + OWNER + "/" + REPO + "/releases/latest";

    /** Page opened by the "Game list" row in About. */
    static final String GAME_LIST_URL =
        "https://github.com/" + OWNER + "/" + REPO + "/blob/" + BRANCH + "/data/games.json";

    private Config() {}
}
