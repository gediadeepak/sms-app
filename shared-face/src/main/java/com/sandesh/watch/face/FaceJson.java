package com.sandesh.watch.face;
import org.json.JSONObject;
import com.sandesh.watch.FaceDesign;
public class FaceJson {
    public static JSONObject encode(FaceDesign face) {
        JSONObject value=new JSONObject();
        try { value.put("id",face.id).put("name",face.name).put("style",face.style).put("color",face.color).put("date",face.date)
            .put("battery",face.battery).put("seconds",face.seconds).put("twentyFour",face.twentyFour); } catch(Exception ignored) {}
        return value;
    }
    public static FaceDesign decode(JSONObject value) throws Exception {
        return new FaceDesign(value.getString("id"),value.getString("name"),value.getString("style"),value.getString("color"),
            value.getBoolean("date"),value.getBoolean("battery"),value.getBoolean("seconds"),value.getBoolean("twentyFour"));
    }
}


