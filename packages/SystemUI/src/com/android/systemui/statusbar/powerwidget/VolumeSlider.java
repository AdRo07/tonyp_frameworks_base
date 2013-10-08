/*
 * Copyright (C) 2010 The Android Open Source Project
 *  + Copyright (C) 2013 Firtecy
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

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.media.AudioService;
import android.media.ToneGenerator;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IPowerManager;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.Vibrator;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.util.Slog;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.CurrentUserTracker;
import com.android.systemui.statusbar.policy.StateToggleSlider;
import com.android.systemui.statusbar.policy.StateToggleSlider.StateToggleSliderItem;
import com.android.systemui.statusbar.powerwidget.BrightnessSlider.SettingsObserver;

public class VolumeSlider implements StateToggleSlider.Listener {
    private static final String TAG = "StatusBar.VolumeController";

    private static final int VIBRATE_DURATION = 300;
    private AudioManager mAudioManager;
    private Context mContext;
    private StateToggleSlider mControl;
    private View mView;

    private Vibrator mVibrator;
    private int mStreamMode, mLastValue, mLastState;
    private boolean mIgnoreNext;
    
    private Handler mHandler;

    private StateToggleSliderItem[] mStates;

    private final CurrentUserTracker mUserTracker;

    class SettingsObserver extends ContentObserver {
        private int mode;
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.VOLUME_SLIDER_INPUT_MODE),
                            false, this, mUserTracker.getCurrentUserId());
        }

        @Override
        public void onChange(boolean selfChange) {
            try {
                mode = Settings.System.getIntForUser(mContext.getContentResolver(),
                        Settings.System.VOLUME_SLIDER_INPUT_MODE,
                        mUserTracker.getCurrentUserId());
            } catch (SettingNotFoundException ex) {
                ex.printStackTrace();
            }
            if(mode != mStreamMode) {
                releaseContentObserver();
                mStreamMode = mode;
                init();
                observe();
                return;
            }
        }
    }
    
    private class CurVolumeThread extends Thread {
        private boolean mInterrupt = false;
        private int mVolume;
        private boolean mSkip = false;
        
        public void interrupt() {
            mInterrupt = true;
        }
        
        public void skipNext() {
            mSkip = true;
        }

        @Override
        public void run() {
            try {
                while (!mInterrupt) {
                    sleep(1000);
                    int volume = mAudioManager.getStreamVolume(mStreamMode);
                    if(mVolume != volume) {
                        if(!mSkip) {
                            mCurVolumeHandler.sendMessage(mCurVolumeHandler.obtainMessage(0, volume));
                        } else mSkip = false;
                    }
                    mVolume = volume;
                }
            } catch (InterruptedException e) {}
        }
    };

    private CurVolumeThread mVolumeThread = new CurVolumeThread();

    private Handler mCurVolumeHandler = new Handler() {
        public void handleMessage(Message msg) {
            onVolumeChanged((Integer)msg.obj);
        }
    };
    
    public VolumeSlider(Context context) {
        mContext = context;
        mView = View.inflate(mContext, R.layout.volume_slider, null);
        
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mVibrator = (Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
        
        mControl = (StateToggleSlider) mView.findViewById(R.id.volume);

        mIgnoreNext = false;
        mHandler = new Handler();
        mUserTracker = new CurrentUserTracker(mContext);

        mStreamMode = Settings.System.getIntForUser(mContext.getContentResolver(),
            Settings.System.VOLUME_SLIDER_INPUT_MODE, 0, mUserTracker.getCurrentUserId());
        
        mControl.setOnChangedListener(this);
        init();
        mVolumeThread.start();
    }
    
    private void init() {
        int state;
        if(mStreamMode == 0) {
            mStreamMode = AudioManager.STREAM_RING;
            mStates = new StateToggleSliderItem[] {
                    new StateToggleSliderItem(R.drawable.ic_audio_ring_notif_vibrate, "", true),
                    new StateToggleSliderItem(R.drawable.ic_audio_phone, "MUTE", true),
                    new StateToggleSliderItem(R.drawable.ic_audio_ring_notif, "SOUND", false),
            };
        } else if(mStreamMode == 1) {
            mStreamMode = AudioManager.STREAM_MUSIC;
            mStates = new StateToggleSliderItem[] {
                    new StateToggleSliderItem(R.drawable.ic_audio_vol, "UNMUTE", false),
                    new StateToggleSliderItem(R.drawable.ic_audio_vol_mute, "MUTE", true),
            };
        } else if(mStreamMode == 2){
            mStreamMode = AudioManager.STREAM_ALARM;
            mStates = new StateToggleSliderItem[] {
                    new StateToggleSliderItem(R.drawable.ic_audio_alarm, "UNMUTE", false),
                    new StateToggleSliderItem(R.drawable.ic_audio_alarm_mute, "MUTE", true),
            };
        }
        mControl.setItems(mStates);
        mIgnoreNext = true;
        int value = mAudioManager.getStreamVolume(mStreamMode);
        if(mStreamMode == AudioManager.STREAM_RING) {
            if(mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE)
                state = 0;
            else if(mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT)
                state = 1;
            else
                state = 2;
            listenToRingerMode();
        } else {
            state = (value == 0) ? 1 : 0;
        }
        mControl.setMax(value);
        mIgnoreNext = true;
        mControl.setValue(value);
        mIgnoreNext = true;
        mControl.changeToState(state);
    }
    
    private void onVolumeChanged(int volume) {
        mControl.setValue(volume);
    }

    public View getView() {
        return mView;
    }

    public void onInit(StateToggleSlider v) {
        SettingsObserver so = new SettingsObserver(new Handler());
        so.observe();
    }

    @Override
    public void onChanged(StateToggleSlider v, boolean tracking, final int state, int value) {
        if(mIgnoreNext) {
            mIgnoreNext = false;
            mLastValue = value; //Ignore next is only set by this method,
            mLastState = state; //so we save the last values too.
            return;
        }
        if(mStreamMode == AudioManager.STREAM_RING) {
            mVolumeThread.skipNext();
            if(state == 2) {
                if(mLastState == state)
                    mAudioManager.setStreamVolume(mStreamMode, value, AudioManager.FLAG_PLAY_SOUND);
                if(value == 0) {
                    mControl.changeToState(0);
                } else {
                    mAudioManager.setStreamVolume(mStreamMode, value, AudioManager.FLAG_PLAY_SOUND);
                }
            }
            if(state != 2){
                boolean vibrate = state == 0;
                if(state != mLastState) {
                    mAudioManager.setRingerMode(vibrate ? AudioManager.RINGER_MODE_VIBRATE
                            : AudioManager.RINGER_MODE_SILENT);
                    if(vibrate)vibrate();
                    mAudioManager.setStreamVolume(mStreamMode, value, 0);
                } else  if(value > 0) {
                    mIgnoreNext = true;
                    mControl.changeToState(2);
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    mAudioManager.setStreamVolume(mStreamMode, value, AudioManager.FLAG_PLAY_SOUND);
                }
            }
        } else {
            mVolumeThread.skipNext();
            if(value > 0 && state == 1) {
                mControl.changeToState(0);
                mAudioManager.setStreamVolume(mStreamMode, value, AudioManager.FLAG_PLAY_SOUND);
            } else if (value == 0 && state == 0) {
                mControl.changeToState(1);
                mAudioManager.setStreamVolume(mStreamMode, value, 0);
            } else {
                mAudioManager.setStreamVolume(mStreamMode, value, AudioManager.FLAG_PLAY_SOUND);
            }
        }
        mLastValue = value;
        mLastState = state;
    }
    
    private void listenToRingerMode() {
        final IntentFilter filter = new IntentFilter();
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        mContext.registerReceiver(new BroadcastReceiver() {

            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();

                if (AudioManager.RINGER_MODE_CHANGED_ACTION.equals(action) 
                        && mStreamMode == AudioManager.STREAM_RING) {
                    final int audio = mAudioManager.getRingerMode();
                    switch(audio) {
                        case AudioManager.RINGER_MODE_NORMAL:
                            if(mLastState != 2) //Don't update if it is the same!
                                mControl.changeToState(2);
                            break;
                        case AudioManager.RINGER_MODE_SILENT:
                            if(mLastState != 1) //See above
                                mControl.changeToState(1);
                            break;
                        case AudioManager.RINGER_MODE_VIBRATE:
                            if(mLastState != 0) //See two above
                                mControl.changeToState(0);
                            break;
                    }
                }
            }
        }, filter);
    }
    
    protected void vibrate() {
        // Make sure we ended up in vibrate ringer mode
        if (mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_VIBRATE) {
            return;
        }
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE) {
                    mVibrator.vibrate(VIBRATE_DURATION);
                }
            }}, 400);
    }

    public void setTransparent(boolean b) {
        if(b)mView.setBackgroundColor(0x00000000);
        else mView.setBackgroundResource(R.drawable.qs_tile_background);
    }
}
