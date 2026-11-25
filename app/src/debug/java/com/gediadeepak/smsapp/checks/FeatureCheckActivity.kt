package com.gediadeepak.smsapp.checks

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import com.gediadeepak.smsapp.data.SandeshDatabase
import com.gediadeepak.smsapp.scheduling.SendQueue
import com.gediadeepak.smsapp.backup.AutomaticBackups
import java.io.File
import javax.crypto.Cipher
import javax.crypto.SecretKey

/** Development-only isolated DB/crypto checks. Never reads SMS, schedules work or calls SmsManager. */
class FeatureCheckActivity:Activity() {
    override fun onCreate(state:Bundle?) {super.onCreate(state);Thread {
        try {checks();android.util.Log.i("SandeshFeatureCheck","PASS: migrations 1/2/3, queue cancellation/claim/reopen, Keystore password roundtrip; zero carrier calls")}
        catch(e:Throwable) {android.util.Log.e("SandeshFeatureCheck","FAIL: "+e.javaClass.simpleName+" / "+e.message)}
        finally {runOnUiThread {finish()}}
    }.start()}
    private class Isolated(base:Context):ContextWrapper(base) {
        override fun getApplicationContext():Context=this
        override fun getDatabasePath(name:String)=File(filesDir,"feature-check/$name").also {it.parentFile!!.mkdirs()}
        override fun openOrCreateDatabase(name:String,mode:Int,factory:SQLiteDatabase.CursorFactory?):SQLiteDatabase=SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),factory)
        override fun openOrCreateDatabase(name:String,mode:Int,factory:SQLiteDatabase.CursorFactory?,handler:DatabaseErrorHandler?):SQLiteDatabase=SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path,factory,handler)
        override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("feature_check_"+name,mode)
    }
    private fun checks() {
        val isolated=Isolated(this)
        val constructor=SandeshDatabase::class.java.getDeclaredConstructor(Context::class.java).apply {isAccessible=true}
        for(version in 1..3) {
            SQLiteDatabase.deleteDatabase(isolated.getDatabasePath("sandesh.db"))
            SQLiteDatabase.openOrCreateDatabase(isolated.getDatabasePath("sandesh.db"),null).use {db ->
                db.execSQL("CREATE TABLE thread_metadata(thread_id TEXT PRIMARY KEY, starred INTEGER NOT NULL DEFAULT 0, archived INTEGER NOT NULL DEFAULT 0, alias TEXT NOT NULL DEFAULT ''"+(if(version>=3)",spam INTEGER NOT NULL DEFAULT 0" else "")+")")
                db.execSQL("INSERT INTO thread_metadata(thread_id,starred,alias) VALUES ('42',1,'fixture')");db.version=version
            }
            constructor.newInstance(isolated).use {db ->
                db.updateMetadata("42") {it.copy(pinned=true,vendor="Fixture business")}
                val item=db.threadMetadata("42")!!;check(item.starred && item.pinned && !item.spam && item.vendor=="Fixture business" && item.alias=="fixture")
                check(db.metadata()["42"]==item)
            }
        }
        SQLiteDatabase.deleteDatabase(isolated.getDatabasePath("sandesh.db"))
        SQLiteDatabase.deleteDatabase(isolated.getDatabasePath("sandesh_send_queue.db"))
        var first="";var second=""
        SendQueue(isolated).use {q ->
            first=q.add("+447700900000","fixture only",1,System.currentTimeMillis()+86400000,"42")
            check(q.cancel(first));check(!q.claim(first))
            second=q.add("+447700900000","fixture only",1,System.currentTimeMillis()+86400000,"42")
            check(q.claim(second));check(!q.cancel(second));check(!q.claim(second))
        }
        SendQueue(isolated).use {q ->check(q.find(first)!!.state=="CANCELLED");check(q.find(second)!!.state=="CLAIMED");check(q.find(second)!!.thread=="42")}
        SQLiteDatabase.deleteDatabase(isolated.getDatabasePath("sandesh_send_queue.db"))
        val store=AutomaticBackups(isolated)
        val key=AutomaticBackups::class.java.getDeclaredMethod("key").apply {isAccessible=true}.invoke(store) as SecretKey
        val original="fixture-password-Δ2026".toCharArray();val bytes=String(original).toByteArray(Charsets.UTF_8)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key)
        val encoded=cipher.doFinal(bytes);bytes.fill(0)
        store.prefs.edit().putString("password",Base64.encodeToString(encoded,Base64.NO_WRAP)).putString("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)).commit()
        val decoded=store.password();check(decoded.contentEquals(original));decoded.fill('\u0000');original.fill('\u0000');store.prefs.edit().clear().commit()
    }
}
