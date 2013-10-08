/*
 * Copyright (C) 2013 Firtecy
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

package com.android.systemui.statusbar.powerwidget;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.policy.CurrentUserTracker;

public class QFloatingSlider extends RelativeLayout {

    private Context mContext;
    private View mView;
    private ImageView mScaleImage;
    private BaseStatusBar mBar;

    private final CurrentUserTracker mUserTracker;
    
    public QFloatingSlider(Context context) {
        super(context);
        mContext = context;
        mView = View.inflate(mContext, R.layout.q_floating_slider, this);
        
        mScaleImage = (ImageView)mView.findViewById(R.id.image);
        mScaleImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mBar != null)mBar.scaleForeground();
            }
        });
        mScaleImage.setBackgroundResource(R.drawable.ic_qf_scale_button);
        mUserTracker = new CurrentUserTracker(mContext);
        
        SettingsObserver so = new SettingsObserver(new Handler());
        so.observe();
    }

    public void setBar(BaseStatusBar bar) {
        mBar = bar;
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            /*resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
                            false, this, mUserTracker.getCurrentUserId());
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
                            false, this, mUserTracker.getCurrentUserId());*/
        }

        @Override
        public void onChange(boolean selfChange) {
        	
        }
    }

    public void setTransparent(boolean b) {
        if(b)mView.setBackgroundColor(0x00000000);
        else mView.setBackgroundResource(R.drawable.qs_tile_background);
    }
}
