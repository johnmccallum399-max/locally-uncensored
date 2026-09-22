package com.lcdr.assistant.tools.impl

import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import com.lcdr.assistant.tools.ToolCallSpec
import com.lcdr.assistant.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsTools @Inject constructor(@ApplicationContext private val context: Context) {

    @Suppress("DEPRECATION")
    suspend fun readSms(spec: ToolCallSpec): ToolResult = withContext(Dispatchers.IO) {
        try {
            val contactQuery = spec.arguments["contact"] as? String
            val limit = (spec.arguments["limit"] as? Double)?.toInt() ?: 10

            val uri = Telephony.Sms.CONTENT_URI
            val selection = if (contactQuery != null) {
                "${Telephony.Sms.ADDRESS} LIKE ?"
            } else null
            val selectionArgs = if (contactQuery != null) arrayOf("%$contactQuery%") else null

            val cursor = context.contentResolver.query(
                uri,
                arrayOf(
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE
                ),
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC LIMIT $limit"
            )

            val messages = StringBuilder()
            cursor?.use {
                while (it.moveToNext()) {
                    val address = it.getString(0) ?: "unknown"
                    val body = it.getString(1) ?: ""
                    val date = it.getLong(2)
                    val type = it.getInt(3)
                    val direction = if (type == Telephony.Sms.MESSAGE_TYPE_SENT) "→" else "←"
                    val name = resolveContactName(address)
                    messages.appendLine("[$direction ${name ?: address}] $body")
                }
            }

            if (messages.isEmpty()) ToolResult.Success("No SMS messages found.")
            else ToolResult.Success(messages.toString().trim())
        } catch (e: Exception) {
            ToolResult.Failure("SMS read failed: ${e.message}")
        }
    }

    suspend fun sendSms(spec: ToolCallSpec): ToolResult = withContext(Dispatchers.IO) {
        val to = spec.arguments["to"] as? String
            ?: return@withContext ToolResult.Failure("'to' is required")
        val message = spec.arguments["message"] as? String
            ?: return@withContext ToolResult.Failure("'message' is required")

        val phone = resolvePhoneNumber(to) ?: to

        try {
            @Suppress("DEPRECATION")
            val smsManager = context.getSystemService(SmsManager::class.java)
                ?: SmsManager.getDefault()

            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phone, null, message, null, null)
            }
            ToolResult.Success("SMS sent to $phone.")
        } catch (e: Exception) {
            ToolResult.Failure("SMS send failed: ${e.message}")
        }
    }

    private fun resolveContactName(phone: String): String? {
        return try {
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(phone)
            )
            context.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )?.use { it.takeIf { c -> c.moveToFirst() }?.getString(0) }
        } catch (_: Exception) { null }
    }

    private fun resolvePhoneNumber(nameOrPhone: String): String? {
        if (nameOrPhone.matches(Regex("[0-9+\\-() ]+"))) return nameOrPhone
        return try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$nameOrPhone%"),
                null
            )?.use { it.takeIf { c -> c.moveToFirst() }?.getString(0) }
        } catch (_: Exception) { null }
    }
}
