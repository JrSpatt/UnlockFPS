package com.spatt.unlockfps;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Unlock FPS home: target refresh rate, Beast mode, background settings and the app picker. */
public class MainActivity extends Activity {
    private static final int FILTER_ALL = 0;
    private static final int FILTER_SELECTED = 1;
    private static final int FILTER_GAMES = 2;
    private static final int FILTER_SYSTEM = 3;
    private static final int FILTER_COUNT = 4;

    private static class AppEntry {
        final String label;
        final String packageName;
        final Drawable icon;
        final boolean isGame;
        final boolean isSystem;
        View row;
        FrameLayout check;
        TextView sub;

        AppEntry(String label, String packageName, Drawable icon, boolean isGame, boolean isSystem) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
            this.isGame = isGame;
            this.isSystem = isSystem;
        }
    }

    private final List<AppEntry> allApps = new ArrayList<>();
    private long lastSentAt;
    private int lastSentRate;
    private int lastSentCount;
    private ProfileDraft draft;
    private Set<String> selectedPackages;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable resetBtnRunnable = this::resetApplyButton;
    private final Deque<String> logLines = new ArrayDeque<>();
    private ScrollView homeScroll;
    static final String ACTION_PICK_PROFILE = "com.spatt.unlockfps.PICK_PROFILE";
    private View appsSection;
    private boolean appsLoaded, pendingPickerScroll;
    private EditText appSearch;
    private final Runnable catalogListener = this::catalogChanged;
    private int catalogRevision, loadRequest;
    private int restoreHomeY;
    private String renderedTheme, renderedLang, searchText = "";
    private final TextView[] filterChips = new TextView[FILTER_COUNT];
    private RateSelector segBar;
    private TextView beastStrip;
    private FrameLayout beastBtn;
    private ImageView beastIcon;
    private TextView captionText;

    private TextView statusChip;
    private TextView appCountText;
    private TextView hzNumber;
    private TextView applyBtn;
    private TextView lastApplied;
    private LinearLayout logCard;
    private TextView logText;
    private TextView logTileSub;
    private TextView selectedCountText;
    private LinearLayout listCard;
    private TextView emptyText;
    private Switch autoSwitch;
    private boolean syncingUi = false;
    private LinearLayout healthSlot;
    private boolean serviceWasKilled = false;
    private boolean batteryDismissed = false;
    private int filter = FILTER_ALL;
    private String query = "";

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        renderedTheme = Themes.get(this);
        renderedLang = Lang.get(this);
        if (savedInstanceState != null) {
            restoreHomeY = savedInstanceState.getInt("home_y");
            filter = savedInstanceState.getInt("app_filter", FILTER_ALL);
            searchText = savedInstanceState.getString("search", "");
            query = searchText.trim().toLowerCase(Locale.ROOT);
            batteryDismissed = savedInstanceState.getBoolean("dismissed");
        }
        Themes.apply(this);
        UiHelper.setupDarkBars(this);
        draft = new ProfileDraft(this);
        selectedPackages = draft.suggestInstalledGames();
        catalogRevision = InstalledApps.get(this).revision();
        GameCaps.init(this);
        // A stale heartbeat means HyperOS killed the background service while we were away.
        long beat = FpsApplier.lastServiceBeat(this);
        serviceWasKilled = FpsApplier.isAutoEnabled(this) && beat > 0
            && System.currentTimeMillis() - beat > 60_000L;

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        content.addView(buildHeader());
        healthSlot = new LinearLayout(this);
        healthSlot.setOrientation(LinearLayout.VERTICAL);
        content.addView(healthSlot);
        content.addView(buildHero());
        content.addView(buildSettings());
        content.addView(buildTools());
        content.addView(buildLog());
        appsSection = buildAppsSection();
        content.addView(appsSection);

        ScrollView scroll = new ScrollView(this);
        homeScroll = scroll;
        scroll.setBackgroundColor(UiHelper.COL_BG);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content);
        UiHelper.applyNotchSafePadding(this, scroll, 16, 12, 24);
        setContentView(scroll);

        if (savedInstanceState != null) {
            List<String> lines = savedInstanceState.getStringArrayList("log_lines");
            if (lines != null) logLines.addAll(lines);
            logText.setText(TextUtils.join("\n", logLines));
            if (savedInstanceState.getBoolean("log_open")) toggleLog();
        }
        if (savedInstanceState != null && savedInstanceState.containsKey("last_sent_at")) {
            lastSentAt = savedInstanceState.getLong("last_sent_at");
            lastSentRate = savedInstanceState.getInt("last_sent_rate");
            lastSentCount = savedInstanceState.getInt("last_sent_count");
            showLastSent();
        }
        if (savedInstanceState == null && FpsApplier.hasAppliedProfile(this) && FpsApplier.isAutoEnabled(this))
            Auto120Service.ensureRunning(this);
        loadInstalledAppsAsync();
        GameCaps.refreshAsync(this, this::rebindTags);
        if (!FpsApplier.isOnboarded(this) && !ACTION_PICK_PROFILE.equals(getIntent().getAction())) {
            startActivity(new Intent(this, OnboardingActivity.class));
        }
    }

    @Override protected void onStart() {
        super.onStart();
        InstalledApps catalog = InstalledApps.get(this);
        catalog.addListener(catalogListener);
        if (catalog.revision() != catalogRevision) catalogChanged();
    }

    @Override protected void onStop() {
        InstalledApps.get(this).removeListener(catalogListener);
        super.onStop();
    }

    private void catalogChanged() {
        InstalledApps catalog = InstalledApps.get(this);
        catalogRevision = catalog.revision();
        selectedPackages = draft.suggestInstalledGames();
        allApps.removeIf(app -> !catalog.packages().contains(app.packageName));
        refreshList();
        updateFilterChips();
        resetApplyButton();
        loadInstalledAppsAsync();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openPickerIfRequested();
    }

    private void openPickerIfRequested() {
        if (!ACTION_PICK_PROFILE.equals(getIntent().getAction())) return;
        getIntent().setAction(null);
        restoreHomeY = 0;
        filter = FILTER_ALL;
        appSearch.setText("");
        updateFilterChips();
        refreshList();
        pendingPickerScroll = true;
        scrollToPicker();
    }

    private void scrollToPicker() {
        if (!pendingPickerScroll || !appsLoaded) return;
        pendingPickerScroll = false;
        homeScroll.post(() -> homeScroll.scrollTo(0, appsSection.getTop()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!renderedTheme.equals(Themes.get(this)) || !renderedLang.equals(Lang.get(this))) {
            recreate();
            return;
        }
        openPickerIfRequested();
        updateHealth();
        // The widget or the quick tile may have changed things while we were away.
        syncingUi = true;
        autoSwitch.setChecked(FpsApplier.isAutoEnabled(this));
        syncingUi = false;
        updateBeastUi();
        updateSegments();
        updateStatusChip(FpsApplier.isPowerKeeperInstalled(this));
    }

    /** Re-tags rows after a newer game list was downloaded. */
    private void rebindTags() {
        for (AppEntry e : allApps) if (e.sub != null) bindSub(e);
        appendLog("Game list updated (v" + GameCaps.version() + ")");
    }

    // ---------------------------------------------------------------- health

    private void updateHealth() {
        healthSlot.removeAllViews();
        if (FpsApplier.isSystemRefreshTooLow(this)) {
            LinearLayout card = (LinearLayout) healthCard(R.string.health_refresh_title, R.string.health_refresh_body, null);
            LinearLayout actions = (LinearLayout) card.getChildAt(card.getChildCount() - 1);
            actions.removeAllViews();
            actions.addView(actionChip(getString(R.string.health_open_display), UiHelper.COL_WARN,
                v -> UiHelper.openDisplaySettings(this)));
            TextView title = (TextView) ((LinearLayout) card.getChildAt(0)).getChildAt(1);
            title.setText(getString(R.string.health_refresh_title, FpsApplier.getSystemRefreshSetting(this)));
            healthSlot.addView(card);
        } else if (serviceWasKilled) {
            healthSlot.addView(healthCard(R.string.health_killed_title, R.string.health_killed_body, () -> {
                serviceWasKilled = false;
                updateHealth();
            }));
        } else if (!batteryDismissed && FpsApplier.isAutoEnabled(this) && !UiHelper.isBatteryUnrestricted(this)) {
            healthSlot.addView(healthCard(R.string.health_battery_title, R.string.health_battery_body, () -> {
                batteryDismissed = true;
                updateHealth();
            }));
        }
    }

    private View healthCard(int titleRes, int bodyRes, Runnable onDismiss) {
        LinearLayout card = UiHelper.card(this);
        card.setBackground(UiHelper.pillBg(this, UiHelper.alpha(UiHelper.COL_WARN, 0.08f),
            UiHelper.alpha(UiHelper.COL_WARN, 0.35f), 22));
        card.setPadding(dp(16), dp(16), dp(16), dp(14));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = UiHelper.icon(this, R.drawable.ui_info, UiHelper.COL_WARN, 20);
        ((LinearLayout.LayoutParams) ic.getLayoutParams()).rightMargin = dp(10);
        head.addView(ic);
        head.addView(UiHelper.text(this, getString(titleRes), 15, UiHelper.COL_TEXT, UiHelper.TF_BOLD),
            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (onDismiss != null) head.addView(actionChip(getString(R.string.health_dismiss), UiHelper.COL_MUTED, v -> onDismiss.run()));
        card.addView(head);
        TextView body = UiHelper.text(this, getString(bodyRes), 13, UiHelper.COL_MUTED, UiHelper.TF_REGULAR);
        body.setLineSpacing(dp(3), 1f);
        body.setPadding(dp(30), dp(8), 0, dp(12));
        card.addView(body);
        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(0, 0, 0, 0);
        actions.addView(actionChip(getString(R.string.health_fix_battery), UiHelper.COL_WARN,
            v -> UiHelper.requestBatteryExemption(this)));
        actions.addView(actionChip(getString(R.string.health_autostart), UiHelper.COL_WARN,
            v -> UiHelper.openAutostart(this)));
        actions.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int needed = 0;
            for (int i = 0; i < actions.getChildCount(); i++) {
                TextView chip = (TextView) actions.getChildAt(i);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) chip.getLayoutParams();
                needed += (int) Math.ceil(chip.getPaint().measureText(chip.getText().toString()))
                    + chip.getPaddingLeft() + chip.getPaddingRight() + lp.rightMargin;
            }
            int orientation = needed > right - left ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL;
            if (actions.getOrientation() != orientation) actions.post(() -> actions.setOrientation(orientation));
        });
        card.addView(actions);
        return card;
    }

    private TextView actionChip(String label, int color, View.OnClickListener l) {
        TextView c = UiHelper.text(this, label, 13, color, UiHelper.TF_BOLD);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(12), 0, dp(12), 0);
        c.setBackground(UiHelper.ripple(this, UiHelper.pillBg(this, 0, UiHelper.alpha(color, 0.4f), 99), 99));
        c.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
        lp.rightMargin = dp(8);
        c.setLayoutParams(lp);
        return c;
    }

    // ---------------------------------------------------------------- header

    private View buildHeader() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(2), 0, 0, dp(6));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(42), dp(42));
        lp.rightMargin = dp(12);
        bar.addView(logo, lp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(UiHelper.text(this, getString(R.string.app_name), 22, UiHelper.COL_TEXT, UiHelper.TF_BOLD));
        TextView sub = UiHelper.text(this, deviceLine(), 12.5f, UiHelper.COL_MUTED, UiHelper.TF_REGULAR);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        sub.setPadding(0, dp(5), 0, 0);
        col.addView(sub);
        bar.addView(col);

        View settings = UiHelper.iconButton(this, R.drawable.ui_settings,
            v -> startActivity(new Intent(this, AboutActivity.class)));
        settings.setContentDescription(getString(R.string.about_title));
        bar.addView(settings);
        return bar;
    }

    private String deviceLine() {
        int max = FpsApplier.getMaxSupportedFps(this);
        if (max <= 0) {
            return getString(R.string.main_device_line_unknown, FpsApplier.getDeviceName(this),
                android.os.Build.VERSION.RELEASE);
        }
        return getString(R.string.main_device_line, FpsApplier.getDeviceName(this),
            android.os.Build.VERSION.RELEASE, max);
    }

    // ---------------------------------------------------------------- hero

    private View buildHero() {
        LinearLayout hero = UiHelper.card(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{UiHelper.COL_HERO_A, UiHelper.COL_SURFACE, UiHelper.COL_HERO_B});
        bg.setCornerRadius(dp(28));
        bg.setStroke(dp(1), UiHelper.alpha(UiHelper.COL_PRIMARY, 0.22f));
        hero.setBackground(bg);
        hero.setPadding(dp(20), dp(20), dp(20), dp(20));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        statusChip = UiHelper.chip(this, "", UiHelper.COL_TEAL);
        top.addView(statusChip);
        View spacer = new View(this);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        appCountText = UiHelper.text(this, "", 13, UiHelper.COL_MUTED, UiHelper.TF_MEDIUM);
        top.addView(appCountText);
        hero.addView(top);

        LinearLayout numRow = new LinearLayout(this);
        numRow.setOrientation(LinearLayout.HORIZONTAL);
        numRow.setGravity(Gravity.BOTTOM);
        numRow.setPadding(0, dp(18), 0, 0);
        hzNumber = UiHelper.text(this, "", 76, UiHelper.COL_TEXT, UiHelper.TF_DISPLAY);
        hzNumber.setLetterSpacing(-0.03f);
        float h = hzNumber.getTextSize();
        hzNumber.getPaint().setShader(new LinearGradient(0, 0, 0, h,
            new int[]{0xFFFFFFFF, UiHelper.mix(UiHelper.COL_PRIMARY, 0xFFFFFFFF, 0.6f), UiHelper.COL_PRIMARY}, null, Shader.TileMode.CLAMP));
        numRow.addView(hzNumber);
        TextView hz = UiHelper.text(this, "Hz", 24, UiHelper.COL_MUTED, UiHelper.TF_BOLD);
        hz.setPadding(dp(6), 0, 0, dp(10));
        numRow.addView(hz);
        hero.addView(numRow);

        captionText = UiHelper.text(this, getString(R.string.main_caption),
            13.5f, UiHelper.COL_MUTED, UiHelper.TF_REGULAR);
        captionText.setPadding(0, dp(6), 0, dp(18));
        hero.addView(captionText);

        // Segmented control
        LinearLayout rateRow = new LinearLayout(this);
        rateRow.setOrientation(LinearLayout.HORIZONTAL);
        rateRow.setGravity(Gravity.CENTER_VERTICAL);

        segBar = new RateSelector(this, fps -> {
            draft.setTarget(fps);
            updateSegments();
        });
        for (int fps : FpsApplier.detectSupportedFps(this))
            segBar.addRate(fps, String.valueOf(fps), 1f);
        rateRow.addView(segBar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Shown instead of the rate picker while Beast mode is on
        beastStrip = UiHelper.text(this, "", 14, UiHelper.COL_BEAST, UiHelper.TF_BOLD);
        beastStrip.setGravity(Gravity.CENTER);
        beastStrip.setSingleLine(true);
        beastStrip.setBackground(UiHelper.pillBg(this, UiHelper.alpha(UiHelper.COL_BEAST, 0.12f),
            UiHelper.alpha(UiHelper.COL_BEAST, 0.45f), 16));
        rateRow.addView(beastStrip, new LinearLayout.LayoutParams(0, dp(50), 1f));

        // Beast mode logo, always at the side of the rate picker
        beastBtn = new FrameLayout(this);
        beastIcon = new ImageView(this);
        beastIcon.setImageResource(R.drawable.ui_beast);
        beastBtn.addView(beastIcon, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
        beastBtn.setClickable(true);
        beastBtn.setOnClickListener(v -> onBeastTap(v));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(50), dp(50));
        bp.leftMargin = dp(8);
        rateRow.addView(beastBtn, bp);
        hero.addView(rateRow);

        applyBtn = UiHelper.button(this, "", R.drawable.ui_zap, true, v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            applyNow(true, "Manual tap");
        });
        ((LinearLayout.LayoutParams) applyBtn.getLayoutParams()).topMargin = dp(12);
        hero.addView(applyBtn);

        lastApplied = UiHelper.text(this, "", 12, UiHelper.COL_DIM, UiHelper.TF_REGULAR);
        lastApplied.setGravity(Gravity.CENTER);
        lastApplied.setPadding(0, dp(12), 0, 0);
        hero.addView(lastApplied, UiHelper.matchWrap(this, 0));
        return hero;
    }

    private void updateSegments() {
        if (!FpsApplier.panelDetected(this)) {
            hzNumber.setText("—");
            hzNumber.getPaint().setShader(null);
            hzNumber.setTextColor(UiHelper.COL_DIM);
            hzNumber.invalidate();
            resetApplyButton();
            return;
        }
        int active = draft.beast() ? FpsApplier.getVerifiedMaxFps(this) : draft.target();
        int col = UiHelper.fpsColor(active);
        hzNumber.setText(String.valueOf(active));
        hzNumber.getPaint().setShader(new LinearGradient(0, 0, 0, hzNumber.getTextSize(),
            new int[]{0xFFFFFFFF, UiHelper.mix(col, 0xFFFFFFFF, 0.45f), col}, null, Shader.TileMode.CLAMP));
        hzNumber.invalidate();
        segBar.select(draft.target());
        resetApplyButton();
    }

    // ---------------------------------------------------------------- beast mode

    /** Beast logo: coloured when on, grey and faded when off. The rate picker hides while it is on. */
    private void updateBeastUi() {
        boolean detected = FpsApplier.panelDetected(this);
        boolean on = detected && draft.beast();
        segBar.setVisibility(on || !detected ? View.GONE : View.VISIBLE);
        beastStrip.setVisibility(on || !detected ? View.VISIBLE : View.GONE);
        beastStrip.setTextColor(detected ? UiHelper.COL_BEAST : UiHelper.COL_WARN);
        beastStrip.setText(detected ? getString(R.string.beast_strip, FpsApplier.getVerifiedMaxFps(this))
            : getString(R.string.panel_unknown));
        captionText.setText(getString(on ? R.string.beast_caption : R.string.main_caption));
        beastIcon.setImageTintList(android.content.res.ColorStateList.valueOf(
            on ? UiHelper.COL_BEAST : UiHelper.COL_DIM));
        beastIcon.setAlpha(on ? 1f : 0.5f);
        beastBtn.setBackground(UiHelper.ripple(this, on
            ? UiHelper.pillBg(this, UiHelper.alpha(UiHelper.COL_BEAST, 0.16f), UiHelper.alpha(UiHelper.COL_BEAST, 0.6f), 16)
            : UiHelper.pillBg(this, UiHelper.alpha(0xFF000000, 0.28f), UiHelper.COL_OUTLINE, 16), 16));
    }

    private void onBeastTap(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        if (draft.beast()) {
            setBeast(false);
        } else if (FpsApplier.getVerifiedMaxFps(this) <= 0) {
            android.widget.Toast.makeText(this, getString(R.string.beast_unavailable),
                android.widget.Toast.LENGTH_LONG).show();
        } else if (FpsApplier.isBeastWarnHidden(this)) {
            setBeast(true);
        } else {
            showBeastWarning();
        }
    }

    private void setBeast(boolean on) {
        draft.setBeast(on);
        updateBeastUi();
        updateSegments();
    }

    private void showBeastWarning() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        final android.app.Dialog[] holder = new android.app.Dialog[1];

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = UiHelper.icon(this, R.drawable.ui_beast, UiHelper.COL_BEAST, 30);
        ((LinearLayout.LayoutParams) ic.getLayoutParams()).rightMargin = dp(12);
        head.addView(ic);
        head.addView(UiHelper.text(this, getString(R.string.beast_title), 20, UiHelper.COL_TEXT, UiHelper.TF_BOLD));
        c.addView(head);

        TextView body = UiHelper.text(this, getString(R.string.beast_warn_body),
            14, UiHelper.COL_MUTED, UiHelper.TF_REGULAR);
        body.setLineSpacing(dp(4), 1f);
        body.setPadding(0, dp(14), 0, dp(10));
        c.addView(body);

        android.widget.CheckBox dontShow = new android.widget.CheckBox(this);
        dontShow.setText(getString(R.string.beast_dont_show));
        dontShow.setTextColor(UiHelper.COL_MUTED);
        dontShow.setTextSize(13.5f);
        dontShow.setButtonTintList(android.content.res.ColorStateList.valueOf(UiHelper.COL_BEAST));
        c.addView(dontShow, UiHelper.matchWrap(this, 0));

        TextView ok = UiHelper.button(this, getString(R.string.beast_ok), R.drawable.ui_check, true, v -> {
            if (dontShow.isChecked()) FpsApplier.setBeastWarnHidden(this, true);
            holder[0].dismiss();
            setBeast(true);
        });
        ok.setBackground(UiHelper.ripple(this, UiHelper.gradientBg(this, 18, UiHelper.COL_BEAST, 0xFFFFA24A), 18));
        ((LinearLayout.LayoutParams) ok.getLayoutParams()).topMargin = dp(14);
        c.addView(ok);
        TextView cancel = UiHelper.button(this, getString(R.string.beast_cancel), 0, false, v -> holder[0].dismiss());
        ((LinearLayout.LayoutParams) cancel.getLayoutParams()).topMargin = dp(8);
        c.addView(cancel);

        holder[0] = UiHelper.sheet(this, c);
        holder[0].show();
    }

    private void updateStatusChip(boolean pkFound) {
        if (!pkFound) {
            UiHelper.setChip(this, statusChip, getString(R.string.chip_pk_missing), UiHelper.COL_WARN);
        } else if (FpsApplier.isAutoEnabled(this)) {
            UiHelper.setChip(this, statusChip, getString(R.string.chip_active), UiHelper.COL_TEAL);
        } else {
            UiHelper.setChip(this, statusChip, getString(R.string.chip_manual), UiHelper.COL_PRIMARY);
        }
    }

    // ---------------------------------------------------------------- settings

    private View buildSettings() {
        LinearLayout card = UiHelper.card(this);
        card.setPadding(dp(14), dp(4), dp(16), dp(4));

        autoSwitch = UiHelper.styledSwitch(this);
        autoSwitch.setChecked(FpsApplier.isAutoEnabled(this));
        LinearLayout autoRow = UiHelper.row(this, R.drawable.ui_zap, UiHelper.COL_TEAL,
            getString(R.string.set_bg_title), getString(R.string.set_bg_sub), autoSwitch, null);
        autoRow.setOnClickListener(v -> autoSwitch.toggle());
        autoRow.setBackground(UiHelper.ripple(this, null, 14));
        autoSwitch.setOnCheckedChangeListener((b, on) -> {
            if (syncingUi) return;
            FpsApplier.setAutoEnabled(this, on);
            syncServiceState(on);
            UnlockWidget.updateAll(this);
            updateStatusChip(FpsApplier.isPowerKeeperInstalled(this));
            UnlockTileService.requestTileSync(this);
            appendLog("Background override " + (on ? "on" : "off"));
        });
        card.addView(autoRow);
        card.addView(UiHelper.divider(this, 58));

        Switch addSwitch = UiHelper.styledSwitch(this);
        addSwitch.setChecked(FpsApplier.isAutoAddGamesEnabled(this));
        LinearLayout addRow = UiHelper.row(this, R.drawable.ui_gamepad, UiHelper.COL_ACCENT2,
            getString(R.string.set_add_title), getString(R.string.set_add_sub), addSwitch, null);
        addRow.setOnClickListener(v -> addSwitch.toggle());
        addRow.setBackground(UiHelper.ripple(this, null, 14));
        addSwitch.setOnCheckedChangeListener((b, on) -> {
            FpsApplier.setAutoAddGamesEnabled(this, on);
            if (on) {
                selectedPackages = draft.suggestInstalledGames();
                resetApplyButton();
                loadInstalledAppsAsync();
            } else {
                appendLog("Auto-add games off");
            }
        });
        card.addView(addRow);
        return card;
    }

    // ---------------------------------------------------------------- tools + log

    private View buildTools() {
        LinearLayout rowL = new LinearLayout(this);
        rowL.setOrientation(LinearLayout.HORIZONTAL);
        rowL.setLayoutParams(UiHelper.matchWrap(this, 12));

        View tester = toolTile(R.drawable.ui_gauge, UiHelper.COL_PRIMARY, getString(R.string.tool_tester),
            getString(R.string.tool_tester_sub), v -> startActivity(new Intent(this, FpsTesterActivity.class)));
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        a.rightMargin = dp(6);
        rowL.addView(tester, a);

        View log = toolTile(R.drawable.ui_terminal, UiHelper.COL_MUTED, getString(R.string.tool_log),
            getString(R.string.tool_log_show), v -> toggleLog());
        logTileSub = UiHelper.subtitleOf(log);
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        b.leftMargin = dp(6);
        rowL.addView(log, b);
        return rowL;
    }

    private View toolTile(int icon, int color, String title, String sub, View.OnClickListener l) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(16), dp(16), dp(16), dp(16));
        t.setBackground(UiHelper.ripple(this, UiHelper.pillBg(this, UiHelper.COL_SURFACE, UiHelper.COL_OUTLINE, 22), 22));
        FrameLayout badge = UiHelper.iconBadge(this, icon, color);
        ((LinearLayout.LayoutParams) badge.getLayoutParams()).rightMargin = 0;
        ((LinearLayout.LayoutParams) badge.getLayoutParams()).bottomMargin = dp(14);
        t.addView(badge);
        t.addView(UiHelper.text(this, title, 15, UiHelper.COL_TEXT, UiHelper.TF_MEDIUM));
        TextView s = UiHelper.text(this, sub, 12.5f, UiHelper.COL_MUTED, UiHelper.TF_REGULAR);
        s.setTag("subtitle");
        s.setPadding(0, dp(5), 0, 0);
        t.addView(s);
        t.setOnClickListener(l);
        return t;
    }

    private View buildLog() {
        logCard = UiHelper.card(this);
        logCard.setBackground(UiHelper.pillBg(this, UiHelper.COL_LOG_BG, UiHelper.COL_OUTLINE, 20));
        logCard.setPadding(dp(16), dp(14), dp(16), dp(16));
        logCard.addView(UiHelper.overline(this, getString(R.string.log_title), UiHelper.COL_DIM));
        logText = UiHelper.text(this, "", 11.5f, UiHelper.COL_MUTED, Typeface.MONOSPACE);
        logText.setLineSpacing(dp(4), 1f);
        logText.setPadding(0, dp(10), 0, 0);
        logCard.addView(logText);
        logCard.setVisibility(View.GONE);
        return logCard;
    }

    private void toggleLog() {
        boolean show = logCard.getVisibility() != View.VISIBLE;
        logCard.setVisibility(show ? View.VISIBLE : View.GONE);
        logTileSub.setText(getString(show ? R.string.tool_log_hide : R.string.tool_log_show));
        if (show) {
            logCard.setAlpha(0f);
            logCard.setTranslationY(-dp(8));
            logCard.animate().alpha(1f).translationY(0).setDuration(200).start();
        }
    }

    // ---------------------------------------------------------------- apps

    private View buildAppsSection() {
        LinearLayout sec = new LinearLayout(this);
        sec.setOrientation(LinearLayout.VERTICAL);
        sec.setLayoutParams(UiHelper.matchWrap(this, 26));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.BOTTOM);
        head.setPadding(dp(4), 0, dp(4), 0);
        TextView title = UiHelper.text(this, getString(R.string.apps_title), 20, UiHelper.COL_TEXT, UiHelper.TF_BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        selectedCountText = UiHelper.text(this, "", 13, UiHelper.COL_PRIMARY, UiHelper.TF_MEDIUM);
        head.addView(selectedCountText);
        sec.addView(head);

        // Search field
        LinearLayout search = new LinearLayout(this);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setBackground(UiHelper.pillBg(this, UiHelper.COL_SURFACE, UiHelper.COL_OUTLINE, 18));
        search.setPadding(dp(16), 0, dp(12), 0);
        search.setLayoutParams(UiHelper.matchWrap(this, 12));
        search.addView(UiHelper.icon(this, R.drawable.ui_search, UiHelper.COL_DIM, 18));
        EditText box = new EditText(this);
        appSearch = box;
        box.setHint(getString(R.string.search_hint));
        box.setHintTextColor(UiHelper.COL_DIM);
        box.setTextColor(UiHelper.COL_TEXT);
        box.setTextSize(14.5f);
        box.setSingleLine(true);
        box.setBackground(null);
        box.setPadding(dp(12), dp(14), 0, dp(14));
        box.setText(searchText);
        box.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                searchText = s.toString();
                query = searchText.trim().toLowerCase(Locale.ROOT);
                refreshList();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        search.addView(box, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        sec.addView(search);

        // Filter chips
        android.widget.HorizontalScrollView chipScroll = new android.widget.HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setLayoutParams(UiHelper.matchWrap(this, 12));
        LinearLayout chips = new LinearLayout(this);
        String[] names = {getString(R.string.filter_all), getString(R.string.filter_selected),
            getString(R.string.filter_games), getString(R.string.filter_system)};
        for (int i = 0; i < FILTER_COUNT; i++) {
            final int f = i;
            TextView c = UiHelper.text(this, names[i], 13, UiHelper.COL_MUTED, UiHelper.TF_MEDIUM);
            c.setGravity(Gravity.CENTER);
            c.setPadding(dp(14), 0, dp(14), 0);
            c.setOnClickListener(v -> {
                filter = f;
                updateFilterChips();
                refreshList();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            lp.rightMargin = dp(8);
            chips.addView(c, lp);
            filterChips[i] = c;
        }
        chipScroll.addView(chips);
        sec.addView(chipScroll);

        // Explains why some games stay at 60 even when the override works
        LinearLayout note = new LinearLayout(this);
        note.setGravity(Gravity.TOP);
        note.setBackground(UiHelper.pillBg(this, UiHelper.alpha(UiHelper.COL_WARN, 0.06f),
            UiHelper.alpha(UiHelper.COL_WARN, 0.22f), 18));
        note.setPadding(dp(14), dp(12), dp(14), dp(12));
        note.setLayoutParams(UiHelper.matchWrap(this, 12));
        ImageView ni = UiHelper.icon(this, R.drawable.ui_info, UiHelper.COL_WARN, 18);
        ((LinearLayout.LayoutParams) ni.getLayoutParams()).rightMargin = dp(10);
        note.addView(ni);
        TextView nt = UiHelper.text(this,
            getString(R.string.limit_note),
            12.5f, UiHelper.COL_MUTED, UiHelper.TF_REGULAR);
        nt.setLineSpacing(dp(3), 1f);
        note.addView(nt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        sec.addView(note);

        listCard = UiHelper.card(this);
        listCard.setPadding(dp(14), dp(4), dp(14), dp(4));
        emptyText = UiHelper.text(this, getString(R.string.loading_apps), 14, UiHelper.COL_MUTED, UiHelper.TF_REGULAR);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setPadding(0, dp(28), 0, dp(28));
        listCard.addView(emptyText, UiHelper.matchWrap(this, 0));
        sec.addView(listCard);

        updateFilterChips();
        return sec;
    }

    private void updateFilterChips() {
        int apps = 0, selected = 0, games = 0, system = 0;
        for (AppEntry e : allApps) {
            if (e.isSystem) {
                system++;
            } else {
                apps++;
                if (selectedPackages.contains(e.packageName)) selected++;
                if (e.isGame) games++;
            }
        }
        int[] counts = {apps, selected, games, system};
        String[] names = {getString(R.string.filter_all), getString(R.string.filter_selected),
            getString(R.string.filter_games), getString(R.string.filter_system)};
        for (int i = 0; i < FILTER_COUNT; i++) {
            TextView c = filterChips[i];
            boolean on = i == filter;
            c.setText(allApps.isEmpty() ? names[i] : names[i] + "  " + counts[i]);
            c.setTextColor(on ? UiHelper.COL_PRIMARY : UiHelper.COL_MUTED);
            c.setBackground(UiHelper.ripple(this, UiHelper.pillBg(this,
                on ? UiHelper.alpha(UiHelper.COL_PRIMARY, 0.14f) : UiHelper.COL_SURFACE,
                on ? UiHelper.alpha(UiHelper.COL_PRIMARY, 0.45f) : UiHelper.COL_OUTLINE, 99), 99));
        }
        selectedCountText.setText(getString(R.string.apps_selected, selectedPackages.size()));
    }

    /**
     * Renders an app icon into a bitmap at the size we actually show (48dp). Apps ship icons up to
     * 512px+, and keeping ~150 of those decoded cost ~180 MB, which made HyperOS likely to kill
     * the process (and the background service with it).
     */
    private Drawable smallIcon(Drawable d) {
        int px = dp(48);
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        d.setBounds(0, 0, px, px);
        d.draw(c);
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("home_y", restoreHomeY > 0 ? restoreHomeY : homeScroll.getScrollY());
        state.putInt("app_filter", filter);
        state.putString("search", searchText);
        state.putBoolean("dismissed", batteryDismissed);
        state.putBoolean("log_open", logCard.getVisibility() == View.VISIBLE);
        state.putStringArrayList("log_lines", new ArrayList<>(logLines));
        if (lastSentAt > 0) {
            state.putLong("last_sent_at", lastSentAt);
            state.putInt("last_sent_rate", lastSentRate);
            state.putInt("last_sent_count", lastSentCount);
        }
    }

    private void showLastSent() {
        String ts = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(lastSentAt));
        lastApplied.setText(getString(R.string.last_sent, lastSentRate, lastSentCount, ts));
    }

    @Override
    protected void onDestroy() {
        // Let the icons go as soon as the screen closes; the background service stays light.
        ui.removeCallbacks(resetBtnRunnable);
        allApps.clear();
        if (listCard != null) listCard.removeAllViews();
        super.onDestroy();
    }

    private void loadInstalledAppsAsync() {
        int request = ++loadRequest;
        List<ApplicationInfo> entries = InstalledApps.get(this).entries();
        Set<String> selected = new java.util.HashSet<>(selectedPackages);
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<AppEntry> list = new ArrayList<>();
            for (ApplicationInfo ai : entries) {
                list.add(new AppEntry(pm.getApplicationLabel(ai).toString(), ai.packageName,
                    smallIcon(pm.getApplicationIcon(ai)), FpsApplier.isGameApp(ai), FpsApplier.isSystemApp(ai)));
            }
            Collections.sort(list, (a, b) -> {
                boolean as = selected.contains(a.packageName);
                boolean bs = selected.contains(b.packageName);
                if (as != bs) return as ? -1 : 1;
                if (a.isGame != b.isGame) return a.isGame ? -1 : 1;
                return a.label.compareToIgnoreCase(b.label);
            });
            ui.post(() -> {
                if (request != loadRequest || isDestroyed() || isFinishing()) return;
                allApps.clear();
                allApps.addAll(list);
                appsLoaded = true;
                updateFilterChips();
                refreshList();
                scrollToPicker();
                if (restoreHomeY > 0) {
                    homeScroll.post(() -> {
                        homeScroll.scrollTo(0, restoreHomeY);
                        restoreHomeY = 0;
                    });
                }
            });
        }).start();
    }

    private void refreshList() {
        listCard.removeAllViews();
        int shown = 0;
        for (AppEntry e : allApps) {
            // System apps only ever show in the System tab
            if (filter == FILTER_SYSTEM ? !e.isSystem : e.isSystem) continue;
            if (filter == FILTER_SELECTED && !selectedPackages.contains(e.packageName)) continue;
            if (filter == FILTER_GAMES && !e.isGame) continue;
            if (!query.isEmpty()
                && !e.label.toLowerCase(Locale.ROOT).contains(query)
                && !e.packageName.toLowerCase(Locale.ROOT).contains(query)) continue;
            if (e.row == null) buildRow(e);
            if (shown > 0) listCard.addView(UiHelper.divider(this, 56));
            if (e.row.getParent() != null) ((ViewGroup) e.row.getParent()).removeView(e.row);
            listCard.addView(e.row);
            shown++;
        }
        if (shown == 0) {
            emptyText.setText(getString(appsLoaded ? R.string.no_match : R.string.loading_apps));
            listCard.addView(emptyText);
        }
    }

    private void buildRow(AppEntry e) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), dp(2), dp(12));
        row.setBackground(UiHelper.ripple(this, null, 14));

        ImageView iv = new ImageView(this);
        iv.setImageDrawable(e.icon);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(42), dp(42));
        ip.rightMargin = dp(14);
        row.addView(iv, ip);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView name = UiHelper.text(this, e.label, 15.5f, UiHelper.COL_TEXT, UiHelper.TF_MEDIUM);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(name);

        TextView pkg = UiHelper.text(this, "", 12, UiHelper.COL_DIM, UiHelper.TF_REGULAR);
        pkg.setSingleLine(true);
        pkg.setEllipsize(TextUtils.TruncateAt.END);
        pkg.setPadding(0, dp(5), 0, 0);
        col.addView(pkg);
        e.sub = pkg;
        bindSub(e);
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout check = new FrameLayout(this);
        ImageView tick = UiHelper.icon(this, R.drawable.ui_check, UiHelper.COL_ON_PRIMARY, 16);
        check.addView(tick, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(26), dp(26));
        cp.leftMargin = dp(12);
        row.addView(check, cp);

        e.row = row;
        e.check = check;
        styleCheck(e, false);
        row.setOnLongClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            showGameSheet(e);
            return true;
        });
        row.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            boolean nowOn = !selectedPackages.contains(e.packageName);
            if (nowOn) selectedPackages.add(e.packageName);
            else selectedPackages.remove(e.packageName);
            draft.saveSelection(selectedPackages, e.packageName, e.isGame);
            appendLog((nowOn ? "Added " : "Removed ") + e.packageName);
            styleCheck(e, true);
            updateFilterChips();
            resetApplyButton();
            if (filter == FILTER_SELECTED) refreshList();
        });
    }

    /** Second line of a row: limit tag, per-game rate, package name. */
    private void bindSub(AppEntry e) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        GameCaps.Info caps = GameCaps.get(e.packageName);
        String tag = null;
        int tagColor = UiHelper.COL_TEAL;
        if (caps != null) {
            if (caps.cap == GameCaps.MAX_60) {
                tag = getString(R.string.tag_60);
                tagColor = UiHelper.COL_WARN;
            } else if (caps.cap == GameCaps.MAX_90) {
                tag = getString(R.string.tag_90);
                tagColor = UiHelper.COL_PRIMARY;
            } else {
                tag = getString(R.string.tag_120);
            }
        } else if (e.isGame) {
            tag = getString(R.string.tag_game);
        }
        if (tag != null) {
            sb.append(tag);
            sb.setSpan(new ForegroundColorSpan(tagColor), 0, tag.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new StyleSpan(Typeface.BOLD), 0, tag.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append("  \u00B7  ");
        }
        int own = draft.gameRate(e.packageName);
        if (own > 0) {
            int st = sb.length();
            sb.append(own + "Hz");
            sb.setSpan(new ForegroundColorSpan(UiHelper.COL_TEXT), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new StyleSpan(Typeface.BOLD), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append("  \u00B7  ");
        }
        sb.append(e.packageName);
        e.sub.setText(sb);
    }

    // ---------------------------------------------------------------- game sheet

    private void showGameSheet(AppEntry e) {
        GameCaps.Info caps = GameCaps.get(e.packageName);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        final android.app.Dialog[] holder = new android.app.Dialog[1];

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(e.icon);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(48), dp(48));
        ip.rightMargin = dp(14);
        head.addView(iv, ip);
        LinearLayout hc = new LinearLayout(this);
        hc.setOrientation(LinearLayout.VERTICAL);
        hc.addView(UiHelper.text(this, e.label, 18, UiHelper.COL_TEXT, UiHelper.TF_BOLD));
        TextView tagLine = UiHelper.text(this, "", 12.5f, UiHelper.COL_DIM, UiHelper.TF_REGULAR);
        tagLine.setPadding(0, dp(4), 0, 0);
        tagLine.setText(e.sub.getText());
        tagLine.setSingleLine(true);
        tagLine.setEllipsize(TextUtils.TruncateAt.END);
        hc.addView(tagLine);
        head.addView(hc, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        c.addView(head);

        TextView tip = UiHelper.text(this, caps != null ? caps.note : getString(R.string.dlg_tip_unknown),
            14, UiHelper.COL_MUTED, UiHelper.TF_REGULAR);
        tip.setLineSpacing(dp(4), 1f);
        tip.setPadding(0, dp(16), 0, 0);
        c.addView(tip);

        TextView rateLabel = UiHelper.overline(this, getString(R.string.dlg_rate_title), UiHelper.COL_DIM);
        rateLabel.setPadding(dp(2), dp(22), 0, dp(10));
        c.addView(rateLabel);
        RateSelector seg = new RateSelector(this, val -> {
            draft.setGameRate(e.packageName, val);
            bindSub(e);
            tagLine.setText(e.sub.getText());
            appendLog(e.packageName + " rate " + (val == 0 ? "default" : val + "Hz"));
            resetApplyButton();
        });
        String defaultLabel = draft.target() > 0 ? getString(R.string.dlg_rate_default, draft.target())
            : getString(R.string.dlg_rate_default_plain);
        seg.addRate(0, defaultLabel, 1.9f);
        for (int hz : FpsApplier.detectSupportedFps(this))
            seg.addRate(hz, String.valueOf(hz), 1f);
        seg.select(draft.gameRate(e.packageName));
        c.addView(seg, UiHelper.matchWrap(this, 0));

        TextView spacer = new TextView(this);
        c.addView(spacer, new LinearLayout.LayoutParams(1, dp(16)));
        TextView close = UiHelper.button(this, getString(R.string.dlg_close), 0, false, v -> holder[0].dismiss());
        close.setBackground(UiHelper.ripple(this, null, 18));
        close.setTextColor(UiHelper.COL_MUTED);
        ((LinearLayout.LayoutParams) close.getLayoutParams()).topMargin = dp(4);
        c.addView(close);

        holder[0] = UiHelper.sheet(this, c);
        holder[0].show();
    }

    private void styleCheck(AppEntry e, boolean animate) {
        boolean on = selectedPackages.contains(e.packageName);
        GradientDrawable d;
        if (on) {
            d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{UiHelper.COL_PRIMARY, UiHelper.COL_ACCENT2});
        } else {
            d = new GradientDrawable();
            d.setColor(0);
            d.setStroke(dp(2), UiHelper.COL_STROKE_STRONG);
        }
        d.setShape(GradientDrawable.OVAL);
        e.check.setBackground(d);
        e.check.getChildAt(0).setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        if (animate) {
            e.check.setScaleX(0.7f);
            e.check.setScaleY(0.7f);
            e.check.animate().scaleX(1f).scaleY(1f).setDuration(260)
                .setInterpolator(new OvershootInterpolator(2.5f)).start();
        }
    }

    // ---------------------------------------------------------------- apply

    private void syncServiceState(boolean enabled) {
        Intent svc = new Intent(this, Auto120Service.class);
        if (enabled && FpsApplier.hasAppliedProfile(this)) Auto120Service.ensureRunning(this);
        else stopService(svc);
    }

    private void applyNow(boolean animateBtn, String reason) {
        if (!FpsApplier.panelDetected(this)) {
            lastApplied.setText(getString(R.string.panel_unknown));
            return;
        }
        if (!FpsApplier.isPowerKeeperInstalled(this)) {
            lastApplied.setText(R.string.pk_not_found_long);
            return;
        }
        boolean leavingBeast = FpsApplier.isBeastEnabled(this) && !draft.beast();
        draft.publish();
        FpsApplier.ApplyResult res = FpsApplier.reapply(this);
        if (leavingBeast) android.widget.Toast.makeText(this, R.string.beast_off_note, android.widget.Toast.LENGTH_LONG).show();
        if (FpsApplier.isAutoEnabled(this)) Auto120Service.ensureRunning(this);
        Auto120Service.refreshNotification(this);
        UnlockTileService.requestTileSync(this);
        updateStatusChip(res.powerKeeperFound);
        appCountText.setText(getResources().getQuantityString(R.plurals.apps_count, res.count, res.count));
        if (res.count == 0) {
            boolean empty = draft.beast() ? FpsApplier.beastPackages(this).isEmpty() : selectedPackages.isEmpty();
            int message = empty ? R.string.widget_no_apps : R.string.widget_failed;
            lastApplied.setText(message);
            appendLog(getString(message));
            resetApplyButton();
            UnlockWidget.updateAll(this);
            return;
        }
        lastSentAt = System.currentTimeMillis();
        lastSentRate = res.fps;
        lastSentCount = res.count;
        showLastSent();

        String preview = "";
        if (!res.packages.isEmpty()) {
            int show = Math.min(2, res.packages.size());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < show; i++) {
                if (i > 0) sb.append(", ");
                sb.append(res.packages.get(i));
            }
            if (res.packages.size() > show) sb.append(" +").append(res.packages.size() - show);
            preview = " [" + sb + "]";
        }
        appendLog(reason + ": " + res.fps + "Hz → " + res.count + " apps"
            + (res.powerKeeperFound ? "" : " (PowerKeeper missing)") + preview);

        if (animateBtn) animateApplyButton(res);
        UnlockWidget.updateAll(this);
    }

    private void animateApplyButton(FpsApplier.ApplyResult res) {
        ui.removeCallbacks(resetBtnRunnable);
        applyBtn.animate().cancel();
        applyBtn.setScaleX(0.96f);
        applyBtn.setScaleY(0.96f);
        applyBtn.animate().scaleX(1f).scaleY(1f).setDuration(240)
            .setInterpolator(new DecelerateInterpolator()).start();
        if (res.powerKeeperFound) {
            applyBtn.setText(getString(R.string.btn_sent, res.fps, res.count));
            setButtonIcon(R.drawable.ui_check);
            applyBtn.setBackground(UiHelper.ripple(this,
                UiHelper.gradientBg(this, 18, UiHelper.COL_TEAL, 0xFF5EEAD4), 18));
        } else {
            applyBtn.setText(getString(R.string.btn_pk_not_found));
            applyBtn.setBackground(UiHelper.ripple(this,
                UiHelper.gradientBg(this, 18, UiHelper.COL_WARN, 0xFFFCD34D), 18));
        }
        ui.postDelayed(resetBtnRunnable, 1500);
    }

    private void resetApplyButton() {
        int count = draft.beast() ? FpsApplier.beastPackages(this).size() : selectedPackages.size();
        appCountText.setText(getResources().getQuantityString(R.plurals.apps_count, count, count));
        boolean detected = FpsApplier.panelDetected(this);
        applyBtn.setEnabled(detected);
        applyBtn.setAlpha(detected ? 1f : 0.5f);
        applyBtn.setText(detected ? getString(draft.hasChanges() ? R.string.apply_changes : R.string.btn_apply)
            : getString(R.string.panel_unknown));
        setButtonIcon(R.drawable.ui_zap);
        UiHelper.styleButton(this, applyBtn, true);
    }

    private void setButtonIcon(int res) {
        Drawable d = getDrawable(res).mutate();
        d.setTint(UiHelper.COL_ON_PRIMARY);
        d.setBounds(0, 0, dp(18), dp(18));
        applyBtn.setCompoundDrawables(d, null, null, null);
    }

    private void appendLog(String msg) {
        String ts = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        logLines.addLast(ts + "  " + msg);
        while (logLines.size() > 6) logLines.removeFirst();
        logText.setText(TextUtils.join("\n", logLines));
    }

    private int dp(float v) {
        return UiHelper.dp(this, v);
    }
}
