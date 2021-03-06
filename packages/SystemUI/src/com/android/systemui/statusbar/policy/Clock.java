/*
 * Copyright (C) 2006 The Android Open Source Project
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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.DateFormat;
import android.text.style.CharacterStyle;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import com.android.internal.R;

/**
 * This widget display an analogic clock with two hands for hours and minutes.
 */
public class Clock extends TextView {
    protected boolean mAttached;
    protected Calendar mCalendar;
    protected SimpleDateFormat mClockFormat;

    public static final int AM_PM_STYLE_NORMAL = 0;
    public static final int AM_PM_STYLE_SMALL = 1;
    public static final int AM_PM_STYLE_GONE = 2;

    protected int mAmPmStyle = AM_PM_STYLE_GONE;

    public static final int STYLE_HIDE_CLOCK = 0;
    public static final int STYLE_CLOCK_RIGHT = 1;
    public static final int STYLE_CLOCK_CENTER = 2;

    protected int mClockStyle = STYLE_CLOCK_RIGHT;

    public static final int WEEKDAY_STYLE_GONE = 0;
    public static final int WEEKDAY_STYLE_SMALL = 1;
    public static final int WEEKDAY_STYLE_NORMAL = 2;

    protected int mWeekday = WEEKDAY_STYLE_GONE;

    private final boolean DEBUG = false;
    private static final String TAG = "Statusbar :Clock";

    protected boolean mShowClockDuringLockscreen = false;
    protected int mClockColor = com.android.internal.R.color.holo_blue_light;

    public Clock(Context context) {
        this(context, null);
    }

    public Clock(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Clock(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();

            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);

            getContext().registerReceiver(mIntentReceiver, filter, null,
                    getHandler());
        }

        // NOTE: It's safe to do these after registering the receiver since the
        // receiver always runs
        // in the main thread, therefore the receiver can't run before this
        // method returns.

        // The time zone may have changed while the receiver wasn't registered,
        // so update the Time
        mCalendar = Calendar.getInstance(TimeZone.getDefault());

        SettingsObserver settingsObserver = new SettingsObserver(new Handler());
        settingsObserver.observe();
        updateSettings();
        // Make sure we update to the current time
        updateClock();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            getContext().unregisterReceiver(mIntentReceiver);
            mAttached = false;
        }
    }

    protected final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_TIMEZONE_CHANGED)) {
                String tz = intent.getStringExtra("time-zone");
                mCalendar = Calendar.getInstance(TimeZone.getTimeZone(tz));
                if (mClockFormat != null) {
                    mClockFormat.setTimeZone(mCalendar.getTimeZone());
                }
            }
            updateClock();
        }
    };

    protected final void updateClock() {
        mCalendar.setTimeInMillis(System.currentTimeMillis());
        setText(getSmallTime());
    }

    protected final CharSequence getSmallTime() {
        final char MAGIC1 = '\uEF00';
        final char MAGIC2 = '\uEF01';

        // new magic for weekday display
        final char MAGIC3 = '\uEF02';
        final char MAGIC4 = '\uEF03';

        Context context = getContext();
        boolean b24 = DateFormat.is24HourFormat(context);
        int res;

        if (b24) {
            res = R.string.twenty_four_hour_time_format;
        } else {
            res = R.string.twelve_hour_time_format;
        }

        String format = context.getString(res);

        if (mWeekday != WEEKDAY_STYLE_GONE) {
            format = MAGIC3 + "EEE " + MAGIC4 + format;
        }

        /*
         * Search for an unquoted "a" in the format string, so we can add dummy
         * characters around it to let us find it again after formatting and
         * change its size.
         */
        if (mAmPmStyle != AM_PM_STYLE_NORMAL) {
            int a = -1;
            boolean quoted = false;
            for (int i = 0; i < format.length(); i++) {
                char c = format.charAt(i);

                if (c == '\'') {
                    quoted = !quoted;
                }
                if (!quoted && c == 'a') {
                    a = i;
                    break;
                }
            }

            if (a >= 0) {
                // Move a back so any whitespace before AM/PM is also in the
                // alternate size.
                final int b = a;
                while (a > 0 && Character.isWhitespace(format.charAt(a - 1))) {
                    a--;
                }
                format = format.substring(0, a) + MAGIC1
                        + format.substring(a, b) + "a" + MAGIC2
                        + format.substring(b + 1);
            }
            if (DEBUG) {
                Log.d(TAG, "mClockFormat: {" + format + "}");
                Log.d(TAG, String.format("MAGIC1: {%s}	MAGIC2: {%s}	MAGIC3: {%s}	MAGIC4: {%s}", MAGIC1, MAGIC2, MAGIC3, MAGIC4));
            }
            mClockFormat = new SimpleDateFormat(format);
        }
        mClockFormat = new SimpleDateFormat(format);
        String result = mClockFormat.format(mCalendar.getTime());

        SpannableStringBuilder formatted = new SpannableStringBuilder(result);
        if (mAmPmStyle != AM_PM_STYLE_NORMAL) {
            int magic1 = result.indexOf(MAGIC1);
            int magic2 = result.indexOf(MAGIC2);
            if (magic1 >= 0 && magic2 > magic1) {
                if (mAmPmStyle == AM_PM_STYLE_GONE) {
                    formatted.delete(magic1, magic2 + 1);
                } else {
                    if (mAmPmStyle == AM_PM_STYLE_SMALL) {
                        CharacterStyle style = new RelativeSizeSpan(0.7f);
                        formatted.setSpan(style, magic1, magic2,
                                Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                    }
                    formatted.delete(magic2, magic2 + 1);
                    formatted.delete(magic1, magic1 + 1);
                }
            }
        }
        if (mWeekday != WEEKDAY_STYLE_NORMAL) {
            // always in front of am/pm
            int magic3 = result.indexOf(MAGIC3);
            int magic4 = result.indexOf(MAGIC4);
            if (magic3 >= 0 && magic4 > magic3) {
                if (mWeekday == WEEKDAY_STYLE_GONE) {
                    formatted.delete(magic3, magic4 + 1);
                } else {
                    if (mWeekday == WEEKDAY_STYLE_SMALL) {
                        CharacterStyle style = new RelativeSizeSpan(0.7f);
                        formatted.setSpan(style, magic3, magic4,
                                Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                    }
                    formatted.delete(magic4, magic4 + 1);
                    formatted.delete(magic3, magic3 + 1);
                }
            }
        }
        return formatted;
    }

    public void updateVisibilityFromStatusBar(boolean show) {
        if (mClockStyle == STYLE_CLOCK_RIGHT)
            setVisibility(show ? View.VISIBLE : View.GONE);

    }

    protected class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.STATUSBAR_CLOCK_AM_PM_STYLE),
                    false, this);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.STATUSBAR_CLOCK_STYLE), false,
                    this);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.STATUSBAR_CLOCK_COLOR), false,
                    this);
            resolver.registerContentObserver(
                    Settings.System
                            .getUriFor(Settings.System.STATUSBAR_CLOCK_LOCKSCREEN_HIDE),
                    false, this);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.STATUSBAR_CLOCK_WEEKDAY), false,
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
        int defaultColor = getResources().getColor(
                com.android.internal.R.color.holo_blue_light);

        mAmPmStyle = Settings.System.getInt(resolver,
                Settings.System.STATUSBAR_CLOCK_AM_PM_STYLE, AM_PM_STYLE_GONE);

        mClockColor = Settings.System.getInt(resolver,
                Settings.System.STATUSBAR_CLOCK_COLOR, defaultColor);
        if (mClockColor == Integer.MIN_VALUE) {
            // flag to reset the color
            mClockColor = defaultColor;
        }
        setTextColor(mClockColor);

        mClockStyle = Settings.System.getInt(resolver,
                Settings.System.STATUSBAR_CLOCK_STYLE, 1);
        mWeekday = Settings.System.getInt(resolver,
                Settings.System.STATUSBAR_CLOCK_WEEKDAY, 0);
        updateClockVisibility();

        mShowClockDuringLockscreen = (Settings.System.getInt(resolver,
                Settings.System.STATUSBAR_CLOCK_LOCKSCREEN_HIDE, 1) == 1);
        updateClock();
    }

    protected void updateClockVisibility() {
        if (mClockStyle == STYLE_CLOCK_RIGHT)
            setVisibility(View.VISIBLE);
        else
            setVisibility(View.GONE);
    }
}
