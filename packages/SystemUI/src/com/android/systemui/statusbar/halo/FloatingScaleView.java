package com.android.systemui.statusbar.halo;

import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.NotificationData;
import com.android.systemui.statusbar.halo.Halo.Gesture;
import com.android.systemui.statusbar.halo.Halo.HaloEffect;
import com.android.systemui.statusbar.halo.Halo.State;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.RemoteException;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

public class FloatingScaleView extends FrameLayout {

    public final int MIN_HEIGHT, MIN_WIDTH;
    
    enum State {
        RESIZING,
        NOT_SHOWN,
        DRAG,
        NOTHING,
        GESTURES
    }
    
    enum ScaleCorner {
        NORTH_WEST,
        NORTH_EAST,
        SOUTH_WEST,
        SOUTH_EAST
    }
    
    private Context mContext;
    private BaseStatusBar mBar;
    private WindowManager mWindowManager;
    private Display mDisplay;
    private Vibrator mVibrator;
    private LayoutInflater mInflater;
    private SettingsObserver mSettingsObserver;
    private GestureDetector mGestureDetector;
    private Paint mLinePaint, mDirectionPaint;
    
    private int initX, initY, initVX, initVY, initW, initH;
    private int newX, newY, newWidth, newHeight;
    private int[] mStartLocation;
    private boolean mInMove;
    
    protected State mState;
    protected ScaleCorner mCorner;
    protected Activity mUsedAct;
    
    private final class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            /*resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HALO_REVERSED), false, this);*/
        }

        @Override
        public void onChange(boolean selfChange) {
            
        }
    }
    
    public FloatingScaleView(Context context) {
        this(context, null, 0);
    }
    
    public FloatingScaleView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public FloatingScaleView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        
        mContext = context;
        mWindowManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        mInflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mDisplay = mWindowManager.getDefaultDisplay();
        mGestureDetector = new GestureDetector(mContext, new GestureListener());
        
        DisplayMetrics metrics = new DisplayMetrics();
        mDisplay.getMetrics(metrics);
        
        MIN_HEIGHT = (int)(metrics.heightPixels * 0.1f);
        MIN_WIDTH = (int)(metrics.widthPixels * 0.1f);
        
        setVisibility(View.GONE);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                MIN_WIDTH * 3,
                MIN_HEIGHT * 3,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.LEFT|Gravity.TOP;
        lp.alpha = 0.75f;
        mWindowManager.addView(this, lp);
        
        mLinePaint = new Paint();
        mLinePaint.setAntiAlias(true);
        mLinePaint.setColor(0xFFFFFFFF);
        mLinePaint.setStrokeWidth(mContext.getResources().getDisplayMetrics().density * 1.0f);
        mDirectionPaint = new Paint();
        mDirectionPaint.setAntiAlias(true);
        mDirectionPaint.setColor(0xAAFFFFFF);
        mDirectionPaint.setStyle(Style.FILL_AND_STROKE);
        mDirectionPaint.setStrokeWidth(mContext.getResources().getDisplayMetrics().density * 1.0f);
    }
    
    @Override
    protected void onDraw(Canvas c) {
        if(mState == State.DRAG) {
            c.drawColor(0xFF2A74B8);
        } else if (mState == State.RESIZING) {
            c.drawColor(0xFF31BF5E);
            if(!mInMove) {
                c.drawLine(getWidth() / 2.0f, 0, getWidth() / 2.0f, getHeight(), mLinePaint);
                c.drawLine(0, getHeight() / 2.0f, getWidth(), getHeight() / 2.0f, mLinePaint);
            }
            if(mCorner != null) {
                if (mCorner == ScaleCorner.NORTH_WEST) {
                    c.drawRect(0, 0, getWidth() / 2, getHeight() / 2, mDirectionPaint);
                } else if (mCorner == ScaleCorner.NORTH_EAST) {
                    c.drawRect(getWidth() / 2, 0, getWidth(), getHeight() / 2, mDirectionPaint);
                } else if (mCorner == ScaleCorner.SOUTH_WEST) {
                    c.drawRect(0, getHeight() / 2, getWidth() / 2, getHeight(), mDirectionPaint);
                } else if (mCorner == ScaleCorner.SOUTH_EAST) {
                    c.drawRect(getWidth() / 2, getHeight() / 2, getWidth(), getHeight(), mDirectionPaint);
                }
            }
        }
    }
    
    public void setStatusBar(BaseStatusBar bar) {
        mBar = bar;
    }
    
    public void startResizing(Activity a) {
        if(a == null || !a.getWindow().mIsFloatingWindow || !a.getWindow().mIsFloatingChangeable)
            return; //We shouldn't scale these windows that aren't floating and are not changeable
        
        mUsedAct = a;
        mCorner = null;
        mState = State.DRAG;
        
        newX = newY = newWidth = newHeight = -1; //Set Values to default
        
        WindowManager.LayoutParams params = (WindowManager.LayoutParams)getLayoutParams();
        params.x = a.getX();
        params.y = a.getY();
        params.width = a.getWidth();
        params.height = a.getHeight();
        setLayoutParams(params);
        setVisibility(View.VISIBLE);
    }
    
    public void finishResizing() {
        if(mUsedAct == null)return;
        
        //If a value is lower than 0 or to low (width/height) 
        //use the last values from activity
        if(newX < 0) {
            newX = initVX;
        }
        if(newY < 0) {
            newY = initVY;
        }
        if(newWidth < MIN_WIDTH) {
            newWidth = MIN_WIDTH;
        }
        if(newHeight < MIN_HEIGHT) {
            newHeight = MIN_HEIGHT;
        }

        mCorner = null;
        mUsedAct.setDimensForWindow(newWidth, newHeight, newX, newY); //apply them
        mUsedAct.scaleFloatingWindow(mContext);
        mUsedAct = null;
        mState = State.NOTHING;
        setVisibility(View.GONE);
        newX = newY = newWidth = newHeight = -1; //Reset values
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {

        // Prevent any kind of interaction while Q-Floating explains itself
        //if (mState == State.FIRST_RUN) return true;

        mGestureDetector.onTouchEvent(event);

        final int action = event.getAction();
        final int posX = (int)event.getRawX();
        final int posY = (int)event.getRawY();
        
        switch(action) {
            case MotionEvent.ACTION_DOWN:
                
                if (mState != State.GESTURES) {}
                
                if(mState == State.RESIZING|| mState == State.DRAG) {
                    initX = posX;
                    initY = posY;
                    mStartLocation = new int[2];
                    getLocationOnScreen(mStartLocation);
                    initVX = mStartLocation[0];
                    initVY = mStartLocation[1];
                }
                if(mState == State.RESIZING) {
                    
                    mInMove = true;
                    
                    initW = getWidth();
                    initH = getHeight();
                    int relX = posX - initVX;
                    int relY = posY - initVY;
                    boolean north = (relY < initH / 2);
                    boolean west = (relX < initW / 2);
                    
                    if(north && west)mCorner = ScaleCorner.NORTH_WEST;
                    else if (north && !west)mCorner = ScaleCorner.NORTH_EAST;
                    else if (!north && !west)mCorner = ScaleCorner.SOUTH_EAST;
                    else if (!north && west)mCorner = ScaleCorner.SOUTH_WEST;
                }
                
                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if(mState == State.DRAG) {
                    int relX = posX - initX;
                    int relY = posY - initY;
                    newX = mStartLocation[0] + relX;
                    newY = mStartLocation[1] + relY;
                    if(newX < 0) newX = 0;
                    if(newY < 0) newY = 0;
                    //No need to do more - only change x,y coordinates
                } else if(mState == State.RESIZING) {
                    
                    mInMove = false;
                    
                    int relX = posX - initX;
                    int relY = posY - initY;
                    
                    if(mCorner == ScaleCorner.SOUTH_EAST) { 
                        //Easy bottom right -> just change width and height
                        newWidth = initW + relX;
                        newHeight = initH + relY;
                        if(newWidth < MIN_WIDTH)newWidth = MIN_WIDTH;
                        if(newHeight < MIN_HEIGHT)newHeight = MIN_HEIGHT;
                        
                    } else if (mCorner == ScaleCorner.SOUTH_WEST) {
                        //Same as at SOUTH_EAST - cause Height + SOUTH = easy job
                        newHeight = initH + relY;
                        if(newHeight < MIN_HEIGHT)newHeight = MIN_HEIGHT;
                        
                        //Now width (this time the x must also be changed)
                        newX = mStartLocation[0] + relX;
                        if(newX < 0)newX = 0;
                        newWidth = (mStartLocation[0] + initW) - newX;
                        if(newWidth < MIN_WIDTH)newWidth = MIN_WIDTH;
                        
                    } else if (mCorner == ScaleCorner.NORTH_EAST) {
                        //Same as at SOUTH_EAST - cause Width + EAST = easy job
                        newWidth = initW + relX;
                        if(newWidth < MIN_WIDTH)newWidth = MIN_WIDTH;
                        
                        //Now height (y must also be changed)
                        newY = mStartLocation[1] + relY;
                        if(newY < 0)newY = 0;
                        newHeight = (mStartLocation[1] + initH)- newY;
                        if(newHeight < MIN_HEIGHT)newHeight = MIN_HEIGHT;
                        
                    } else if (mCorner == ScaleCorner.NORTH_WEST) {
                        //width (this time the x must also be changed)
                        newX = mStartLocation[0] + relX;
                        if(newX < 0)newX = 0;
                        newWidth = (mStartLocation[0] + initW) - newX;
                        if(newWidth < MIN_WIDTH)newWidth = MIN_WIDTH; 
                        
                        //height (y must also be changed)
                        newY = mStartLocation[1] + relY;
                        if(newY < 0)newY = 0;
                        newHeight = (mStartLocation[1] + initH)- newY;
                        if(newHeight < MIN_HEIGHT)newHeight = MIN_HEIGHT;
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
               
                if(mState == State.DRAG) {
                    int relX = posX - initX;
                    int relY = posY - initY;
                    WindowManager.LayoutParams params = (WindowManager.LayoutParams)getLayoutParams();
                    params.x = initVX + relX;
                    params.y = initVY + relY;
                    setLayoutParams(params);
                } else if (mState == State.RESIZING){
                    int relX = posX - initX;
                    int relY = posY - initY;
                    WindowManager.LayoutParams params = (WindowManager.LayoutParams)getLayoutParams();
                    
                    if(mCorner == ScaleCorner.SOUTH_EAST) { 
                        //Easy bottom right -> just change width and height
                        params.width = initW + relX;
                        params.height = initH + relY;
                        if(params.width < MIN_WIDTH)params.width = MIN_WIDTH;
                        if(params.height < MIN_HEIGHT)params.height = MIN_HEIGHT;
                        
                    } else if (mCorner == ScaleCorner.SOUTH_WEST) {
                        //Same as at SOUTH_EAST - cause Height + SOUTH = easy job
                        newHeight = initH + relY;
                        if(newHeight < MIN_HEIGHT)newHeight = MIN_HEIGHT;
                        
                        //Now width (this time the x must also be changed)
                        params.x = mStartLocation[0] + relX;
                        if(params.x < 0)params.x = 0;
                        params.width = (mStartLocation[0] + initW) - params.x;
                        if(params.width < MIN_WIDTH)params.width = MIN_WIDTH;
                        
                    } else if (mCorner == ScaleCorner.NORTH_EAST) {
                        //Same as at SOUTH_EAST - cause Width + EAST = easy job
                        params.width = initW + relX;
                        if(params.width < MIN_WIDTH)params.width = MIN_WIDTH;
                        
                        //Now height (y must also be changed)
                        params.y = mStartLocation[1] + relY;
                        if(params.y < 0)params.y = 0;
                        params.height = (mStartLocation[1] + initH)- params.y;
                        if(params.height < MIN_HEIGHT)params.height = MIN_HEIGHT;
                        
                    } else if (mCorner == ScaleCorner.NORTH_WEST) {
                        //width (this time the x must also be changed)
                        params.x = mStartLocation[0] + relX;
                        if(params.x < 0)params.x = 0;
                        params.width = (mStartLocation[0] + initW) - params.x;
                        if(params.width < MIN_WIDTH)params.width = MIN_WIDTH; 
                        
                        //height (y must also be changed)
                        params.y = mStartLocation[1] + relY;
                        if(params.y < 0)params.y = 0;
                        params.height = (mStartLocation[1] + initH)- params.y;
                        if(params.height < MIN_HEIGHT)params.height = MIN_HEIGHT;
                    }
                    
                    setLayoutParams(params);
                }

                break;
        }
        return false;
    }
    
    class GestureListener extends GestureDetector.SimpleOnGestureListener {
        
        @Override
        public boolean onSingleTapUp (MotionEvent event) {
            playSoundEffect(SoundEffectConstants.CLICK);
            return true;
        }

        @Override
        public boolean onFling(MotionEvent event1, MotionEvent event2, 
                float velocityX, float velocityY) {
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent event) {
            if (mState == State.DRAG) {
                mState = State.RESIZING;
            } else if (mState == State.RESIZING) {
                mState = State.DRAG;
            }
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent event) {
            finishResizing();
            return true;
        }
    }
}
