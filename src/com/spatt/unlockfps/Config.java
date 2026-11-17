package com.spatt.unlockfps;

/**
 * Every outside address the app can use. All of them are EMPTY on purpose: fill them in later.
 * An empty value switches the matching feature off (no request is made, no link opens).
 *
 * Example once your repo exists (replace USER/REPO):
 *   GAMES_JSON_URL    = "https://raw.githubusercontent.com/USER/REPO/main/data/games.json"
 *   RELEASES_API_URL  = "https://api.github.com/repos/USER/REPO/releases/latest"
 *   RELEASES_PAGE_URL = "https://github.com/USER/REPO/releases/latest"
 *   GAME_LIST_URL     = "https://github.com/USER/REPO/blob/main/data/games.json"
 */
final class Config {
    /** Online copy of the game list (downloaded once a day). Empty = use only the bundled list. */
    static final String GAMES_JSON_URL = "";

    /** GitHub "latest release" API address used by the update check. Empty = update check off. */
    static final String RELEASES_API_URL = "";

    /** Page opened when an update is available or the check fails. */
    static final String RELEASES_PAGE_URL = "";

    /** Page opened by the "Game list" row in About. */
    static final String GAME_LIST_URL = "";

    private Config() {}
}
