package com.sandesh.watch.face;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import com.sandesh.watch.FaceDesign;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Live vector clock artwork. The retro panel is drawn, never a photograph of a clock. */
public final class FacePainter {
    private final Paint p=new Paint();
    private final Path path=new Path();
    private final Calendar clock=Calendar.getInstance();
    private final Typeface mono=Typeface.create("monospace",Typeface.BOLD);
    private final Typeface regular=Typeface.create("sans-serif",Typeface.NORMAL);
    public void draw(Canvas canvas,Rect bounds,FaceDesign face,long now,int battery,boolean ambient,boolean lowBit,boolean burnIn) {
        clock.setTimeZone(TimeZone.getDefault()); clock.setTimeInMillis(now);
        canvas.save(); canvas.clipRect(bounds); canvas.translate(bounds.left,bounds.top); canvas.scale(bounds.width()/320f,bounds.height()/320f);
        p.setAntiAlias(!ambient || !lowBit); p.setStyle(Paint.Style.FILL);
        canvas.drawColor(ambient ? Color.BLACK : face.style.startsWith("RETRO") ? 0xFF111719 : background(face.color));
        if(ambient && burnIn) canvas.translate((clock.get(Calendar.MINUTE)%3-1)*2,(clock.get(Calendar.MINUTE)/3%3-1)*2);
        if(ambient) ambient(canvas,face,battery);
        else if(face.style.equals("RETRO_DUAL")) dual(canvas,face,battery);
        else if(face.style.equals("RETRO")) retro(canvas,face,battery);
        else if(face.style.equals("ANALOG")) analog(canvas,face,battery);
        else digital(canvas,face,battery,face.style.equals("MINIMAL"));
        canvas.restore();
    }
    private void retro(Canvas c,FaceDesign f,int battery) {
        c.save(); c.scale(320f/280f,320f/223f); c.translate(-20,-54);
        int lcd=lcd(f.color), ink=lcdInk(f.color), ghost=Color.rgb((Color.red(lcd)*9+Color.red(ink))/10,(Color.green(lcd)*9+Color.green(ink))/10,(Color.blue(lcd)*9+Color.blue(ink))/10);




        fill(c,21,59,299,272,0xFF64706B); fill(c,26,64,294,267,lcd);
        for(int y=67;y<267;y+=5) { fill(c,16,y,21,y+1,0xFFADB5B2); fill(c,299,y,304,y+1,0xFFADB5B2); }
        if(f.date) {
            text(c,"DAY",40,80,8,ink,false,mono); text(c,"MM  DD",279,80,8,ink,true,mono);
            String weekday=new SimpleDateFormat("EEE",Locale.getDefault()).format(clock.getTime()).toUpperCase(Locale.getDefault());
            text(c,weekday,40,105,22,ink,false,mono);
            text(c,String.format(Locale.ROOT,"%02d-%02d",clock.get(Calendar.MONTH)+1,clock.get(Calendar.DAY_OF_MONTH)),279,105,21,ink,true,mono);
        } else text(c,"LOCAL TIME",40,104,18,ink,false,mono);
        int hour=FaceDesign.displayedHour(clock.get(Calendar.HOUR_OF_DAY),f.twentyFour);
        String digits=String.format(Locale.ROOT,"%02d%02d",hour,clock.get(Calendar.MINUTE));
        float x=43;
        for(int i=0;i<4;i++) { if(i==2) { fill(c,x+1,145,x+5,150,ink); fill(c,x+1,165,x+5,170,ink); x+=12; } digit(c,digits.charAt(i),x,119,39,76,ink,ghost,true); x+=43; }
        text(c,f.twentyFour ? "24H" : clock.get(Calendar.AM_PM)==Calendar.AM ? "AM" : "PM",276,138,10,ink,true,mono);
        if(f.seconds) number(c,String.format(Locale.ROOT,"%02d",clock.get(Calendar.SECOND)),239,159,17,34,ink,ghost,true);
        fill(c,29,204,291,207,ink);
        for(int i=0;i<7;i++) { path.reset(); path.moveTo(31+i*38,211); path.lineTo(51+i*38,211); path.lineTo(61+i*38,221); path.lineTo(41+i*38,221); path.close(); p.setColor(ink); c.drawPath(path,p); }
        if(f.battery) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f); p.setColor(ink); c.drawRect(40,236,80,251,p); c.drawRect(80,241,83,246,p); p.setStyle(Paint.Style.FILL);
            for(int i=0;i<5;i++) if(battery>=0 && i<Math.round(battery/20f)) fill(c,44+i*7,239,48+i*7,248,ink);
            text(c,battery<0 ? "--%" : battery+"%",94,250,14,ink,false,mono);
        }
        if(f.date) number(c,Integer.toString(clock.get(Calendar.YEAR)),211,230,15,25,ink,ghost,true);
        c.restore();
    }
    private void dual(Canvas c,FaceDesign f,int battery) {
        int lcd=lcd(f.color),ink=lcdInk(f.color),gold=0xFFE0D18A;
        int ghost=Color.rgb((Color.red(lcd)*9+Color.red(ink))/10,(Color.green(lcd)*9+Color.green(ink))/10,(Color.blue(lcd)*9+Color.blue(ink))/10);
        fill(c,6,6,314,314,0xFF363938); fill(c,13,13,307,307,0xFF101312);
        text(c,"SANDESH",27,37,15,gold,false,mono); text(c,"DUAL LCD",293,37,11,gold,true,mono);
        fill(c,24,49,296,177,0xFF343A35); fill(c,29,54,119,169,lcd);
        text(c,f.seconds ? "SECONDS" : "HOUR",74,72,10,ink,false,mono,true);
        number(c,String.format(Locale.ROOT,"%02d",f.seconds ? clock.get(Calendar.SECOND) : FaceDesign.displayedHour(clock.get(Calendar.HOUR_OF_DAY),f.twentyFour)),39,87,31,66,ink,ghost,true);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(gold); c.drawCircle(216,111,57,p); c.drawCircle(216,111,40,p); p.setStyle(Paint.Style.FILL);
        if(f.date) {
            String[] days={"SU","MO","TU","WE","TH","FR","SA"}; int today=clock.get(Calendar.DAY_OF_WEEK)-1;
            for(int i=0;i<7;i++) { double a=(i*360.0/7-90)*Math.PI/180; float x=216+(float)Math.cos(a)*48,y=114+(float)Math.sin(a)*48;
                if(i==today) fill(c,x-11,y-10,x+11,y+3,gold);
                text(c,days[i],x,y,8,i==today ? 0xFF101312 : gold,false,mono,true);
            }
            fill(c,185,91,247,131,lcd); number(c,String.format(Locale.ROOT,"%02d",clock.get(Calendar.DAY_OF_MONTH)),190,95,24,32,ink,ghost,true);
            text(c,String.format(Locale.ROOT,"%02d / %04d",clock.get(Calendar.MONTH)+1,clock.get(Calendar.YEAR)),216,185,10,gold,false,mono,true);
        } else { text(c,"LOCAL",216,109,13,gold,false,mono,true); text(c,"TIME",216,126,13,gold,false,mono,true); }
        fill(c,25,194,295,274,0xFF586050); fill(c,30,199,290,269,lcd);
        text(c,f.twentyFour ? "24H" : clock.get(Calendar.AM_PM)==Calendar.AM ? "AM" : "PM",35,212,8,ink,false,mono);
        String digits=String.format(Locale.ROOT,"%02d%02d",FaceDesign.displayedHour(clock.get(Calendar.HOUR_OF_DAY),f.twentyFour),clock.get(Calendar.MINUTE));
        float x=54; for(int i=0;i<4;i++) { if(i==2) { fill(c,x+2,224,x+7,229,ink); fill(c,x+2,244,x+7,249,ink); x+=16; } digit(c,digits.charAt(i),x,207,45,54,ink,ghost,true); x+=49; }
        if(f.battery) text(c,battery<0 ? "BAT --" : "BAT "+battery+"%",75,186,10,gold,false,mono,true);
        text(c,"▣ SMS",78,296,13,gold,false,mono,true); text(c,"FACES ▣",242,296,13,gold,false,mono,true);
    }
    private void digital(Canvas c,FaceDesign f,int battery,boolean minimal) {
        int ink=ink(f.color), accent=accent(f.color);
        text(c,"SANDESH",160,43,13,accent,false,mono,true);
        if(!f.twentyFour) text(c,clock.get(Calendar.AM_PM)==Calendar.AM ? "AM" : "PM",285,43,12,accent,true,mono);
        int hour=FaceDesign.displayedHour(clock.get(Calendar.HOUR_OF_DAY),f.twentyFour);
        if(minimal) {
            text(c,String.format(Locale.ROOT,"%02d",hour),160,132,78,ink,false,regular,true);
            text(c,String.format(Locale.ROOT,"%02d",clock.get(Calendar.MINUTE)),160,204,78,accent,false,regular,true);
        } else {
            text(c,String.format(Locale.ROOT,"%02d:%02d",hour,clock.get(Calendar.MINUTE)),160,154,67,ink,false,mono,true);
            if(f.seconds) text(c,String.format(Locale.ROOT,"%02d",clock.get(Calendar.SECOND)),160,183,23,accent,false,mono,true);
        }
        if(f.date) text(c,date(),160,minimal ? 237 : 217,17,ink,false,regular,true);
        if(f.battery) text(c,battery<0 ? "Battery --" : "Battery "+battery+"%",160,minimal ? 263 : 247,14,ink,false,regular,true);
        text(c,"OPEN SMS",160,294,12,accent,false,mono,true);
    }
    private void analog(Canvas c,FaceDesign f,int battery) {
        int ink=ink(f.color), accent=accent(f.color);
        float cx=160,cy=131,r=91;
        p.setColor(accent); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); c.drawCircle(cx,cy,r+6,p); p.setStyle(Paint.Style.FILL);
        for(int i=0;i<60;i++) {
            double a=Math.PI*i/30; float outer=r,inner=r-(i%5==0 ? 12 : 4);
            line(c,cx+(float)Math.sin(a)*inner,cy-(float)Math.cos(a)*inner,cx+(float)Math.sin(a)*outer,cy-(float)Math.cos(a)*outer,i%5==0 ? 3 : 1,ink);
        }
        float minute=clock.get(Calendar.MINUTE)+clock.get(Calendar.SECOND)/60f;
        hand(c,cx,cy,(clock.get(Calendar.HOUR)+minute/60f)*30,r*.55f,6,ink);
        hand(c,cx,cy,minute*6,r*.84f,4,accent);
        if(f.seconds) hand(c,cx,cy,clock.get(Calendar.SECOND)*6,r*.9f,1.5f,0xFFB64D54);
        p.setColor(accent); c.drawCircle(cx,cy,5,p);
        if(f.date) text(c,date(),160,250,17,ink,false,regular,true);
        if(f.battery) text(c,battery<0 ? "Battery --" : "Battery "+battery+"%",160,274,13,ink,false,regular,true);
        text(c,"OPEN SMS",160,302,11,accent,false,mono,true);
    }
    private void ambient(Canvas c,FaceDesign f,int battery) {
        int hour=FaceDesign.displayedHour(clock.get(Calendar.HOUR_OF_DAY),f.twentyFour);
        number(c,String.format(Locale.ROOT,"%02d",hour),36,112,49,83,Color.WHITE,0,false);
        fill(c,151,138,156,143,Color.WHITE); fill(c,151,165,156,170,Color.WHITE);
        number(c,String.format(Locale.ROOT,"%02d",clock.get(Calendar.MINUTE)),171,112,49,83,Color.WHITE,0,false);
        if(f.date) text(c,date(),160,235,16,Color.WHITE,false,regular,true);
        if(f.battery) text(c,battery<0 ? "--%" : battery+"%",160,266,12,Color.WHITE,false,regular,true);
    }
    private String date() { return new SimpleDateFormat("EEE d MMM",Locale.getDefault()).format(new Date(clock.getTimeInMillis())); }
    private void hand(Canvas c,float x,float y,float angle,float length,float width,int color) { double a=Math.toRadians(angle); line(c,x,y,x+(float)Math.sin(a)*length,y-(float)Math.cos(a)*length,width,color); }
    private void line(Canvas c,float x,float y,float x2,float y2,float width,int color) { p.setColor(color); p.setStrokeWidth(width); c.drawLine(x,y,x2,y2,p); }
    private void fill(Canvas c,float x,float y,float x2,float y2,int color) { p.setColor(color); p.setStyle(Paint.Style.FILL); c.drawRect(x,y,x2,y2,p); }
    private void number(Canvas c,String value,float x,float y,float w,float h,int ink,int ghost,boolean showGhost) { for(int i=0;i<value.length();i++) digit(c,value.charAt(i),x+i*(w+4),y,w,h,ink,ghost,showGhost); }
    private void digit(Canvas c,char value,float x,float y,float w,float h,int ink,int ghost,boolean showGhost) {
        int mask=FaceDesign.segmentMask(value); float t=w*.16f;
        for(int i=0;i<7;i++) {
            boolean lit=(mask&(1<<i))!=0; if(!lit && !showGhost) continue; p.setColor(lit ? ink : ghost);
            float sx=x,sy=y,sw=w,sh=t;
            switch(i) {
                case 0:sx=x+t;sw=w-2*t;break;
                case 1:sx=x+w-t;sy=y+t;sw=t;sh=h/2-t*1.5f;break;
                case 2:sx=x+w-t;sy=y+h/2+t/2;sw=t;sh=h/2-t*1.5f;break;
                case 3:sx=x+t;sy=y+h-t;sw=w-2*t;break;
                case 4:sx=x;sy=y+h/2+t/2;sw=t;sh=h/2-t*1.5f;break;
                case 5:sx=x;sy=y+t;sw=t;sh=h/2-t*1.5f;break;
                case 6:sx=x+t;sy=y+h/2-t/2;sw=w-2*t;break;
            }
            float b=Math.min(sw,sh)*.3f;
            path.reset();
            if(sw<sh) { path.moveTo(sx+sw/2,sy); path.lineTo(sx+sw,sy+b); path.lineTo(sx+sw,sy+sh-b); path.lineTo(sx+sw/2,sy+sh); path.lineTo(sx,sy+sh-b); path.lineTo(sx,sy+b); }
            else { path.moveTo(sx+b,sy); path.lineTo(sx+sw-b,sy); path.lineTo(sx+sw,sy+sh/2); path.lineTo(sx+sw-b,sy+sh); path.lineTo(sx+b,sy+sh); path.lineTo(sx,sy+sh/2); }
            path.close(); c.drawPath(path,p);
        }
    }
    private void text(Canvas c,String value,float x,float y,float size,int color,boolean right,Typeface font) { text(c,value,x,y,size,color,right,font,false); }
    private void text(Canvas c,String value,float x,float y,float size,int color,boolean right,Typeface font,boolean center) {
        p.setTypeface(font); p.setTextSize(size); p.setColor(color); p.setStyle(Paint.Style.FILL); p.setTextAlign(center ? Paint.Align.CENTER : right ? Paint.Align.RIGHT : Paint.Align.LEFT); c.drawText(value,x,y,p);
    }
    public static int accent(String color) { switch(color) { case "GREEN":return 0xFF356B55;case "DARK":return Color.WHITE;case "PURPLE":return 0xFF7255A4;case "ROSE":return 0xFFA44E72;case "AMBER":return 0xFF8D681E;default:return 0xFF315ED2; } }
    private int background(String color) { return color.equals("DARK") ? Color.BLACK : 0xFFF6F8F3; }
    private int ink(String color) { return color.equals("DARK") ? Color.WHITE : 0xFF17313B; }
    private int lcd(String color) { switch(color) { case "BLUE":return 0xFFBDCADD;case "DARK":return 0xFF172027;case "PURPLE":return 0xFFC7BDD5;case "ROSE":return 0xFFD4BDC5;case "AMBER":return 0xFFD2C399;default:return 0xFFB9C4AD; } }
    private int lcdInk(String color) { return color.equals("DARK") ? 0xFFDDE6E8 : 0xFF193C4A; }
}



