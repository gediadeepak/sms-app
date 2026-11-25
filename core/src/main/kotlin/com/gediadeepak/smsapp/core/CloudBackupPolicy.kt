package com.gediadeepak.smsapp.core

/** Automatic device copies cannot share the manual account pointer or another installation's pointer. */
object CloudBackupPolicy {
    private val deviceId=Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    fun automaticSource(installationId:String):String {
        require(deviceId.matches(installationId)) { "Invalid backup installation identity." }
        return "auto-"+installationId
    }
    fun validSource(id:String)=id=="latest" || (id.startsWith("auto-") && deviceId.matches(id.removePrefix("auto-")))
}
