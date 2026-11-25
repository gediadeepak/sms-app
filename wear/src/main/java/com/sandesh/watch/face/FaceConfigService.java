package com.sandesh.watch.face;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.*;
import com.sandesh.watch.FaceDesign;
import com.sandesh.watch.WatchProtocol;
import org.json.*;
import java.util.concurrent.TimeUnit;

/** Face-only messages; never requests SMS or stores phone content. */
public final class FaceConfigService extends WearableListenerService {
    @Override public void onMessageReceived(MessageEvent event) {
        if(!event.getPath().equals("/sandesh/faces/v1/request") || event.getData().length>4096) return;
        GoogleApiClient client=null;
        try {
            JSONObject request=new JSONObject(new String(event.getData(),"UTF-8"));
            String id=request.optString("id"),command=request.optString("command");
            if(!WatchProtocol.validToken(id) || request.optInt("version")!=1) return;
            JSONObject reply=new JSONObject().put("id",id).put("version",1); FaceStore store=new FaceStore(this);
            String status="ok";
            try {
                if(command.equals("save")) store.save(FaceJson.decode(request.getJSONObject("face")));
                else if(command.equals("apply")) {
                    store.activate(FaceJson.decode(request.getJSONObject("face")));
                    android.app.WallpaperInfo info=WallpaperManager.getInstance(this).getWallpaperInfo();
                    if(info==null || !new ComponentName(this,SandeshFaceService.class).equals(info.getComponent())) {
                        status="confirm";
                        startActivity(new Intent(this,FaceApplyActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } else status="applied";
                } else if(command.equals("delete")) store.delete(request.getString("faceId"));
                else if(!command.equals("list")) return;
            } catch(Exception e) { status="error"; reply.put("error",e instanceof IllegalArgumentException ? e.getMessage() : "Could not update face. Open Faces on the watch."); }
            JSONArray faces=new JSONArray(); for(FaceDesign face:store.all()) faces.put(FaceJson.encode(face));
            reply.put("status",status).put("faces",faces).put("active",FaceJson.encode(store.active()));
            android.util.Log.i("SandeshFaces",command+" / "+status);
            client=new GoogleApiClient.Builder(this).addApi(Wearable.API).build();
            if(client.blockingConnect(4,TimeUnit.SECONDS).isSuccess())
                Wearable.MessageApi.sendMessage(client,event.getSourceNodeId(),"/sandesh/faces/v1/response",reply.toString().getBytes("UTF-8")).await(4,TimeUnit.SECONDS);
        } catch(Exception ignored) { android.util.Log.w("SandeshFaces","Face request failed"); }
        finally { if(client!=null) client.disconnect(); }
    }
}
