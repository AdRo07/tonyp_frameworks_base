package com.android.systemui.quicksettings;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

public class NetworkLocationTile extends QuickSettingsTile {

    private boolean enabled = false;

    private String mDescription = null;

    ContentResolver mContentResolver;

    public NetworkLocationTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mContentResolver = mContext.getContentResolver();

        enabled = Settings.Secure.isLocationProviderEnabled(mContentResolver, LocationManager.NETWORK_PROVIDER);

        mOnClick = new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!enabled) mQsc.mBar.collapseAllPanels(true);
                Settings.Secure.setLocationProviderEnabled(mContentResolver, LocationManager.NETWORK_PROVIDER, !enabled);
            }
        };

        mOnLongClick = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                return true;
            }
        };
        qsc.registerAction(LocationManager.PROVIDERS_CHANGED_ACTION, this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        enabled = Settings.Secure.isLocationProviderEnabled(mContentResolver, LocationManager.NETWORK_PROVIDER);
        updateResources();
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void updateResources() {
        updateTile();
        updateQuickSettings();
    }

    private synchronized void updateTile() {
        if (enabled) {
            mDrawable = R.drawable.ic_qs_location;
        } else {
            mDrawable = R.drawable.ic_qs_gps_off;
        }
        setGenericLabel();
    }

    private void setGenericLabel() {
        if (mDescription != null) {
            mLabel = mDescription;
        } else {
            mLabel = (enabled ? mContext.getString(R.string.quick_settings_network_location) : mContext.getString(R.string.quick_settings_network_location_off));
        }
    }
}
