package com.spatt.unlockfps;

import android.content.Context;
import android.graphics.Color;

/** Four colour themes. The choice is saved; every screen calls apply() before it builds its views. */
final class Themes {
    static final String OLED = "oled";
    static final String BLACK = "black";
    static final String RUBY = "ruby";
    static final String GOLD = "gold";
    static final String[] ALL = {OLED, BLACK, RUBY, GOLD};

    private static final String PREFS = "unlock_fps_prefs";
    private static final String KEY = "theme";

    private Themes() {}

    static String get(Context ctx) {
        String saved = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, BLACK);
        for (String t : ALL) if (t.equals(saved)) return saved;
        return BLACK;
    }

    static void set(Context ctx, String id) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, id).apply();
    }

    /** Accent colour of a theme (used by the picker swatches). */
    static int accent(String id) {
        switch (id) {
            case OLED: return Color.parseColor("#38BDF8");
            case RUBY: return Color.parseColor("#FB4F76");
            case GOLD: return Color.parseColor("#F5C542");
            default: return Color.parseColor("#38BDF8");
        }
    }

    /** Background colour of a theme (used by the picker swatches). */
    static int background(String id) {
        switch (id) {
            case OLED: return Color.parseColor("#000000");
            case RUBY: return Color.parseColor("#1A0A10");
            case GOLD: return Color.parseColor("#18140A");
            default: return Color.parseColor("#1C1C1F");
        }
    }

    static int nameRes(String id) {
        switch (id) {
            case OLED: return R.string.theme_oled;
            case RUBY: return R.string.theme_ruby;
            case GOLD: return R.string.theme_gold;
            default: return R.string.theme_black;
        }
    }

    static void apply(Context ctx) {
        switch (get(ctx)) {
            case OLED:
                set(Color.parseColor("#000000"), Color.parseColor("#09090B"), Color.parseColor("#111114"), Color.parseColor("#0B1A26"), Color.parseColor("#1C1C22"), Color.parseColor("#111115"),
                    Color.parseColor("#38BDF8"), Color.parseColor("#818CF8"), Color.parseColor("#6366F1"), Color.parseColor("#2DD4BF"), Color.parseColor("#04121C"),
                    Color.parseColor("#F1F5F9"), Color.parseColor("#8A8F99"), Color.parseColor("#565B66"), Color.parseColor("#07131C"), Color.parseColor("#0D0B1C"),
                    Color.parseColor("#000000"), Color.parseColor("#141418"), Color.parseColor("#040405"), Color.parseColor("#2C2C33"));
                break;
            default:
            case BLACK:
                set(Color.parseColor("#0B0B0D"), Color.parseColor("#141416"), Color.parseColor("#1C1C1F"), Color.parseColor("#102030"), Color.parseColor("#27272C"), Color.parseColor("#1B1B1F"),
                    Color.parseColor("#38BDF8"), Color.parseColor("#818CF8"), Color.parseColor("#6366F1"), Color.parseColor("#2DD4BF"), Color.parseColor("#04121C"),
                    Color.parseColor("#F1F5F9"), Color.parseColor("#8E939C"), Color.parseColor("#5A5F69"), Color.parseColor("#0E1F2D"), Color.parseColor("#171630"),
                    Color.parseColor("#08080A"), Color.parseColor("#1C1C21"), Color.parseColor("#0C0C0F"), Color.parseColor("#34343B"));
                break;
            case RUBY:
                set(Color.parseColor("#0D0508"), Color.parseColor("#1A0A10"), Color.parseColor("#241017"), Color.parseColor("#2C0F1A"), Color.parseColor("#3A1621"), Color.parseColor("#26101A"),
                    Color.parseColor("#FB4F76"), Color.parseColor("#FF8FA8"), Color.parseColor("#E11D48"), Color.parseColor("#2DD4BF"), Color.parseColor("#200309"),
                    Color.parseColor("#FBEFF2"), Color.parseColor("#B58C97"), Color.parseColor("#7A5560"), Color.parseColor("#2A0A14"), Color.parseColor("#1C0710"),
                    Color.parseColor("#080304"), Color.parseColor("#2A121B"), Color.parseColor("#0F0508"), Color.parseColor("#4A2230"));
                break;
            case GOLD:
                set(Color.parseColor("#0C0A05"), Color.parseColor("#18140A"), Color.parseColor("#211B0D"), Color.parseColor("#2A220C"), Color.parseColor("#3A3015"), Color.parseColor("#231D0D"),
                    Color.parseColor("#F5C542"), Color.parseColor("#FFD97A"), Color.parseColor("#D4A017"), Color.parseColor("#2DD4BF"), Color.parseColor("#1A1200"),
                    Color.parseColor("#FAF5E6"), Color.parseColor("#B3A57F"), Color.parseColor("#7A6E4F"), Color.parseColor("#2A2108"), Color.parseColor("#1B1505"),
                    Color.parseColor("#070502"), Color.parseColor("#2A220F"), Color.parseColor("#0E0B04"), Color.parseColor("#4A3E1C"));
                break;
        }
    }

    private static void set(int bg, int surface, int surface2, int sel, int outline, int divider,
                            int primary, int accent2, int indigo, int teal, int onPrimary,
                            int text, int muted, int dim, int heroA, int heroB,
                            int logBg, int track, int trackDark, int strokeStrong) {
        UiHelper.COL_BG = bg;
        UiHelper.COL_SURFACE = surface;
        UiHelper.COL_SURFACE_2 = surface2;
        UiHelper.COL_SURFACE_SEL = sel;
        UiHelper.COL_OUTLINE = outline;
        UiHelper.COL_DIVIDER = divider;
        UiHelper.COL_PRIMARY = primary;
        UiHelper.COL_ACCENT2 = accent2;
        UiHelper.COL_INDIGO = indigo;
        UiHelper.COL_TEAL = teal;
        UiHelper.COL_ON_PRIMARY = onPrimary;
        UiHelper.COL_TEXT = text;
        UiHelper.COL_MUTED = muted;
        UiHelper.COL_DIM = dim;
        UiHelper.COL_HERO_A = heroA;
        UiHelper.COL_HERO_B = heroB;
        UiHelper.COL_LOG_BG = logBg;
        UiHelper.COL_TRACK = track;
        UiHelper.COL_TRACK_DARK = trackDark;
        UiHelper.COL_STROKE_STRONG = strokeStrong;
    }
}
