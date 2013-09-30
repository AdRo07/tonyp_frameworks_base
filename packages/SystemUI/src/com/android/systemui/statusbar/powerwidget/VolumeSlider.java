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
import com.android.systemui.statusbar.policy.ImageToggleSlider;
import com.android.systemui.statusbar.powerwidget.BrightnessSlider.SettingsObserver;

public class VolumeSlider implements ImageToggleSlider.Listener {
    private static final String TAG = "StatusBar.VolumeController";

    private static final int VIBRATE_DURATION = 300;
    private AudioManager mAudioManager;
    private Context mContext;
    private ImageToggleSlider mControl;
    private View mView;

    private Vibrator mVibrator;
    private int mStreamMode;
    
    private Handler mHandler;

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
            } catch (InterruptedException e) {
            }
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
        
        mControl = (ImageToggleSlider) mView.findViewById(R.id.volume);

        mHandler = new Handler();
        mUserTracker = new CurrentUserTracker(mContext);

        try {
            mStreamMode = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.VOLUME_SLIDER_INPUT_MODE,
                mUserTracker.getCurrentUserId());
        } catch (SettingNotFoundException ex) {}

        init();
        mControl.setOnChangedListener(this);
        mVolumeThread.start();
    }
    
    private void init() {
        if(mStreamMode == 0) {
            mStreamMode = AudioManager.STREAM_RING;
        } else if(mStreamMode == 1) {
            mStreamMode = AudioManager.STREAM_MUSIC;
        } else {
            mStreamMode = AudioManager.STREAM_ALARM;
        }
        
        int value = mAudioManager.getStreamVolume(mStreamMode);
        if(mStreamMode == AudioManager.STREAM_RING) {
            mControl.setChecked(mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT);
            listenToRingerMode();
        } else {
            updateIcons(value);
        }
        mControl.setMax(mAudioManager.getStreamMaxVolume(mStreamMode));
        mControl.setValue(value);
    }
    
    private void onVolumeChanged(int volume) {
        mControl.setValue(volume);
        onChanged(null, false, false, volume);
    }

    public View getView() {
        return mView;
    }

    public void onInit(ImageToggleSlider v) {
        SettingsObserver so = new SettingsObserver(new Handler());
        so.observe();
    }

    public void onChanged(ImageToggleSlider view, boolean tracking, boolean silent, int value) {
        if(mStreamMode == AudioManager.STREAM_RING) {
            mAudioManager.setRingerMode(silent ? AudioManager.RINGER_MODE_SILENT 
                    : AudioManager.RINGER_MODE_NORMAL);
            if(!silent) {
                if(value == 0) {
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                    mControl.setImage(R.drawable.ic_audio_ring_notif_vibrate);
                    onVibrate();
                } else {
                    mVolumeThread.skipNext();
                    mAudioManager.setStreamVolume(mStreamMode, value, AudioManager.FLAG_PLAY_SOUND);
                    mControl.setImage(R.drawable.ic_audio_ring_notif);
                }
            } else {
                mVolumeThread.skipNext();
                mControl.setImage(R.drawable.ic_audio_phone);
            }
        } else {
            mVolumeThread.skipNext();
            mAudioManager.setStreamMute(mStreamMode, silent);
            if(!silent)
                mAudioManager.setStreamVolume(mStreamMode, value, AudioManager.FLAG_PLAY_SOUND);
            else mAudioManager.setStreamVolume(mStreamMode, value, 0);
            updateIcons(value);
        }
    }

    private void updateIcons(int value) {
        int u = (mStreamMode == AudioManager.STREAM_MUSIC) ? R.drawable.ic_audio_vol :
            R.drawable.ic_audio_alarm;
        int m = (mStreamMode == AudioManager.STREAM_MUSIC) ? R.drawable.ic_audio_vol_mute :
            R.drawable.ic_audio_alarm_mute;
        mControl.setImage((value == 0) ? m : u); 
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
                            mControl.setChecked(false);
                            mControl.setImage(R.drawable.ic_audio_ring_notif);
                            break;
                        case AudioManager.RINGER_MODE_SILENT:
                            mControl.setChecked(true);
                            mControl.setImage(R.drawable.ic_audio_phone);
                            break;
                        case AudioManager.RINGER_MODE_VIBRATE:
                            mControl.setChecked(false);
                            mControl.setImage(R.drawable.ic_audio_ring_notif_vibrate);
                            break;
                    }
                }
            }
        }, filter);
    }
    
    protected void onVibrate() {
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
