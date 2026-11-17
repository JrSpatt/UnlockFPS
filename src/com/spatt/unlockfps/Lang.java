package com.spatt.unlockfps;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import java.util.Locale;

/** Two languages only: Turkish and English. The choice is kept in the app's own preferences. */
final class Lang {
    static final String TR = "tr";
    static final String EN = "en";
    private static final String PREFS = "unlock_fps_prefs";
    private static final String KEY = "lang";

    private Lang() {}

    /** Saved choice; first run follows the phone (Turkish phone = Turkish, anything else = English). */
    static String get(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String saved = sp.getString(KEY, null);
        if (TR.equals(saved) || EN.equals(saved)) return saved;
        return Locale.getDefault().getLanguage().equals("tr") ? TR : EN;
    }

    static void set(Context ctx, String lang) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, lang).apply();
    }

    /** Returns a context whose resources use the chosen language. */
    static Context wrap(Context base) {
        Locale locale = new Locale(get(base));
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.setLocale(locale);
        return base.createConfigurationContext(cfg);
    }
}
