package com.gediadeepak.smsapp.telephony

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.gediadeepak.smsapp.core.DeviceSim

object SmsAccess {
    val permissions = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS,
        Manifest.permission.READ_PHONE_STATE, Manifest.permission.RECEIVE_MMS, Manifest.permission.RECEIVE_WAP_PUSH)
    fun isDefault(context: Context) = if (Build.VERSION.SDK_INT >= 29) context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_SMS) == true
        else Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    fun allowed(context: Context, permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    fun ready(context: Context) = isDefault(context) && arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS,
        Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE).all { allowed(context, it) }
    fun hasTelephony(context: Context) = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    @Suppress("DEPRECATION")
    fun roleIntent(context: Context): Intent? = if (Build.VERSION.SDK_INT >= 29) {
        context.getSystemService(RoleManager::class.java)?.takeIf { it.isRoleAvailable(RoleManager.ROLE_SMS) }?.createRequestRoleIntent(RoleManager.ROLE_SMS)
    } else Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
    fun subscriptions(context: Context): List<DeviceSim> {
        if (!allowed(context, Manifest.permission.READ_PHONE_STATE)) return emptyList()
        return try { context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList.orEmpty()
            .filter { it.simSlotIndex >= 0 }.map { DeviceSim(it.subscriptionId, it.simSlotIndex, it.displayName?.toString().orEmpty().ifBlank { "Mobile line" }, it.carrierName?.toString().orEmpty()) }
        } catch (_: SecurityException) { emptyList() } catch (_: UnsupportedOperationException) { emptyList() }
    }
}
