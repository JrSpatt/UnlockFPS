package com.spatt.unlockfps;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.PathInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.IntConsumer;

/** Segmented rate picker. The moving highlight never commits an intermediate rate. */
final class RateSelector extends LinearLayout {
    private final Activity activity;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int lastFillColor, lastFillHeight;
    private final RectF bounds = new RectF();
    private final ArgbEvaluator colors = new ArgbEvaluator();
    private final int radius;
    private final int slop;
    private final IntConsumer onChoice;
    private TextView selected;
    private ValueAnimator motion;
    private float left, right, touchX, touchY;
    private int tint;
    private boolean dragging;

    RateSelector(Activity activity, IntConsumer onChoice) {
        super(activity);
        this.activity = activity;
        this.onChoice = onChoice;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(getResources().getDisplayMetrics().density);
        radius = UiHelper.dp(activity, 12);
        slop = ViewConfiguration.get(activity).getScaledTouchSlop();
        int inset = UiHelper.dp(activity, 4);
        setPadding(inset, inset, inset, inset);
        setBackground(UiHelper.pillBg(activity, UiHelper.alpha(0xff000000, .28f), UiHelper.COL_OUTLINE, 16));
    }

    void addRate(int rate, String label, float weight) {
        TextView tab = UiHelper.text(activity, label, 14, UiHelper.COL_MUTED, UiHelper.TF_BOLD);
        tab.setTag(rate);
        tab.setGravity(Gravity.CENTER);
        tab.setSingleLine(true);
        tab.setAutoSizeTextTypeUniformWithConfiguration(9, 14, 1, TypedValue.COMPLEX_UNIT_SP);
        tab.setBackground(UiHelper.ripple(activity, null, 12));
        tab.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            select(rate);
            dragging = false;
            onChoice.accept(rate);
        });
        addView(tab, new LayoutParams(0, UiHelper.dp(activity, 42), weight));
    }

    private int color(int rate) {
        return rate == 0 ? UiHelper.COL_PRIMARY : UiHelper.fpsColor(rate);
    }

    void select(int rate) {
        TextView next = null;
        for (int i = 0; i < getChildCount(); i++) {
            TextView tab = (TextView) getChildAt(i);
            int value = (int) tab.getTag();
            boolean on = value == rate;
            tab.setSelected(on);
            tab.setTextColor(on ? 0xffffffff : value == 0 ? UiHelper.COL_MUTED : color(value));
            if (on) next = tab;
        }
        if (next == selected && !dragging) return;
        boolean animate = selected != null && next != null && next.getWidth() > 0 && isShown()
            && ValueAnimator.areAnimatorsEnabled();
        selected = next;
        stopMotion();
        if (!animate) {
            snap();
            return;
        }
        final float fromLeft = left, fromRight = right;
        final float toLeft = next.getLeft(), toRight = next.getRight();
        final int fromColor = tint, toColor = color(rate);
        motion = ValueAnimator.ofFloat(0f, 1f);
        motion.setDuration(255);
        motion.setInterpolator(new PathInterpolator(.22f, .8f, .25f, 1f));
        motion.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            float stretch = Math.min(radius * .45f, Math.abs(toLeft - fromLeft) * .08f)
                * (float) Math.sin(Math.PI * t);
            left = fromLeft + (toLeft - fromLeft) * t - stretch;
            right = fromRight + (toRight - fromRight) * t + stretch;
            tint = (int) colors.evaluate(t, fromColor, toColor);
            invalidate();
        });
        motion.start();
    }

    private void stopMotion() {
        if (motion != null) motion.cancel();
        motion = null;
    }

    private void snap() {
        if (selected != null) {
            left = selected.getLeft();
            right = selected.getRight();
            tint = color((int) selected.getTag());
        }
        invalidate();
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed || motion == null || !motion.isRunning()) snap();
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        if (selected != null && right > left) {
            bounds.set(left + 1, getPaddingTop() + 1, right - 1, getHeight() - getPaddingBottom() - 1);
            if (lastFillColor != tint || lastFillHeight != getHeight()) {
                fill.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                    UiHelper.alpha(UiHelper.mix(tint, 0xffffffff, .35f), .5f),
                    UiHelper.alpha(tint, .24f), Shader.TileMode.CLAMP));
                rim.setColor(UiHelper.alpha(UiHelper.mix(tint, 0xffffffff, .5f), .85f));
                lastFillColor = tint;
                lastFillHeight = getHeight();
            }
            canvas.drawRoundRect(bounds, radius, radius, fill);
            canvas.drawRoundRect(bounds, radius, radius, rim);
        }
        super.dispatchDraw(canvas);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            touchX = e.getX();
            touchY = e.getY();
            dragging = false;
        } else if (e.getActionMasked() == MotionEvent.ACTION_MOVE && selected != null) {
            float dx = Math.abs(e.getX() - touchX);
            if (dx > slop && dx > Math.abs(e.getY() - touchY)) {
                dragging = true;
                stopMotion();
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
        }
        return super.onInterceptTouchEvent(e);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!dragging) return super.onTouchEvent(e);
        float x = Math.max(getPaddingLeft(), Math.min(getWidth() - getPaddingRight() - 1, e.getX()));
        if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float width = selected.getWidth();
            left = Math.max(getPaddingLeft(), Math.min(getWidth() - getPaddingRight() - width, x - width / 2));
            right = left + width;
            invalidate();
        } else if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            for (int i = 0; i < getChildCount(); i++) {
                View tab = getChildAt(i);
                if (x >= tab.getLeft() && x < tab.getRight()) {
                    tab.performClick();
                    break;
                }
            }
            dragging = false;
        } else if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            dragging = false;
            snap();
        }
        return true;
    }

    @Override protected void onDetachedFromWindow() {
        stopMotion();
        super.onDetachedFromWindow();
    }
}
