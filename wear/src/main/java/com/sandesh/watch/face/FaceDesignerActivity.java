package com.sandesh.watch.face;

import android.app.Activity;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.BatteryManager;
import android.os.Bundle;
import android.content.IntentFilter;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import android.text.InputFilter;
import com.sandesh.watch.FaceDesign;
import java.util.List;
import java.util.UUID;

/** Small offline design gallery with a live-vector preview and the system Apply flow. */
public final class FaceDesignerActivity extends Activity {
    private FaceStore store;
    private FaceDesign draft;
    private LinearLayout content;
    private EditText name;
    private Spinner saved,style,color;
    private CheckBox date,battery,seconds,twentyFour;
    private Preview preview;
    private boolean binding;
    private List<FaceDesign> faces;
    private int level=-1;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store=new FaceStore(this); draft=store.active();
        if(state!=null) try { draft=FaceStore.decode(new org.json.JSONObject(state.getString("draft",""))); } catch(Exception ignored) {}
        Intent charge=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if(charge!=null) { int value=charge.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=charge.getIntExtra(BatteryManager.EXTRA_SCALE,-1); if(value>=0 && scale>0) level=value*100/scale; }
        build();
    }
    private void build() {
        binding=true;
        ScrollView scroll=new ScrollView(this); content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(12),dp(16),dp(12),dp(20)); content.setBackgroundColor(0xFF101820); scroll.addView(content); setContentView(scroll);
        label("Watch faces",21); label("Create · save · apply",12);
        preview=new Preview(); content.addView(preview,new LinearLayout.LayoutParams(-1,-2));
        label("Saved designs",13); saved=new Spinner(this); content.addView(saved,new LinearLayout.LayoutParams(-1,dp(48))); refreshGallery();
        saved.setOnItemSelectedListener(new Select() { @Override public void onItemSelected(AdapterView<?> p,View v,int position,long id) { if(!binding && position<faces.size()) { draft=faces.get(position); bind(); } } });
        button("New face",() -> { draft=new FaceDesign(UUID.randomUUID().toString(),newName(),"RETRO","GREEN",true,true,true,android.text.format.DateFormat.is24HourFormat(this)); bind(); });
        label("Face name",13); name=new EditText(this); name.setTextColor(Color.WHITE); name.setSingleLine(true); name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)}); content.addView(name,new LinearLayout.LayoutParams(-1,dp(48)));
        label("Clock style",13); style=spinner(new String[]{"Retro LCD","Digital","Analog","Minimal","Retro Dual"});
        label("Display color",13); color=spinner(new String[]{"LCD green","Blue","Black & white","Purple","Rose","Amber"});
        date=check("Show date"); battery=check("Show watch battery"); seconds=check("Seconds while awake"); twentyFour=check("24-hour digital clock");
        style.setOnItemSelectedListener(new Select() { @Override public void onItemSelected(AdapterView<?> p,View v,int position,long id) { if(!binding) updatePreview(); } });
        color.setOnItemSelectedListener(new Select() { @Override public void onItemSelected(AdapterView<?> p,View v,int position,long id) { if(!binding) updatePreview(); } });
        button("Save face",() -> { try { draft=read(); store.save(draft); refreshGallery(); toast("Face saved. Tap Apply to use it."); } catch(Exception e) { toast(e.getMessage()); } });
        button("Apply face",this::apply);
        button("Remove saved face",() -> { try { store.delete(draft.id); draft=store.all().get(0); refreshGallery(); bind(); toast("Saved face removed"); } catch(Exception e) { toast(e.getMessage()); } });
        label("Apply opens the watch’s system picker. Your designs live in this gallery under one Sandesh watch face. Ambient mode uses a simple white clock; seconds stop while asleep. No SMS text is shown on the face.",12);
        button("Back to Sandesh",this::finish);
        bind(); content.setFocusableInTouchMode(true); content.requestFocus();
    }
    private void refreshGallery() {
        boolean previous=binding; binding=true; faces=store.all();
        String[] labels=new String[faces.size()]; int selected=0;
        for(int i=0;i<faces.size();i++) { labels[i]=faces.get(i).name; if(faces.get(i).id.equals(draft.id)) selected=i; }
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels); adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); saved.setAdapter(adapter); saved.setSelection(selected); binding=previous;
    }
    private String newName() { for(int i=1;i<=99;i++) { String value="My retro "+i; boolean found=false; for(FaceDesign face:store.all()) if(face.name.equalsIgnoreCase(value)) found=true; if(!found) return value; } return "New face"; }
    private void bind() {
        binding=true; if(name!=null) name.setText(draft.name);
        if(style!=null) style.setSelection(index(FaceDesign.STYLES,draft.style)); if(color!=null) color.setSelection(index(FaceDesign.COLORS,draft.color));
        if(date!=null) date.setChecked(draft.date); if(battery!=null) battery.setChecked(draft.battery); if(seconds!=null) seconds.setChecked(draft.seconds); if(twentyFour!=null) twentyFour.setChecked(draft.twentyFour);
        binding=false; if(preview!=null) preview.invalidate();
    }
    private FaceDesign read() { return new FaceDesign(draft.id,name.getText().toString(),FaceDesign.STYLES[style.getSelectedItemPosition()],FaceDesign.COLORS[color.getSelectedItemPosition()],date.isChecked(),battery.isChecked(),seconds.isChecked(),twentyFour.isChecked()); }
    private void updatePreview() { try { draft=read(); preview.invalidate(); } catch(Exception ignored) {} }
    private void apply() {
        try { draft=read(); store.activate(draft); refreshGallery(); }
        catch(Exception e) { toast(e.getMessage()); return; }
        try {
            ComponentName component=new ComponentName(this,SandeshFaceService.class);
            WallpaperInfo current=WallpaperManager.getInstance(this).getWallpaperInfo();
            if(current!=null && component.equals(current.getComponent())) { toast("Design applied"); home(); return; }
            Intent apply=new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,component);
            try { startActivityForResult(apply,41); }
            catch(android.content.ActivityNotFoundException ignored) { startActivityForResult(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER),41); }
        } catch(Exception e) { toast("Saved design. Long-press your clock and choose Sandesh to apply."); }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) { super.onActivityResult(request,result,data); if(request==41) { WallpaperInfo current=WallpaperManager.getInstance(this).getWallpaperInfo(); toast(current!=null && new ComponentName(this,SandeshFaceService.class).equals(current.getComponent()) ? "Sandesh face applied" : "Choose Sandesh in the watch face picker to apply"); } }
    private void home() { startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); finish(); }
    @Override public void onSaveInstanceState(Bundle state) { try { state.putString("draft",FaceStore.encode(read()).toString()); } catch(Exception ignored) {} super.onSaveInstanceState(state); }
    private Spinner spinner(String[] values) { Spinner s=new Spinner(this); ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values); a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); s.setAdapter(a); content.addView(s,new LinearLayout.LayoutParams(-1,dp(48))); return s; }
    private CheckBox check(String title) { CheckBox c=new CheckBox(this); c.setText(title); c.setTextColor(Color.WHITE); c.setMinHeight(dp(48)); content.addView(c); c.setOnCheckedChangeListener((button,checked)->{ if(!binding) updatePreview(); }); return c; }
    private void label(String value,int size) { TextView t=new TextView(this); t.setText(value); t.setTextColor(Color.WHITE); t.setTextSize(size); t.setPadding(0,dp(8),0,dp(5)); content.addView(t); }
    private void button(String label,Runnable action) { Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(14); b.setMinHeight(dp(48)); b.setOnClickListener(v->action.run()); content.addView(b,new LinearLayout.LayoutParams(-1,-2)); }
    private void toast(String message) { Toast.makeText(this,message==null ? "Could not save this face" : message,Toast.LENGTH_LONG).show(); }
    private int index(String[] values,String value) { for(int i=0;i<values.length;i++) if(values[i].equals(value)) return i; return 0; }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private abstract static class Select implements AdapterView.OnItemSelectedListener { @Override public void onNothingSelected(AdapterView<?> parent) {} }
    private final class Preview extends View {
        private final FacePainter painter=new FacePainter();
        Preview() { super(FaceDesignerActivity.this); setContentDescription("Watch face design preview"); }
        @Override protected void onMeasure(int w,int h) { int width=MeasureSpec.getSize(w); setMeasuredDimension(width,width); }
        @Override protected void onDraw(Canvas canvas) { painter.draw(canvas,new Rect(0,0,getWidth(),getHeight()),draft,System.currentTimeMillis(),level,false,false,false); }
    }
}

