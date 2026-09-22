package com.lcdr.assistant.tools

import com.lcdr.assistant.data.remote.dto.ToolDefinition

data class ToolCallSpec(
    val id: String,
    val name: String,
    val arguments: Map<String, Any?>
)

sealed class ToolResult {
    data class Success(val content: String) : ToolResult()
    data class Failure(val error: String) : ToolResult()

    val isError get() = this is Failure
    val content get() = when (this) {
        is Success -> content
        is Failure -> error
    }
}

val ALL_TOOL_DEFINITIONS: List<ToolDefinition> = listOf(
    ToolDefinition(
        name = "read_sms",
        description = "Read SMS messages from a contact or all recent messages",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "contact" to mapOf("type" to "string", "description" to "Contact name or phone number (optional)"),
                "limit" to mapOf("type" to "integer", "description" to "Max messages to return (default 10)")
            )
        )
    ),
    ToolDefinition(
        name = "send_sms",
        description = "Send an SMS message to a contact",
        parameters = mapOf(
            "type" to "object",
            "required" to listOf("to", "message"),
            "properties" to mapOf(
                "to" to mapOf("type" to "string", "description" to "Recipient phone number or contact name"),
                "message" to mapOf("type" to "string", "description" to "Message text to send")
            )
        )
    ),
    ToolDefinition(
        name = "read_contacts",
        description = "Search contacts by name or phone number",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "query" to mapOf("type" to "string", "description" to "Search query")
            )
        )
    ),
    ToolDefinition(
        name = "write_contact",
        description = "Add or update a contact",
        parameters = mapOf(
            "type" to "object",
            "required" to listOf("name", "phone"),
            "properties" to mapOf(
                "name" to mapOf("type" to "string"),
                "phone" to mapOf("type" to "string"),
                "email" to mapOf("type" to "string", "description" to "Optional email")
            )
        )
    ),
    ToolDefinition(
        name = "list_events",
        description = "List calendar events in a date range",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "start_date" to mapOf("type" to "string", "description" to "ISO date or 'today'"),
                "end_date" to mapOf("type" to "string", "description" to "ISO date or 'today'")
            )
        )
    ),
    ToolDefinition(
        name = "create_event",
        description = "Create a calendar event",
        parameters = mapOf(
            "type" to "object",
            "required" to listOf("title", "start", "end"),
            "properties" to mapOf(
                "title" to mapOf("type" to "string"),
                "start" to mapOf("type" to "string", "description" to "ISO datetime"),
                "end" to mapOf("type" to "string", "description" to "ISO datetime"),
                "location" to mapOf("type" to "string"),
                "notes" to mapOf("type" to "string")
            )
        )
    ),
    ToolDefinition(
        name = "delete_event",
        description = "Delete a calendar event by ID",
        parameters = mapOf(
            "type" to "object",
            "required" to listOf("id"),
            "properties" to mapOf(
                "id" to mapOf("type" to "string")
            )
        )
    ),
    ToolDefinition(
        name = "list_files",
        description = "List files in a directory path",
        parameters = mapOf(
            "type" to "object",
            "required" to listOf("path"),
            "properties" to mapOf(
                "path" to mapOf("type" to "string", "description" to "Directory path")
            )
        )
    ),
    ToolDefinition(
        name = "read_file",
        description = "Read text file contents",
        parameters = mapOf(
            "type" to "object",
            "required" to listOf("path"),
            "properties" to mapOf(
                "path" to mapOf("type" to "string")
            )
        )
    ),
    ToolDefinition(
        name = "write_file",
        description = "Write or overwrite a text file",
        parameters = mapOf(
            "type" to "object",
            "required" to listOf("path", "content"),
            "properties" to mapOf(
                "path" to mapOf("type" to "string"),
                "content" to mapOf("type" to "string")
            )
        )
    ),
    ToolDefinition(
        name = "get_battery",
        description = "Get battery level and charging state",
        parameters = mapOf("type" to "object", "properties" to emptyMap<String, Any>())
    ),
    ToolDefinition(
        name = "get_location",
        description = "Get current GPS location with reverse geocode",
        parameters = mapOf("type" to "object", "properties" to emptyMap<String, Any>())
    ),
    ToolDefinition(
        name = "set_alarm",
        description = "Create a system alarm",
        parameters = mapOf(
            "type" to "object",
            "required" to listOf("time"),
            "properties" to mapOf(
                "time" to mapOf("type" to "string", "description" to "Time in HH:mm format"),
                "label" to mapOf("type" to "string")
            )
        )
    ),
    ToolDefinition(
        name = "set_timer",
        description = "Create a countdown timer",
        parameters = mapOf(
            "type" to "object",
            "required" to listOf("seconds"),
            "properties" to mapOf(
                "seconds" to mapOf("type" to "integer"),
                "label" to mapOf("type" to "string")
            )
        )
    ),
    ToolDefinition(
        name = "send_notification",
        description = "Post a local notification",
        parameters = mapOf(
            "type" to "object",
            "required" to listOf("title", "body"),
            "properties" to mapOf(
                "title" to mapOf("type" to "string"),
                "body" to mapOf("type" to "string")
            )
        )
    ),
    ToolDefinition(
        name = "get_clipboard",
        description = "Read clipboard text",
        parameters = mapOf("type" to "object", "properties" to emptyMap<String, Any>())
    ),
    ToolDefinition(
        name = "set_clipboard",
        description = "Write text to clipboard",
        parameters = mapOf(
            "type" to "object",
            "required" to listOf("text"),
            "properties" to mapOf(
                "text" to mapOf("type" to "string")
            )
        )
    ),
    ToolDefinition(
        name = "get_running_apps",
        description = "List recent/foreground apps via UsageStats",
        parameters = mapOf("type" to "object", "properties" to emptyMap<String, Any>())
    ),
    ToolDefinition(
        name = "remember",
        description = "Persist a key-value pair to long-term memory",
        parameters = mapOf(
            "type" to "object",
            "required" to listOf("key", "value"),
            "properties" to mapOf(
                "key" to mapOf("type" to "string"),
                "value" to mapOf("type" to "string")
            )
        )
    ),
    ToolDefinition(
        name = "recall",
        description = "Fetch memory entries, optionally by key",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "key" to mapOf("type" to "string", "description" to "Optional key to filter by")
            )
        )
    )
)
