package com.android.systemui.statusbar.powerwidget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

public class QFloatingApps extends LinearLayout {

    public QFloatingApps(Context c) {
        this(c, null, 0);
    }
    
    public QFloatingApps(Context c, AttributeSet attr) {
        this(c, attr, 0);
    }
    
    public QFloatingApps(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        
    }

}
