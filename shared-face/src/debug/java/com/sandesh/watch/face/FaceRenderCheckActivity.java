package com.sandesh.watch.face;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import com.sandesh.watch.FaceDesign;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Calendar;

/** Debug-only visual fixture: renders known clock data, never screenshots or reads SMS. */
public final class FaceRenderCheckActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        new Thread(this::render,"FaceRenderCheck").start();
    }
    private void render() {
        if(getIntent().getBooleanExtra("icon",false)) {
            BitmapFactory.Options options=new BitmapFactory.Options(); options.inSampleSize=16;
            Bitmap source=BitmapFactory.decodeResource(getResources(),getResources().getIdentifier("sandesh_logo","drawable",getPackageName()),options);
            Bitmap icon=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);
            try {
                float scale=Math.min(112f/source.getWidth(),112f/source.getHeight()); float w=source.getWidth()*scale,h=source.getHeight()*scale;
                new Canvas(icon).drawBitmap(source,null,new RectF((128-w)/2,(128-h)/2,(128+w)/2,(128+h)/2),new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
                try(FileOutputStream out=new FileOutputStream(new File(getFilesDir(),"sandesh-watch-icon.png"))) { icon.compress(Bitmap.CompressFormat.PNG,100,out); }
                android.util.Log.i("SandeshFaceCheck","Bounded 128px launcher icon exported");
            } catch(Exception ignored) { android.util.Log.e("SandeshFaceCheck","Icon export failed"); }
            finally { source.recycle(); icon.recycle(); runOnUiThread(this::finish); }
            return;
        }
        boolean ambient=getIntent().getBooleanExtra("ambient",false);
        Bitmap bitmap=Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888);
        try {
            Calendar sample=Calendar.getInstance(); sample.clear(); sample.set(2026,Calendar.NOVEMBER,25,10,27,36);
            Bitmap clipped=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888);
            for(String style:FaceDesign.STYLES) for(String color:FaceDesign.COLORS) {
                clipped.eraseColor(0xFFAA00AA);
                new FacePainter().draw(new Canvas(clipped),new Rect(40,40,360,360),new FaceDesign("check","Check",style,color,true,true,true,false),sample.getTimeInMillis(),82,false,false,false);
                if(clipped.getPixel(0,0)!=0xFFAA00AA || clipped.getPixel(399,399)!=0xFFAA00AA) throw new IllegalStateException("Preview escaped its bounds");
            }
            clipped.recycle(); android.util.Log.i("SandeshFaceCheck","All 30 style/color previews stayed inside bounds");
            boolean dual=getIntent().getBooleanExtra("dual",false);
            FaceDesign design=dual ? new FaceDesign("dual","Retro Dual","RETRO_DUAL","GREEN",true,true,true,true) : FaceDesign.retro();
            new FacePainter().draw(new Canvas(bitmap),new Rect(0,0,320,320),design,sample.getTimeInMillis(),82,ambient,true,false);
            int panel=bitmap.getPixel(20,20);
            if(!dual && (ambient ? panel!=0xFF000000 : panel!=0xFFB9C4AD)) throw new IllegalStateException("Unexpected clock palette");
            File output=new File(getFilesDir(),dual ? "face-check-dual.png" : ambient ? "face-check-ambient.png" : "face-check-active.png");
            try(FileOutputStream stream=new FileOutputStream(output)) { bitmap.compress(Bitmap.CompressFormat.PNG,100,stream); }
            android.util.Log.i("SandeshFaceCheck",ambient ? "Ambient render passed" : "Active render passed");
        } catch(Exception ignored) { android.util.Log.e("SandeshFaceCheck","Clock render failed"); }
        finally { bitmap.recycle(); runOnUiThread(this::finish); }
    }
}




