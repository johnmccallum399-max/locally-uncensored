package com.lcdr.assistant.ui.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdr.assistant.data.local.dao.MessageDao
import com.lcdr.assistant.domain.model.Message
import com.lcdr.assistant.domain.model.toDomain
import com.lcdr.assistant.tools.ToolCallSpec
import com.lcdr.assistant.tools.impl.CalendarTools
import com.lcdr.assistant.tools.impl.SmsTools
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceUiState(
    val batteryPct: Int = 0,
    val isCharging: Boolean = false,
    val recentSms: String = "",
    val todayEvents: String = "",
    val isLoading: Boolean = false
)

@HiltViewModel
class DeviceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smsTools: SmsTools,
    private val calendarTools: CalendarTools
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceUiState())
    val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()

    init {
        loadBattery()
        loadQuickData()
    }

    private fun loadBattery() {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        _uiState.update {
            it.copy(
                batteryPct = level * 100 / scale,
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            )
        }
    }

    fun loadQuickData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val smsSpec = ToolCallSpec("", "read_sms", mapOf("limit" to 5.0))
            val smsResult = smsTools.readSms(smsSpec)

            val calSpec = ToolCallSpec("", "list_events", mapOf("start_date" to "today", "end_date" to "today"))
            val calResult = calendarTools.listEvents(calSpec)

            _uiState.update {
                it.copy(
                    recentSms = if (smsResult.isError) "Permission required" else smsResult.content,
                    todayEvents = if (calResult.isError) "Permission required" else calResult.content,
                    isLoading = false
                )
            }
        }
    }
}
