package com.gediadeepak.smsapp.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.gediadeepak.smsapp.core.PhoneContact
import com.gediadeepak.smsapp.core.validNumber
import com.gediadeepak.smsapp.core.sameContactNumber
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** Device-only, memory-only contacts; never writes names into SMS, aliases or backups. */
class ContactsRepository(private val context: Context) {
    private val revision = AtomicInteger()
    private var cachedRevision = -1
    private var cached = emptyList<PhoneContact>()
    private val names = mutableMapOf<String, String?>()
    fun allowed() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    fun invalidate() { revision.incrementAndGet() }

    @Synchronized fun load(): List<PhoneContact> {
        if (!allowed()) { clear(); return emptyList() }
        if (cachedRevision == revision.get()) return cached
        val version = revision.get()
        val rows = mutableListOf<PhoneContact>()
        try {
            val phone = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val columns = arrayOf(ContactsContract.CommonDataKinds.Phone._ID, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.LABEL)
            context.contentResolver.query(phone, columns, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY + " COLLATE LOCALIZED ASC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    val raw = cursor.getString(2).orEmpty()
                    // Extensions/URI addresses are not silently converted into a different SMS destination.
                    if (!raw.matches(Regex("[+0-9 ()\\-.]+"))) continue
                    val number = PhoneNumberUtils.stripSeparators(raw)
                    if (!validNumber(number)) continue
                    val name = cursor.getString(1)?.trim().orEmpty().ifBlank { number }
                    val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.resources, cursor.getInt(3), cursor.getString(4)).toString()
                    rows += PhoneContact(cursor.getString(0), name, number, label)
                }
            }
            cached = rows.distinctBy { it.name to it.number }
            names.clear()
            cachedRevision = version
            return cached
        } catch (_: SecurityException) { clear(); return emptyList() }
        catch (_: Exception) { clear(); return emptyList() }
    }

    @Synchronized fun name(number: String): String? {
        if (!allowed()) { clear(); return null }
        if (!number.matches(Regex("[+0-9 ()\\-.]+"))) return null
        if (!validNumber(PhoneNumberUtils.stripSeparators(number))) return null
        if (names.containsKey(number)) return names[number]
        val candidates = mutableMapOf<Long, String>()
        try {
            val region = runCatching { context.getSystemService(TelephonyManager::class.java)?.simCountryIso }.getOrNull()
                ?.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT) ?: Locale.getDefault().country
            val incomingE164 = PhoneNumberUtils.formatNumberToE164(number, region)
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.CONTACT_ID, ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.NUMBER, ContactsContract.PhoneLookup.NORMALIZED_NUMBER), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val saved = cursor.getString(2).orEmpty()
                    val savedE164 = cursor.getString(3)?.takeIf { it.startsWith('+') } ?: PhoneNumberUtils.formatNumberToE164(saved, region)
                    if (sameContactNumber(number, incomingE164, saved, savedE164)) cursor.getString(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { candidates[cursor.getLong(0)] = it }
                }
            }
        } catch (_: Exception) { return null }
        // Ambiguous matches keep their number instead of inventing an identity.
        val result = candidates.values.singleOrNull()
        names[number] = result
        return result
    }

    private fun clear() { cached = emptyList(); names.clear(); cachedRevision = -1 }
}
