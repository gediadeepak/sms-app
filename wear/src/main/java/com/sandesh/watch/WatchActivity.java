package com.sandesh.watch;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Small native API-23 inbox. No SMS permission, account, database, DataItem or background sync. */
public final class WatchActivity extends Activity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, MessageApi.MessageListener {
    private GoogleApiClient client;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout content;
    private boolean visible, listenerReady, busy;
    private String token, peer, thread;
    private JSONArray rows;
    private JSONObject detail;
    private int background = Color.rgb(15,22,32), ink = Color.WHITE, muted = 0xFFBBC7D8, accent = 0xFF4169D8, card = 0xFF202E40;
    private final Runnable timeout = () -> { if (visible && busy) { retire(); showStatus("No phone response. Install/update Sandesh on the paired phone, then tap Refresh."); } };
    private final Runnable expire = () -> { if (visible) { retire(); showStatus("Previews cleared. Tap Refresh to load again."); } };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16),dp(18),dp(16),dp(20));
        scroll.addView(content);
        setContentView(scroll);
        client = new GoogleApiClient.Builder(this).addApi(Wearable.API).addConnectionCallbacks(this).addOnConnectionFailedListener(this).build();
        showStatus("Connecting to your phone…");
    }
    @Override protected void onStart() { super.onStart(); client.connect(); }
    @Override protected void onResume() { super.onResume(); visible = true; if (listenerReady && client.isConnected()) request(thread == null ? "inbox" : "thread"); }
    @Override protected void onPause() { visible = false; retire(); showStatus("Previews cleared. Open Sandesh to refresh."); super.onPause(); }
    @Override protected void onStop() {
        listenerReady = false;
        if (client.isConnected()) Wearable.MessageApi.removeListener(client,this);
        client.disconnect(); peer = null; thread = null;
        super.onStop();
    }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    @Override public void onConnected(Bundle bundle) {
        Wearable.MessageApi.addListener(client,this).setResultCallback(status -> runOnUiThread(() -> {
            if (!visible || !client.isConnected()) return;
            listenerReady = status.isSuccess();
            if (listenerReady) request(thread == null ? "inbox" : "thread");
            else showStatus("Could not listen for phone messages. Tap Refresh.");
        }));
    }
    @Override public void onConnectionSuspended(int reason) { retire(); if (visible) showStatus("Connection paused. Check the phone companion app."); }
    @Override public void onConnectionFailed(ConnectionResult result) { retire(); if (visible) showStatus("Watch services unavailable ("+result.getErrorCode()+"). Check the companion connection."); }

    private void request(String command) {
        if (!visible || busy) return;
        if (!client.isConnected() || !listenerReady) {
            showStatus("Reconnecting…");
            if (client.isConnected()) client.disconnect();
            listenerReady = false; client.connect(); return;
        }
        rows = null; detail = null;
        token = UUID.randomUUID().toString();
        final String id = token;
        busy = true;
        handler.removeCallbacks(expire);
        handler.postDelayed(timeout,15000);
        showStatus("open".equals(command) ? "Requesting phone notification…" : "Loading from phone…");
        Wearable.NodeApi.getConnectedNodes(client).setResultCallback(result -> runOnUiThread(() -> {
            if (!visible || !id.equals(token)) return;
            List<Node> nodes = result.getNodes();
            if (!result.getStatus().isSuccess() || nodes == null || nodes.size() != 1) {
                retire(); showStatus(nodes == null || nodes.isEmpty() ? "No paired phone is reachable. Check Android Wear on the phone." : "Keep one paired phone connected, then refresh."); return;
            }
            peer = nodes.get(0).getId();
            try {
                JSONObject value = new JSONObject().put("version",WatchProtocol.VERSION).put("id",id).put("command",command);
                if (thread != null) value.put("thread",thread);
                Wearable.MessageApi.sendMessage(client,peer,WatchProtocol.REQUEST,WatchProtocol.bytes(value.toString(),WatchProtocol.MAX_REQUEST))
                    .setResultCallback(sent -> runOnUiThread(() -> {
                        if (visible && id.equals(token) && !sent.getStatus().isSuccess()) { retire(); showStatus("Could not reach Sandesh on the phone. Tap Refresh."); }
                    }));
            } catch (Exception ignored) { retire(); showStatus("Could not prepare the request. Tap Refresh."); }
        }));
    }

    @Override public void onMessageReceived(MessageEvent event) {
        if (!WatchProtocol.RESPONSE.equals(event.getPath()) && !WatchProtocol.CLEAR.equals(event.getPath())) return;
        if (event.getData().length > WatchProtocol.MAX_RESPONSE) return;
        final String source = event.getSourceNodeId();
        final boolean clear = WatchProtocol.CLEAR.equals(event.getPath());
        final byte[] bytes = event.getData().clone();
        runOnUiThread(() -> {
            if (!visible || peer == null || !peer.equals(source)) return;
            if (clear) { retire(); showStatus("Phone watch settings changed. Tap Refresh."); return; }
            try {
                JSONObject value = new JSONObject(new String(bytes,StandardCharsets.UTF_8));
                if (value.optInt("version") != WatchProtocol.VERSION || token == null || !token.equals(value.optString("id"))) return;
                handler.removeCallbacks(timeout); busy = false; token = null;
                String status = value.optString("status");
                android.util.Log.i("SandeshWatch", "Reply: "+value.optString("command")+" / "+status);
                if (!"ok".equals(status)) { retire(); showStatus(error(status)); return; }
                palette(value.optString("theme"));
                if ("inbox".equals(value.optString("command"))) {
                    rows = value.optJSONArray("rows");
                    if (rows == null || rows.length() > WatchProtocol.CONVERSATIONS) { retire(); showStatus("Invalid inbox response."); return; }
                    renderInbox();
                } else {
                    JSONArray messages = value.optJSONArray("messages");
                    if (messages == null || messages.length() > WatchProtocol.HISTORY || !thread.equals(value.optString("thread"))) { retire(); showStatus("Invalid conversation response."); return; }
                    detail = value; renderThread();
                }
                handler.postDelayed(expire,60000);
            } catch (Exception ignored) { retire(); showStatus("Could not read the phone response. Tap Refresh."); }
        });
    }

    private String error(String status) {
        switch (status) {
            case "disabled": return "On your phone: Sandesh → Settings → Smartwatch → Enable watch inbox. Then tap Refresh here.";
            case "access": return "Open Sandesh on the phone and finish default SMS / permission setup.";
            case "unavailable": return "This conversation is unavailable, archived, spam or blocked.";
            case "opened": return "Check your phone: tap the Sandesh notification to open this conversation. Nothing was sent.";
            case "notifications": return "Enable Sandesh notifications on the phone, or open Sandesh manually.";
            case "busy": return "Please wait a moment, then tap Refresh.";
            default: return "Sandesh could not load messages. Check the phone and tap Refresh.";
        }
    }
    private void retire() {
        token = null; busy = false; rows = null; detail = null;
        handler.removeCallbacks(timeout); handler.removeCallbacks(expire);
    }
    private void base(String heading) {
        content.removeAllViews(); content.setBackgroundColor(background);
        ((View)content.getParent()).setBackgroundColor(background);
        LinearLayout headingRow=new LinearLayout(this); headingRow.setOrientation(LinearLayout.HORIZONTAL); headingRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView title=new TextView(this); title.setText("Sandesh"); title.setTextSize(21); title.setTextColor(ink); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        headingRow.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button faces=new Button(this); faces.setText("Faces"); faces.setAllCaps(false); faces.setTextSize(11); faces.setContentDescription("Create and apply watch faces");
        faces.setOnClickListener(v->startActivity(new android.content.Intent(this,com.sandesh.watch.face.FaceDesignerActivity.class)));
        headingRow.addView(faces,new LinearLayout.LayoutParams(dp(58),dp(48))); content.addView(headingRow);
        text(heading,12,muted,false);
    }
    private void showStatus(String message) {
        android.util.Log.i("SandeshWatch", "State: "+message);
        base("Your phone’s SMS, on your wrist");
        text(message,15,ink,false).setPadding(0,dp(14),0,dp(12));
        if (!busy) button("Refresh",() -> request(thread == null ? "inbox" : "thread"));
        if (thread != null && !busy) button("Inbox",() -> { thread = null; request("inbox"); });
    }
    private void renderInbox() throws Exception {
        base("10 recent conversations · tap to read");
        button("Refresh",() -> request("inbox"));
        if (rows.length() == 0) text("No inbox conversations. Check Sandesh on your phone.",15,muted,false);
        for (int i=0;i<rows.length();i++) {
            JSONObject row = rows.getJSONObject(i);
            final String id = row.optString("thread");
            if (!WatchProtocol.validThread(id)) continue;
            LinearLayout box = box();
            addText(box,WatchProtocol.clip(row.optString("sender"),60),17,ink,true);
            addText(box,WatchProtocol.clip(row.optString("preview"),140),14,muted,false);
            addText(box,time(row.optLong("date"))+(row.optInt("unread")>0 ? " · "+row.optInt("unread")+" unread" : ""),11,muted,false);
            box.setOnClickListener(v -> { thread=id; request("thread"); });
            box.setContentDescription(row.optString("sender")+", open conversation");
        }
        text("Previews clear when you leave or after one minute. Refresh to load again.",11,muted,false);
    }
    private void renderThread() throws Exception {
        base(WatchProtocol.clip(detail.optString("sender"),60));
        button("Open on phone",() -> request("open"));
        JSONArray messages = detail.getJSONArray("messages");
        text("Latest five messages · previews",12,muted,false);
        for (int i=messages.length()-1;i>=0;i--) {
            JSONObject m=messages.getJSONObject(i);
            LinearLayout box=box();
            addText(box,(m.optBoolean("outgoing") ? "You · " : "")+time(m.optLong("date")),11,muted,true);
            addText(box,WatchProtocol.clip(m.optString("text"),WatchProtocol.TEXT),16,ink,false);
            addText(box,WatchProtocol.clip(m.optString("sim"),30),11,muted,false);
        }
        button("Refresh",() -> request("thread"));
        button("Inbox",() -> { thread=null; request("inbox"); });
        text("Sending and full history stay on your phone.",11,muted,false);
    }
    @Override public void onBackPressed() { if (thread != null) { thread=null; retire(); request("inbox"); } else super.onBackPressed(); }
    private void palette(String theme) {
        if ("DARK".equals(theme)) { background=Color.BLACK; ink=Color.WHITE; muted=0xFFBDBDBD; accent=0xFFFFFFFF; card=0xFF242424; }
        else {
            background=0xFFF5F8FC; ink=0xFF172E2A; muted=0xFF5F6E69; card=Color.WHITE;
            switch(theme) { case "GREEN":accent=0xFF356B55;break; case "PURPLE":accent=0xFF7255A4;break; case "ROSE":accent=0xFFA44E72;break; case "AMBER":accent=0xFF8D681E;break; default:accent=0xFF315ED2; }
        }
    }
    private LinearLayout box() {
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(10),dp(10),dp(10),dp(10));
        GradientDrawable shape=new GradientDrawable(); shape.setColor(card); shape.setCornerRadius(dp(10)); box.setBackground(shape);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,dp(8),0,dp(4)); content.addView(box,lp); return box;
    }
    private TextView text(String value,int size,int color,boolean bold) { return addText(content,value,size,color,bold); }
    private TextView addText(LinearLayout parent,String value,int size,int color,boolean bold) {
        TextView text=new TextView(this); text.setText(value); text.setTextSize(size); text.setTextColor(color);
        if(bold)text.setTypeface(Typeface.DEFAULT,Typeface.BOLD); text.setPadding(0,dp(3),0,dp(3)); parent.addView(text,new LinearLayout.LayoutParams(-1,-2)); return text;
    }
    private void button(String label,Runnable action) {
        Button button=new Button(this); button.setText(label); button.setAllCaps(false); button.setTextSize(14);
        GradientDrawable shape=new GradientDrawable(); shape.setColor(card); shape.setCornerRadius(dp(10)); shape.setStroke(dp(1),accent); button.setBackground(shape);
        button.setTextColor(ink); button.setMinHeight(dp(48)); button.setOnClickListener(v -> action.run()); content.addView(button,new LinearLayout.LayoutParams(-1,-2));
    }
    private String time(long date) { return date<=0 ? "" : DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(date)); }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
}
