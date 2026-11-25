package com.sandesh.watch.face;

import android.content.Context;
import android.content.SharedPreferences;
import com.sandesh.watch.FaceDesign;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Local designs only. Saving a draft does not alter the applied snapshot. */
public final class FaceStore {
    public static final int MAX = 12;
    private final SharedPreferences prefs;
    public FaceStore(Context context) { prefs=context.getSharedPreferences("sandesh_faces",Context.MODE_PRIVATE); }
    public SharedPreferences preferences() { return prefs; }
    public List<FaceDesign> all() {
        List<FaceDesign> result=new ArrayList<>();
        try {
            JSONArray array=new JSONArray(prefs.getString("designs","[]"));
            for(int i=0;i<array.length() && result.size()<MAX;i++) {
                try { FaceDesign face=decode(array.getJSONObject(i)); boolean duplicate=false; for(FaceDesign item:result) if(item.id.equals(face.id)) duplicate=true; if(!duplicate) result.add(face); } catch(Exception ignored) {}
            }
        } catch(Exception ignored) {}
        if(result.isEmpty()) {
            result.add(FaceDesign.retro());
            result.add(new FaceDesign("retro-dual","Retro Dual","RETRO_DUAL","GREEN",true,true,true,true));
            result.add(new FaceDesign("blue-digital","Blue Digital","DIGITAL","BLUE",true,true,false,true));
            result.add(new FaceDesign("mono-analog","Mono Analog","ANALOG","DARK",true,true,false,true));
        }
        return result;
    }
    public FaceDesign active() { try { return decode(new JSONObject(prefs.getString("active",""))); } catch(Exception ignored) { return FaceDesign.retro(); } }
    public void save(FaceDesign design) {
        List<FaceDesign> faces=all();
        for(FaceDesign face:faces) if(!face.id.equals(design.id) && face.name.equalsIgnoreCase(design.name)) throw new IllegalArgumentException("Choose a different face name");
        int index=-1; for(int i=0;i<faces.size();i++) if(faces.get(i).id.equals(design.id)) index=i;
        if(index>=0) faces.set(index,design); else { if(faces.size()>=MAX) throw new IllegalArgumentException("Remove a saved face before adding another"); faces.add(design); }
        write(faces);
    }
    public void activate(FaceDesign design) { save(design); prefs.edit().putString("active",encode(design).toString()).apply(); }
    public void delete(String id) {
        if(active().id.equals(id)) throw new IllegalArgumentException("Apply another design before removing this one");
        List<FaceDesign> faces=all();
        for(int i=faces.size()-1;i>=0;i--) if(faces.get(i).id.equals(id)) faces.remove(i);
        if(faces.isEmpty()) throw new IllegalArgumentException("Keep at least one saved face");
        write(faces);
    }
    private void write(List<FaceDesign> faces) {
        JSONArray array=new JSONArray(); for(FaceDesign face:faces) array.put(encode(face)); prefs.edit().putString("designs",array.toString()).apply();
    }
    public static JSONObject encode(FaceDesign f) { return FaceJson.encode(f); }
    public static FaceDesign decode(JSONObject v) throws Exception { return FaceJson.decode(v); }
}
