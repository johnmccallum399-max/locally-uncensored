package com.lcdr.assistant.tools.impl

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import com.lcdr.assistant.tools.ToolCallSpec
import com.lcdr.assistant.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactTools @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun readContacts(spec: ToolCallSpec): ToolResult = withContext(Dispatchers.IO) {
        val query = spec.arguments["query"] as? String
        try {
            val selection = if (query != null)
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
            else null
            val selectionArgs = if (query != null) arrayOf("%$query%") else null

            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                selection,
                selectionArgs,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC LIMIT 20"
            )

            val result = StringBuilder()
            cursor?.use {
                while (it.moveToNext()) {
                    val name = it.getString(0) ?: "unknown"
                    val phone = it.getString(1) ?: ""
                    result.appendLine("$name: $phone")
                }
            }

            if (result.isEmpty()) ToolResult.Success("No contacts found.")
            else ToolResult.Success(result.toString().trim())
        } catch (e: Exception) {
            ToolResult.Failure("Contact read failed: ${e.message}")
        }
    }

    suspend fun writeContact(spec: ToolCallSpec): ToolResult = withContext(Dispatchers.IO) {
        val name = spec.arguments["name"] as? String
            ?: return@withContext ToolResult.Failure("'name' required")
        val phone = spec.arguments["phone"] as? String
            ?: return@withContext ToolResult.Failure("'phone' required")
        val email = spec.arguments["email"] as? String

        try {
            val ops = ArrayList<ContentProviderOperation>()
            val rawContactIdx = ops.size

            ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build())

            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIdx)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build())

            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIdx)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build())

            if (email != null) {
                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIdx)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                    .build())
            }

            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            ToolResult.Success("Contact '$name' added.")
        } catch (e: Exception) {
            ToolResult.Failure("Write contact failed: ${e.message}")
        }
    }
}
