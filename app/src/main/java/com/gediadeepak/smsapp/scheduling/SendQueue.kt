package com.gediadeepak.smsapp.scheduling

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteDatabase
import androidx.work.*
import com.gediadeepak.smsapp.core.*
import com.gediadeepak.smsapp.data.SandeshDatabase
import com.gediadeepak.smsapp.telephony.*
import java.util.UUID
import java.util.concurrent.TimeUnit

data class QueuedSms(val id:String,val number:String,val body:String,val sim:Int,val due:Long,val state:String,val detail:String,val thread:String="")

class SendQueue(context:Context): SQLiteOpenHelper(context,"sandesh_send_queue.db",null,2) {
    override fun onCreate(db:SQLiteDatabase) { db.execSQL("CREATE TABLE queue(id TEXT PRIMARY KEY, number TEXT NOT NULL, body TEXT NOT NULL, sim INTEGER NOT NULL, due INTEGER NOT NULL, state TEXT NOT NULL, detail TEXT NOT NULL, thread TEXT NOT NULL DEFAULT '')") }
    override fun onUpgrade(db:SQLiteDatabase,old:Int,new:Int) {if(old<2) db.execSQL("ALTER TABLE queue ADD COLUMN thread TEXT NOT NULL DEFAULT ''")}
    fun list():List<QueuedSms> = readableDatabase.rawQuery("SELECT * FROM queue ORDER BY CASE WHEN state IN ('WAITING','CLAIMED') THEN 0 ELSE 1 END, CASE WHEN state IN ('WAITING','CLAIMED') THEN due ELSE -due END LIMIT 200",null).use { c -> buildList { while(c.moveToNext()) add(QueuedSms(c.getString(0),c.getString(1),c.getString(2),c.getInt(3),c.getLong(4),c.getString(5),c.getString(6),c.getString(7))) } }
    fun find(id:String):QueuedSms? = readableDatabase.query("queue",null,"id=?",arrayOf(id),null,null,null).use { c -> if(c.moveToFirst()) QueuedSms(c.getString(0),c.getString(1),c.getString(2),c.getInt(3),c.getLong(4),c.getString(5),c.getString(6),c.getString(7)) else null }
    @Synchronized fun add(number:String,body:String,sim:Int,due:Long,thread:String=""):String {
        require(validNumber(number) && body.isNotBlank() && body.length<=1600)
        val count=readableDatabase.rawQuery("SELECT COUNT(*) FROM queue WHERE state='WAITING'",null).use {it.moveToFirst();it.getInt(0)}
        require(count<100){"Cancel an upcoming message before adding more."}
        val id=UUID.randomUUID().toString()
        writableDatabase.insertOrThrow("queue",null,ContentValues().apply {put("id",id);put("number",number);put("body",body);put("sim",sim);put("due",due);put("state","WAITING");put("detail","");put("thread",thread)})
        return id
    }
    fun claim(id:String)=writableDatabase.update("queue",ContentValues().apply {put("state","CLAIMED");put("detail","Handing to Android. If interrupted, check the conversation before sending again.")},"id=? AND state='WAITING'",arrayOf(id))==1
    fun cancel(id:String)=writableDatabase.update("queue",ContentValues().apply {put("state","CANCELLED");put("detail","Nothing was sent.")},"id=? AND state='WAITING'",arrayOf(id))==1
    fun finish(id:String,state:String,detail:String) {writableDatabase.update("queue",ContentValues().apply {put("state",state);put("detail",detail.take(300))},"id=?",arrayOf(id))}
    fun remove(id:String) {writableDatabase.delete("queue","id=? AND state NOT IN ('WAITING','CLAIMED')",arrayOf(id))}
    companion object {
        fun enqueue(context:Context,number:String,body:String,sim:Int,due:Long,thread:String=""):String {
            val id=SendQueue(context).use {it.add(number,body,sim,due,thread)}
            try { WorkManager.getInstance(context).enqueueUniqueWork("sms-$id",ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ScheduledSmsWorker>().setInputData(workDataOf("id" to id))
                    .setInitialDelay((due-System.currentTimeMillis()).coerceAtLeast(0),TimeUnit.MILLISECONDS).build()).result.get(15,TimeUnit.SECONDS) }
            catch(e:Exception){ runCatching {SendQueue(context).use {it.cancel(id)}}; throw IllegalStateException("Scheduling could not be confirmed. Your draft was kept; check Scheduled & Undo before trying again.",e) }
            return id
        }
        fun cancel(context:Context,id:String):Boolean {
            val cancelled=SendQueue(context).use {it.cancel(id)}
            if(cancelled) WorkManager.getInstance(context).cancelUniqueWork("sms-$id")
            return cancelled
        }
    }
}

/** At-most-once carrier handoff: never automatically retry after claiming a send. */
class ScheduledSmsWorker(context:Context,params:WorkerParameters):Worker(context,params) {
    override fun doWork():Result {
        val id=inputData.getString("id") ?: return Result.failure()
        try {
        SendQueue(applicationContext).use { queue ->
            val item=queue.find(id) ?: return Result.success()
            if(item.state=="CLAIMED") {queue.finish(id,"REVIEW","Sending was interrupted. Check sent messages before trying again.");return Result.success()}
            if(item.state!="WAITING") return Result.success()
            val now=System.currentTimeMillis()
            if(now<item.due) return Result.retry()
            if(!queue.claim(id)) return Result.success()
            try {
                check(!SendPolicy.expired(now,item.due)){"This message is over 24 hours late. Review it and schedule again."}
                check(!SandeshDatabase.get(applicationContext).isSenderBlocked(item.number) && SandeshDatabase.get(applicationContext).threadMetadata(item.thread)?.spam != true){"Sender is blocked. Review the message before sending."}
                // Transport rechecks default-SMS role, permission and the exact saved SIM.
                val uri=SmsTransport(applicationContext).send(item.number,item.body,item.sim)
                if(SandeshDatabase.get(applicationContext).delivery(uri.lastPathSegment.orEmpty())==Delivery.FAILED) queue.finish(id,"REVIEW","Android reported a send failure. Review the failed SMS before sending again.")
                else queue.finish(id,"SUBMITTED","Submitted to Android. Check delivery status in the conversation. SMS reference: ${uri.lastPathSegment}")
            } catch(e:Exception) {
                queue.finish(id,"REVIEW",e.message ?: "Could not submit this message. Review before trying again.")
                SmsNotifications.failure(applicationContext,"A scheduled message needs review. Open Sandesh → Scheduled & Undo.")
            }
        }
        return Result.success()
        } catch(e:Exception) {
            // A durable CLAIMED row prevents a second carrier handoff even if a final DB write failed.
            return Result.retry()
        }
    }
}
