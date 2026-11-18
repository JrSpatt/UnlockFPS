package com.spatt.unlockfps;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;

/** About, updates, support and setup help. */
public class AboutActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    private boolean themeExpanded;
    private boolean languageExpanded;
    private boolean checking = false;
    private String updateUrl = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            themeExpanded = savedInstanceState.getBoolean("theme_expanded");
            languageExpanded = savedInstanceState.getBoolean("language_expanded");
        }
        Themes.apply(this);
        UiHelper.setupDarkBars(this);
        String currentVer = UiHelper.appVersion(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(UiHelper.topBar(this, getString(R.string.about_title)));

        // Hero
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(dp(16), dp(20), dp(16), dp(8));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_launcher);
        hero.addView(icon, new LinearLayout.LayoutParams(dp(92), dp(92)));
        TextView name = UiHelper.text(this, getString(R.string.app_name), 28, UiHelper.COL_TEXT, UiHelper.TF_BOLD);
        name.setPadding(0, dp(16), 0, 0);
        name.setGravity(Gravity.CENTER);
        hero.addView(name);
        TextView ver = UiHelper.chip(this, getString(R.string.about_version, currentVer), UiHelper.COL_TEAL);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        vp.topMargin = dp(10);
        hero.addView(ver, vp);
        TextView desc = UiHelper.text(this,
            getString(R.string.about_desc),
            14, UiHelper.COL_MUTED, UiHelper.TF_REGULAR);
        desc.setGravity(Gravity.CENTER);
        desc.setLineSpacing(dp(4), 1f);
        desc.setPadding(dp(8), dp(16), dp(8), 0);
        hero.addView(desc);
        root.addView(hero);

        root.addView(sectionLabel(getString(R.string.appearance)));
        LinearLayout appearance = UiHelper.card(this);
        appearance.setPadding(dp(14), dp(4), dp(14), dp(4));
        appearance.addView(buildPicker(true));
        appearance.addView(UiHelper.divider(this, 58));
        appearance.addView(buildPicker(false));
        root.addView(appearance);

        // Links
        root.addView(sectionLabel(getString(R.string.about_section_app)));
        LinearLayout links = UiHelper.card(this);
        links.setPadding(dp(14), dp(4), dp(14), dp(4));
        final LinearLayout[] updateRow = new LinearLayout[1];
        updateRow[0] = UiHelper.row(this, R.drawable.ui_refresh, UiHelper.COL_PRIMARY,
            getString(R.string.about_update), getString(R.string.about_update_sub, currentVer), null,
            v -> checkForUpdates(updateRow[0], currentVer));
        links.addView(updateRow[0]);
        links.addView(UiHelper.divider(this, 58));
        links.addView(UiHelper.row(this, R.drawable.ui_info, UiHelper.COL_WARN,
            getString(R.string.about_game_list), null, null,
            v -> showGameList()));
        links.addView(UiHelper.divider(this, 58));
        links.addView(UiHelper.row(this, R.drawable.ui_refresh, UiHelper.COL_MUTED,
            getString(R.string.reset_defaults), getString(R.string.reset_instructions), null, v -> {
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", FpsApplier.POWERKEEPER_PKG, null)));
                } catch (android.content.ActivityNotFoundException | SecurityException e) {
                    android.widget.Toast.makeText(this, R.string.reset_unavailable, android.widget.Toast.LENGTH_SHORT).show();
                }
            }));
        root.addView(links);

        // Setup
        root.addView(sectionLabel(getString(R.string.about_section_running)));
        LinearLayout setup = UiHelper.card(this);
        setup.setPadding(dp(14), dp(4), dp(14), dp(4));
        setup.addView(UiHelper.row(this, R.drawable.ui_settings, UiHelper.COL_TEAL,
            getString(R.string.about_appinfo),
            getString(R.string.about_appinfo_sub),
            null, v -> {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null));
                startActivity(i);
            }));
        setup.addView(UiHelper.divider(this, 58));
        setup.addView(UiHelper.row(this, R.drawable.ui_gauge, UiHelper.COL_ACCENT2,
            getString(R.string.about_widget), getString(R.string.about_widget_sub), null, v -> addWidget()));
        setup.addView(UiHelper.divider(this, 58));
        setup.addView(UiHelper.row(this, R.drawable.ui_info, UiHelper.COL_PRIMARY,
            getString(R.string.about_guide), getString(R.string.about_guide_sub), null,
            v -> startActivity(new Intent(this, OnboardingActivity.class))));
        root.addView(setup);

        TextView footer = UiHelper.text(this, getString(R.string.about_footer),
            12, UiHelper.COL_DIM, UiHelper.TF_REGULAR);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(24), 0, 0);
        root.addView(footer, UiHelper.matchWrap(this, 0));

        ScrollView scroll = new ScrollView(this);
        scroll.setId(R.id.settings_scroll);
        scroll.setBackgroundColor(UiHelper.COL_BG);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(root);
        UiHelper.applyNotchSafePadding(this, scroll, 16, 12, 28);
        setContentView(scroll);
    }

    private View buildPicker(boolean theme) {
        String current = theme ? Themes.get(this) : Lang.get(this);
        String[] ids = theme ? Themes.ALL : new String[]{Lang.TR, Lang.EN};
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        boolean expanded = theme ? themeExpanded : languageExpanded;
        choices.setVisibility(expanded ? View.VISIBLE : View.GONE);
        choices.setPadding(dp(12), 0, 0, dp(8));
        ImageView arrow = UiHelper.icon(this, R.drawable.ui_chevron, UiHelper.COL_MUTED, 18);
        arrow.setRotation(expanded ? -90f : 90f);
        LinearLayout trailing = new LinearLayout(this);
        trailing.setGravity(Gravity.CENTER_VERTICAL);
        TextView value = UiHelper.text(this, pickerLabel(theme, current), 13, UiHelper.COL_MUTED, UiHelper.TF_MEDIUM);
        value.setPadding(0, 0, dp(6), 0);
        trailing.addView(value);
        trailing.addView(arrow);
        group.addView(UiHelper.row(this, theme ? R.drawable.ui_palette : R.drawable.ui_globe, UiHelper.COL_PRIMARY,
            getString(theme ? R.string.set_theme_title : R.string.set_lang_title), null, trailing, v -> {
                boolean open = choices.getVisibility() != View.VISIBLE;
                if (theme) themeExpanded = open; else languageExpanded = open;
                choices.setVisibility(open ? View.VISIBLE : View.GONE);
                arrow.setRotation(open ? -90f : 90f);
            }));
        for (String id : ids) {
            boolean selected = id.equals(current);
            LinearLayout option = new LinearLayout(this);
            option.setGravity(Gravity.CENTER_VERTICAL);
            option.setMinimumHeight(dp(46));
            option.setPadding(dp(12), 0, dp(12), 0);
            option.setBackground(UiHelper.ripple(this, selected ? UiHelper.pillBg(this, UiHelper.COL_SURFACE_SEL, 0, 12) : null, 12));
            if (theme) {
                View swatch = new View(this);
                swatch.setBackground(UiHelper.pillBg(this, Themes.background(id), Themes.accent(id), 8));
                option.addView(swatch, new LinearLayout.LayoutParams(dp(22), dp(18)));
            } else {
                ImageView flag = new ImageView(this);
                flag.setImageResource(id.equals(Lang.TR) ? R.drawable.flag_tr : R.drawable.flag_gb);
                option.addView(flag, new LinearLayout.LayoutParams(dp(22), dp(16)));
            }
            TextView label = UiHelper.text(this, pickerLabel(theme, id), 14, UiHelper.COL_TEXT, UiHelper.TF_MEDIUM);
            label.setPadding(dp(12), 0, 0, 0);
            option.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
            if (selected) option.addView(UiHelper.icon(this, R.drawable.ui_check, UiHelper.COL_PRIMARY, 18));
            option.setSelected(selected);
            option.setOnClickListener(v -> {
                if (selected) {
                    if (theme) themeExpanded = false; else languageExpanded = false;
                    choices.setVisibility(View.GONE);
                    arrow.setRotation(90f);
                    return;
                }
                if (theme) Themes.set(this, id);
                else {
                    Lang.set(this, id);
                    GameCaps.reload(this);
                }
                UnlockWidget.updateAll(this);
                UnlockTileService.requestTileSync(this);
                recreate();
            });
            choices.addView(option);
        }
        group.addView(choices);
        return group;
    }

    private String pickerLabel(boolean theme, String id) {
        if (theme) return getString(Themes.nameRes(id));
        return getString(Lang.TR.equals(id) ? R.string.lang_name_tr : R.string.lang_name_en);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("theme_expanded", themeExpanded);
        state.putBoolean("language_expanded", languageExpanded);
    }

    private void showGameList() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(12), dp(16), dp(12));
        panel.setBackground(UiHelper.pillBg(this, UiHelper.COL_LOG_BG, UiHelper.COL_OUTLINE, 14));
        for (GameCaps.Info game : GameCaps.catalog(this)) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(7), 0, dp(7));
            TextView name = UiHelper.text(this, game.name, 13, UiHelper.COL_TEXT, android.graphics.Typeface.MONOSPACE);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView cap = UiHelper.text(this, game.cap + " FPS", 12, UiHelper.fpsColor(game.cap), android.graphics.Typeface.MONOSPACE);
            cap.setPadding(dp(8), 0, 0, 0);
            row.addView(cap);
            panel.addView(row);
        }
        android.app.Dialog dialog = UiHelper.sheet(this, panel);
        dialog.setTitle(R.string.about_game_list);
        dialog.show();
        int height = Math.min(dp(390), getResources().getDisplayMetrics().heightPixels / 2);
        dialog.getWindow().setLayout(-1, height);
    }

    /** Pins the widget, or explains HyperOS's "Home screen shortcuts" permission first. */
    private void addWidget() {
        if (!UnlockWidget.isMiuiShortcutAllowed(this)) {
            android.widget.LinearLayout c = new android.widget.LinearLayout(this);
            c.setOrientation(android.widget.LinearLayout.VERTICAL);
            final android.app.Dialog[] holder = new android.app.Dialog[1];
            TextView t = UiHelper.text(this, getString(R.string.widget_perm_title), 18, UiHelper.COL_TEXT, UiHelper.TF_BOLD);
            c.addView(t);
            TextView b = UiHelper.text(this, getString(R.string.widget_perm_body), 14, UiHelper.COL_MUTED, UiHelper.TF_REGULAR);
            b.setLineSpacing(dp(4), 1f);
            b.setPadding(0, dp(12), 0, dp(18));
            c.addView(b);
            c.addView(UiHelper.button(this, getString(R.string.widget_perm_open), R.drawable.ui_settings, true, v -> {
                holder[0].dismiss();
                UnlockWidget.openMiuiPermissions(this);
            }));
            TextView manual = UiHelper.button(this, getString(R.string.widget_perm_manual), 0, false, v -> {
                holder[0].dismiss();
                android.widget.Toast.makeText(this, getString(R.string.widget_pin_manual),
                    android.widget.Toast.LENGTH_LONG).show();
            });
            ((android.widget.LinearLayout.LayoutParams) manual.getLayoutParams()).topMargin = dp(10);
            c.addView(manual);
            holder[0] = UiHelper.sheet(this, c);
            holder[0].show();
            return;
        }
        if (!UnlockWidget.requestPin(this)) {
            android.widget.Toast.makeText(this, getString(R.string.widget_pin_manual),
                android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private View sectionLabel(String s) {
        TextView t = UiHelper.overline(this, s, UiHelper.COL_DIM);
        t.setPadding(dp(6), dp(24), 0, dp(0));
        return t;
    }

    private void checkForUpdates(LinearLayout row, String currentVer) {
        if (updateUrl != null) {
            UiHelper.openUrl(this, updateUrl);
            return;
        }
        TextView sub = UiHelper.subtitleOf(row);
        if (Config.RELEASES_API_URL.isEmpty()) {
            sub.setText(getString(R.string.upd_not_set));
            sub.setTextColor(UiHelper.COL_WARN);
            return;
        }
        if (checking) return;
        checking = true;
        sub.setText(getString(R.string.upd_checking));
        sub.setTextColor(UiHelper.COL_MUTED);
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(Config.RELEASES_API_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("User-Agent", "UnlockFPS/" + currentVer);
                if (conn.getResponseCode() != 200) throw new RuntimeException("HTTP " + conn.getResponseCode());
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject json = new JSONObject(sb.toString());
                String tag = json.optString("tag_name", "").replaceFirst("^v", "");
                String releaseUrl = json.optString("html_url", Config.RELEASES_PAGE_URL);
                String apkUrl = releaseUrl;
                JSONArray assets = json.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.getJSONObject(i);
                        if (a.optString("name", "").endsWith(".apk")) {
                            apkUrl = a.optString("browser_download_url", releaseUrl);
                            break;
                        }
                    }
                }
                final String target = apkUrl;
                final boolean newer = compareVersions(tag, currentVer) > 0;
                main.post(() -> {
                    checking = false;
                    if (newer) {
                        updateUrl = target;
                        sub.setText(getString(R.string.upd_available, tag));
                        sub.setTextColor(UiHelper.COL_TEAL);
                    } else {
                        sub.setText(getString(R.string.upd_current, currentVer));
                        sub.setTextColor(UiHelper.COL_TEAL);
                    }
                });
            } catch (Exception e) {
                main.post(() -> {
                    checking = false;
                    updateUrl = Config.RELEASES_PAGE_URL;
                    sub.setText(getString(R.string.upd_failed));
                    sub.setTextColor(UiHelper.COL_WARN);
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private static int compareVersions(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int va = i < pa.length ? parseDigits(pa[i]) : 0;
            int vb = i < pb.length ? parseDigits(pb[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private static int parseDigits(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private int dp(float v) {
        return UiHelper.dp(this, v);
    }
}
