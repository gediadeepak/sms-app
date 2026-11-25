package com.sandesh.watch.face;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;
import com.sandesh.watch.FaceDesign;
import com.sandesh.watch.WatchActivity;

/** Legacy Wear 1.5 clock: time/battery only, no SMS query or background phone sync. */
public final class SandeshFaceService extends CanvasWatchFaceService {
    @Override public Engine onCreateEngine() { return new FaceEngine(); }
    private final class FaceEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {
        private final Handler handler=new Handler(Looper.getMainLooper());
        private final FacePainter painter=new FacePainter();
        private final Rect lastBounds=new Rect();
        private FaceStore store;
        private FaceDesign face;
        private boolean lowBit,burnIn,registered;
        private int battery=-1;
        private final Runnable tick=new Runnable() { @Override public void run() { if(isVisible() && !isInAmbientMode() && face.seconds) { invalidate(); handler.postDelayed(this,1000-System.currentTimeMillis()%1000); } } };
        private final BroadcastReceiver changes=new BroadcastReceiver() {
            @Override public void onReceive(Context context,Intent intent) {
                if(Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                    int level=intent.getIntExtra(BatteryManager.EXTRA_LEVEL,-1), scale=intent.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
                    battery=level>=0 && scale>0 ? Math.min(100,Math.max(0,level*100/scale)) : -1;
                }
                invalidate();
            }
        };
        @Override public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            store=new FaceStore(SandeshFaceService.this); face=store.active();
            store.preferences().registerOnSharedPreferenceChangeListener(this);
            setWatchFaceStyle(new WatchFaceStyle.Builder(SandeshFaceService.this).setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE).setShowSystemUiTime(false).setAcceptsTapEvents(true).build());
        }
        @Override public void onPropertiesChanged(Bundle properties) { super.onPropertiesChanged(properties); lowBit=properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT,false); burnIn=properties.getBoolean(PROPERTY_BURN_IN_PROTECTION,false); }
        @Override public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            if(visible) {
                face=store.active();
                if(!registered) {
                    IntentFilter filter=new IntentFilter(); filter.addAction(Intent.ACTION_TIMEZONE_CHANGED); filter.addAction(Intent.ACTION_TIME_CHANGED); filter.addAction(Intent.ACTION_LOCALE_CHANGED); filter.addAction(Intent.ACTION_BATTERY_CHANGED);
                    Intent initial=registerReceiver(changes,filter); registered=true;
                    if(initial!=null) changes.onReceive(SandeshFaceService.this,initial);
                }
                invalidate();
            } else unregister();
            schedule();
        }
        @Override public void onTimeTick() { super.onTimeTick(); if(isVisible()) invalidate(); }
        @Override public void onAmbientModeChanged(boolean ambient) { super.onAmbientModeChanged(ambient); schedule(); invalidate(); }
        @Override public void onDraw(Canvas canvas,Rect bounds) { lastBounds.set(bounds); painter.draw(canvas,bounds,face,System.currentTimeMillis(),battery,isInAmbientMode(),lowBit,burnIn); }
        @Override public void onSharedPreferenceChanged(SharedPreferences preferences,String key) { face=store.active(); schedule(); invalidate(); }
        @Override public void onTapCommand(int type,int x,int y,long when) {
            if(!face.style.equals("RETRO") && type==WatchFaceService.TAP_TYPE_TAP && !isInAmbientMode() && y>lastBounds.height()*.82f && x>lastBounds.width()*.18f && x<lastBounds.width()*.82f) {
                startActivity(new Intent(SandeshFaceService.this,face.style.equals("RETRO_DUAL") && x>lastBounds.width()/2 ? FaceDesignerActivity.class : WatchActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        }
        private void schedule() { handler.removeCallbacks(tick); if(face!=null && isVisible() && !isInAmbientMode() && face.seconds) handler.post(tick); }
        private void unregister() { if(registered) { unregisterReceiver(changes); registered=false; } }
        @Override public void onDestroy() { handler.removeCallbacksAndMessages(null); unregister(); if(store!=null) store.preferences().unregisterOnSharedPreferenceChangeListener(this); super.onDestroy(); }
    }
}


