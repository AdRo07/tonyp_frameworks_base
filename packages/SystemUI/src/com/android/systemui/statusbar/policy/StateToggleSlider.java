/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.systemui.R;

public class StateToggleSlider extends RelativeLayout implements  SeekBar.OnSeekBarChangeListener, View.OnClickListener {
    private static final String TAG = "StatusBar.ToggleSlider";

    public interface Listener {
        public void onInit(StateToggleSlider v);
        public void onChanged(StateToggleSlider v, boolean tracking, int state, int value);
    }

    private Listener mListener;
    private boolean mTracking;
    private int mState;
    private StateToggleSliderItem[]mStates;

    private SeekBar mSlider;
    private TextView mLabel;
    private ImageView mImage;

    public StateToggleSlider(Context context) {
        this(context, null);
    }

    public StateToggleSlider(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StateToggleSlider(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        View.inflate(context, R.layout.status_bar_state_toggle_slider, this);

        final Resources res = context.getResources();
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ToggleSlider,
                defStyle, 0);

        mSlider = (SeekBar)findViewById(R.id.slider);
        mSlider.setOnSeekBarChangeListener(this);

        mLabel = (TextView)findViewById(R.id.label);
        mLabel.setText(a.getString(R.styleable.ToggleSlider_text));

        mImage = (ImageView)findViewById(R.id.image);
        mImage.setOnClickListener(this);
        mImage.setClickable(true);
        a.recycle();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mListener != null) {
            mListener.onInit(this);
        }
    }

    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (mListener != null) {
            //mListener.onChanged(this, mTracking, mState, progress);
        }
    }

    public void onStartTrackingTouch(SeekBar seekBar) {
        mTracking = true;
        if (mListener != null) {
            //mListener.onChanged(this, mTracking, mState, mSlider.getProgress());
        }
    }

    public void onStopTrackingTouch(SeekBar seekBar) {
        mTracking = false;
        if (mListener != null) {
            mListener.onChanged(this, mTracking, mState, mSlider.getProgress());
        }
    }
    
    @Override
    public void onClick(View v) {
        nextState();
        if (mListener != null) {
            mListener.onChanged(this, mTracking, mState, mSlider.getProgress());
        }
    }

    public void setOnChangedListener(Listener l) {
        mListener = l;
    }

    public void setMax(int max) {
        mSlider.setMax(max);
    }

    public void setValue(int value) {
        mSlider.setProgress(value);
    }
    
    public void setItems(StateToggleSliderItem[]a) {
        mStates = a;
    }
    
    public StateToggleSliderItem[] getItems() {
        return mStates;
    }
    
    public void changeToState(int id) {
        if(id == mState)return;
        if(id < 0 || id >= mStates.length)return;
        mState = id;
        update();
    }
    
    public void nextState() {
        int i;
        if(mState + 1 >= mStates.length)i = 0;
        else i = mState + 1;
        mState = i;
        update();
    }
    
    private void update() {
        Drawable thumb;
        Drawable slider;
        final Resources res = getContext().getResources();
        if (mStates[mState].mDisableSlider) {
            thumb = res.getDrawable(
                    com.android.internal.R.drawable.scrubber_control_disabled_holo);
            slider = res.getDrawable(
                    R.drawable.status_bar_settings_slider_disabled);
        } else {
            thumb = res.getDrawable(
                    com.android.internal.R.drawable.scrubber_control_selector_holo);
            slider = res.getDrawable(
                    com.android.internal.R.drawable.scrubber_progress_horizontal_holo_dark);
        }
        mSlider.setThumb(thumb);
        mSlider.setProgressDrawable(slider);
        mLabel.setText(mStates[mState].mText);
        mImage.setImageResource(mStates[mState].mImageRessource);
    }
    
    public static class StateToggleSliderItem {
        public final int mImageRessource;
        public final String mText;
        public final boolean mDisableSlider;
        
        public StateToggleSliderItem(int image, String text, boolean disable) {
            mImageRessource = image;
            mText = text;
            mDisableSlider = disable;
        }
    }
}
