/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.AttributeSet;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.IWindowManager;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.Clock.SettingsObserver;

public class KeyButtonView extends ImageView {
    protected static final String TAG = "StatusBar.KeyButtonView";
    private static final boolean DEBUG = false;

    final float GLOW_MAX_SCALE_FACTOR = 1.8f;
    float BUTTON_QUIESCENT_ALPHA = 1f;

    public IWindowManager mWindowManager;
    long mDownTime;
    int mCode;
    int mTouchSlop;
    Drawable mGlowBG;
    float mGlowAlpha = 0f, mGlowScale = 1f, mDrawingAlpha = 1f;
    protected boolean mSupportsLongpress = true;
    protected boolean mHandlingLongpress = false;
    RectF mRect = new RectF(0f, 0f, 0f, 0f);

    int durationSpeedOn = 500;
    int durationSpeedOff = 50;
    int mGlowBGColor = Integer.MIN_VALUE;

    Runnable mCheckLongPress = new Runnable() {
        public void run() {
            if (isPressed()) {
                setHandlingLongpress(true);
                if (!performLongClick() && (mCode != 0)) {
                    // we tried to do custom long click and failed - let's
                    // do long click on the primary 'key'
                    sendEvent(KeyEvent.ACTION_DOWN, KeyEvent.FLAG_LONG_PRESS);
                    sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
                }
            }
        }
    };

    public KeyButtonView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyButtonView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.KeyButtonView,
                defStyle, 0);

        mCode = a.getInteger(R.styleable.KeyButtonView_keyCode, 0);

        mSupportsLongpress = a.getBoolean(R.styleable.KeyButtonView_keyRepeat, true);

        mGlowBG = a.getDrawable(R.styleable.KeyButtonView_glowBackground);
        if (mGlowBG != null) {
            mDrawingAlpha = BUTTON_QUIESCENT_ALPHA;
        }

        a.recycle();

        mWindowManager = IWindowManager.Stub.asInterface(
                ServiceManager.getService(Context.WINDOW_SERVICE));

        setClickable(true);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        SettingsObserver settingsObserver = new SettingsObserver(new Handler());
        settingsObserver.observe();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mGlowBG != null) {
            canvas.save();
            final int w = getWidth();
            final int h = getHeight();
            canvas.scale(mGlowScale, mGlowScale, w * 0.5f, h * 0.5f);
            mGlowBG.setBounds(0, 0, w, h);
            mGlowBG.setAlpha((int) (mGlowAlpha * 255));
            mGlowBG.draw(canvas);
            canvas.restore();
            mRect.right = w;
            mRect.bottom = h;
            canvas.saveLayerAlpha(mRect, (int) (mDrawingAlpha * 255), Canvas.ALL_SAVE_FLAG);
        }
        super.onDraw(canvas);
        if (mGlowBG != null) {
            canvas.restore();
        }
    }

    public void setSupportsLongPress(boolean supports) {
        mSupportsLongpress = supports;
    }

    public void setHandlingLongpress(boolean handling) {
        mHandlingLongpress = handling;
    }

    public void setCode(int code) {
        mCode = code;
    }

    public int getCode() {
        return mCode;
    }

    public void setGlowBackground(int id) {
        mGlowBG = getResources().getDrawable(id);
        if (mGlowBG != null) {
            int defaultColor = mContext.getResources().getColor(
                    com.android.internal.R.color.holo_blue_light);
            ContentResolver resolver = mContext.getContentResolver();
            mGlowBGColor = Settings.System.getInt(resolver,
                    Settings.System.NAVIGATION_BAR_GLOW_TINT, defaultColor);

            if (mGlowBGColor == Integer.MIN_VALUE) {
                mGlowBGColor = defaultColor;
            }
            mGlowBG.setColorFilter(null);
            mGlowBG.setColorFilter(mGlowBGColor, PorterDuff.Mode.SRC_ATOP);

            mDrawingAlpha = BUTTON_QUIESCENT_ALPHA;
        }
    }

    public float getDrawingAlpha() {
        if (mGlowBG == null)
            return 0;
        return mDrawingAlpha;
    }

    public void setDrawingAlpha(float x) {
        if (mGlowBG == null)
            return;
        mDrawingAlpha = x;
        invalidate();
    }

    public float getGlowAlpha() {
        if (mGlowBG == null)
            return 0;
        return mGlowAlpha;
    }

    public void setGlowAlpha(float x) {
        if (mGlowBG == null)
            return;
        mGlowAlpha = x;
        invalidate();
    }

    public float getGlowScale() {
        if (mGlowBG == null)
            return 0;
        return mGlowScale;
    }

    public void setGlowScale(float x) {
        if (mGlowBG == null)
            return;
        mGlowScale = x;
        final float w = getWidth();
        final float h = getHeight();
        if (GLOW_MAX_SCALE_FACTOR <= 1.0f) {
            // this only works if we know the glow will never leave our bounds
            invalidate();
        } else {
            final float rx = (w * (GLOW_MAX_SCALE_FACTOR - 1.0f)) / 2.0f + 1.0f;
            final float ry = (h * (GLOW_MAX_SCALE_FACTOR - 1.0f)) / 2.0f + 1.0f;
            com.android.systemui.SwipeHelper.invalidateGlobalRegion(
                    this,
                    new RectF(getLeft() - rx,
                            getTop() - ry,
                            getRight() + rx,
                            getBottom() + ry));

            // also invalidate our immediate parent to help avoid situations
            // where nearby glows
            // interfere
            ((View) getParent()).invalidate();
        }
    }

    public void setPressed(boolean pressed) {
        if (mGlowBG != null) {
            if (pressed != isPressed()) {
                AnimatorSet as = new AnimatorSet();
                if (pressed) {
                    if (mGlowScale < GLOW_MAX_SCALE_FACTOR)
                        mGlowScale = GLOW_MAX_SCALE_FACTOR;
                    if (mGlowAlpha < BUTTON_QUIESCENT_ALPHA)
                        mGlowAlpha = BUTTON_QUIESCENT_ALPHA;
                    setDrawingAlpha(BUTTON_QUIESCENT_ALPHA);
                    as.playTogether(
                            ObjectAnimator.ofFloat(this, "glowAlpha", 1f),
                            ObjectAnimator.ofFloat(this, "glowScale", GLOW_MAX_SCALE_FACTOR)
                            );
                    as.setDuration(durationSpeedOff);
                } else {
                    as.playTogether(
                            ObjectAnimator.ofFloat(this, "glowAlpha", 0f),
                            ObjectAnimator.ofFloat(this, "glowScale", 1f),
                            ObjectAnimator.ofFloat(this, "drawingAlpha", BUTTON_QUIESCENT_ALPHA)
                            );
                    as.setDuration(durationSpeedOn);
                }
                as.start();
            }
        }
        super.setPressed(pressed);
    }

    public boolean onTouchEvent(MotionEvent ev) {
        final int action = ev.getAction();
        int x, y;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                // Slog.d("KeyButtonView", "press");
                setHandlingLongpress(false);
                mDownTime = SystemClock.uptimeMillis();
                setPressed(true);
                if (mCode != 0) {
                    sendEvent(KeyEvent.ACTION_DOWN, 0, mDownTime);
                } else {
                    // Provide the same haptic feedback that the system offers
                    // for virtual keys.
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                }
                if (mSupportsLongpress) {
                    removeCallbacks(mCheckLongPress);
                    postDelayed(mCheckLongPress, ViewConfiguration.getLongPressTimeout());
                }
                break;
            case MotionEvent.ACTION_MOVE:
                x = (int) ev.getX();
                y = (int) ev.getY();
                setPressed(x >= -mTouchSlop
                        && x < getWidth() + mTouchSlop
                        && y >= -mTouchSlop
                        && y < getHeight() + mTouchSlop);
                break;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                if (mCode != 0) {
                    sendEvent(KeyEvent.ACTION_UP, KeyEvent.FLAG_CANCELED);
                }
                if (mSupportsLongpress) {
                    removeCallbacks(mCheckLongPress);
                }
                break;
            case MotionEvent.ACTION_UP:
                final boolean doIt = isPressed();
                setPressed(false);
                Log.d(TAG, "in ACTION_UP DoIT:" + doIt);
                Log.d(TAG, "in ACTION_UP isPressed():" + isPressed());
                if (mCode != 0) {
                    if ((doIt) && (!mHandlingLongpress)) {
                        sendEvent(KeyEvent.ACTION_UP, 0);
                        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
                        playSoundEffect(SoundEffectConstants.CLICK);
                    } else {
                        sendEvent(KeyEvent.ACTION_UP, KeyEvent.FLAG_CANCELED);
                    }
                } else {
                    // no key code, just a regular ImageView
                    if ((doIt) && (!mHandlingLongpress)) {
                        performClick();
                    }
                }
                if (mSupportsLongpress) {
                    removeCallbacks(mCheckLongPress);
                }
                break;
        }

        return true;
    }

    void sendEvent(int action, int flags) {
        sendEvent(action, flags, SystemClock.uptimeMillis());
    }

    void sendEvent(int action, int flags, long when) {
        final int repeatCount = (flags & KeyEvent.FLAG_LONG_PRESS) != 0 ? 1 : 0;
        final KeyEvent ev = new KeyEvent(mDownTime, when, action, mCode, repeatCount,
                0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                flags | KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                InputDevice.SOURCE_KEYBOARD);
        try {
            // Slog.d(TAG, "injecting event " + ev);
            mWindowManager.injectInputEventNoWait(ev);
        } catch (RemoteException ex) {
            // System process is dead
        }
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.NAVIGATION_BAR_TINT), false,
                    this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.NAVIGATION_BAR_GLOW_TINT), false,
                    this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.NAVIGATION_BAR_GLOW_DURATION[1]),
                    false,
                    this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.NAVIGATION_BAR_BUTTON_ALPHA), false,
                    this);
            updateSettings();
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    protected void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();

        durationSpeedOff = Settings.System.getInt(resolver,
                Settings.System.NAVIGATION_BAR_GLOW_DURATION[0], 10);
        durationSpeedOn = Settings.System.getInt(resolver,
                Settings.System.NAVIGATION_BAR_GLOW_DURATION[1], 100);
        BUTTON_QUIESCENT_ALPHA = Settings.System.getFloat(resolver,
                Settings.System.NAVIGATION_BAR_BUTTON_ALPHA,
                0.6f);
        setDrawingAlpha(BUTTON_QUIESCENT_ALPHA);
        
        if (mGlowBG != null) {
            int defaultColor = mContext.getResources().getColor(
                    com.android.internal.R.color.holo_blue_light);
            mGlowBGColor = Settings.System.getInt(resolver,
                    Settings.System.NAVIGATION_BAR_GLOW_TINT, defaultColor);
            
            if (mGlowBGColor == Integer.MIN_VALUE) {
                mGlowBGColor = defaultColor;
            }
            mGlowBG.setColorFilter(null);
            mGlowBG.setColorFilter(mGlowBGColor, PorterDuff.Mode.SRC_ATOP);
        }

        try {
            int color = Settings.System.getInt(resolver,
                    Settings.System.NAVIGATION_BAR_TINT);

            if (color == Integer.MIN_VALUE) {
                setColorFilter(null);
            } else {
                setColorFilter(null);
                setColorFilter(Settings.System.getInt(resolver,
                        Settings.System.NAVIGATION_BAR_TINT));
            }
        } catch (SettingNotFoundException e) {
        }
        invalidate();
    }
}
