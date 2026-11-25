package com.gediadeepak.smsapp.ui

import android.graphics.Rect
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.MessageClient
import com.sandesh.watch.FaceDesign
import com.sandesh.watch.face.FaceJson
import com.sandesh.watch.face.FacePainter
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.UUID

@Composable
internal fun WatchFacesScreen(onBack: () -> Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val client=remember { Wearable.getMessageClient(context) }
    var faces by remember { mutableStateOf(emptyList<FaceDesign>()) }
    var draft by remember { mutableStateOf(FaceDesign.retro()) }
    var name by remember { mutableStateOf(draft.name) }
    var status by remember { mutableStateOf("Connect your paired watch, then refresh.") }
    var pending by remember { mutableStateOf<String?>(null) }
    var peer by remember { mutableStateOf<String?>(null) }
    var timeout by remember { mutableStateOf<Job?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while(true) { delay(1000); now=System.currentTimeMillis() } }
    fun select(face: FaceDesign) { draft=face; name=face.name }
    fun request(command: String) {
        if(pending!=null) return
        val id=UUID.randomUUID().toString(); pending=id; status="Connecting to watch…"
        scope.launch {
            try {
                val nodes=withTimeout(10000) { Wearable.getNodeClient(context).connectedNodes.await() }
                val node=nodes.singleOrNull() ?: throw IllegalStateException(if(nodes.isEmpty()) "No watch connected. Check the companion app and Bluetooth." else "Connect only the watch you want to configure.")
                peer=node.id
                val body=JSONObject().put("id",id).put("version",1).put("command",command)
                if(command=="save" || command=="apply") body.put("face",FaceJson.encode(FaceDesign(draft.id,name,draft.style,draft.color,draft.date,draft.battery,draft.seconds,draft.twentyFour)))
                if(command=="delete") body.put("faceId",draft.id)
                timeout=scope.launch { delay(15000); if(pending==id) { pending=null; status="Watch did not respond. Install the updated watch APK, then refresh." } }
                client.sendMessage(node.id,"/sandesh/faces/v1/request",body.toString().toByteArray(Charsets.UTF_8)).await()
            } catch(e: Exception) { if(e is CancellationException && e !is TimeoutCancellationException) throw e; timeout?.cancel(); pending=null; status=if(e is TimeoutCancellationException) "Watch connection timed out. Check Bluetooth and retry." else e.message ?: "Could not connect to the watch." }
        }
    }
    DisposableEffect(client) {
        val listener=MessageClient.OnMessageReceivedListener { event ->
            if(event.path=="/sandesh/faces/v1/response" && event.sourceNodeId==peer && event.data.size<=32768) {
                runCatching {
                    val response=JSONObject(String(event.data,Charsets.UTF_8))
                    if(response.optInt("version")==1 && response.optString("id")==pending) {
                        val rows=response.getJSONArray("faces")
                        require(rows.length()<=12)
                        val parsed=(0 until rows.length()).map { FaceJson.decode(rows.getJSONObject(it)) }
                        timeout?.cancel(); pending=null; faces=parsed
                        val result=response.optString("status")
                        status=when(result) {
                            "confirm" -> "Design sent. Confirm Sandesh in the watch’s face picker to apply it."
                            "applied" -> "Face applied on your watch."
                            "error" -> response.optString("error","Could not save face.")
                            else -> "Watch gallery updated. Save keeps a design; Apply changes the clock."
                        }
                        if(result!="error") parsed.firstOrNull { it.id==draft.id }?.let { select(it) }
                    }
                }
            }
        }
        client.addListener(listener)
        onDispose { timeout?.cancel(); client.removeListener(listener) }
    }
    SettingsPage("Watch face designer",onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(status)
            OutlinedButton(onClick={request("list")},enabled=pending==null) { Text("Refresh watch gallery") }
            AndroidView(factory={ ctx -> object: View(ctx) {
                val painter=FacePainter()
                var face=draft
                override fun onDraw(canvas: android.graphics.Canvas) { painter.draw(canvas,Rect(0,0,width,height),face,System.currentTimeMillis(),-1,false,false,false) }
            } },update={ it.face=draft; now; it.invalidate() },modifier=Modifier.align(Alignment.CenterHorizontally).widthIn(max=320.dp).fillMaxWidth().aspectRatio(1f))
            Text("Preview uses your phone’s local time. Battery appears on the watch.",style=MaterialTheme.typography.bodySmall)
            Text("Saved on watch",style=MaterialTheme.typography.titleMedium)
            faces.forEach { face -> OutlinedButton(onClick={select(face)},enabled=pending==null,modifier=Modifier.fillMaxWidth()) { Text(face.name) } }
            Button(onClick={select(FaceDesign(UUID.randomUUID().toString(),"My face ${faces.size+1}","RETRO_DUAL","GREEN",true,true,true,true))},enabled=pending==null) { Text("New face") }
            OutlinedTextField(value=name,onValueChange={name=it.take(24)},label={Text("Face name")},singleLine=true,enabled=pending==null)
            Text("Clock style",style=MaterialTheme.typography.titleMedium)
            val labels=listOf("Retro LCD","Digital","Analog","Minimal","Retro Dual")
            FaceDesign.STYLES.forEachIndexed { index,value ->
                Row { RadioButton(selected=draft.style==value,onClick={draft=FaceDesign(draft.id,draft.name,value,draft.color,draft.date,draft.battery,draft.seconds,draft.twentyFour)},enabled=pending==null); Text(labels[index],Modifier.padding(top=12.dp)) }
            }
            Text("Color",style=MaterialTheme.typography.titleMedium)
            FaceDesign.COLORS.toList().chunked(3).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { row.forEach { value -> FilterChip(selected=draft.color==value,onClick={draft=FaceDesign(draft.id,draft.name,draft.style,value,draft.date,draft.battery,draft.seconds,draft.twentyFour)},label={Text(value.lowercase().replaceFirstChar { it.uppercase() })},enabled=pending==null) } } }
            listOf("Date" to draft.date,"Watch battery" to draft.battery,"Seconds while awake" to draft.seconds,"24-hour clock" to draft.twentyFour).forEachIndexed { index,pair ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(pair.first,Modifier.padding(top=12.dp)); Switch(checked=pair.second,enabled=pending==null,onCheckedChange={value -> draft=FaceDesign(draft.id,draft.name,draft.style,draft.color,if(index==0)value else draft.date,if(index==1)value else draft.battery,if(index==2)value else draft.seconds,if(index==3)value else draft.twentyFour) }) }
            }
            Button(onClick={request("save")},enabled=pending==null,modifier=Modifier.fillMaxWidth()) { Text("Save to watch") }
            Button(onClick={request("apply")},enabled=pending==null,modifier=Modifier.fillMaxWidth()) { Text("Apply on watch") }
            OutlinedButton(onClick={request("delete")},enabled=pending==null && faces.any { it.id==draft.id },modifier=Modifier.fillMaxWidth()) { Text("Remove saved face") }
            Text("Choose Sandesh in the watch’s picker the first time. Later designs apply from here. Designs stay on the watch; no SMS text or cloud account is used. Ambient mode shows a simple white clock to save power.",style=MaterialTheme.typography.bodySmall)
        }
    }
}

