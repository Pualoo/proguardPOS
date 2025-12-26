package com.example.arduinoposproject

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.util.UUID

val BgColor = Color(0xFF121212)
val CardBg = Color(0xFF1E1E1E)
val TextColor = Color(0xFFE0E0E0)
val AccentColor = Color(0xFFBB86FC)
val DangerColor = Color(0xFFCF6679)
val SuccessColor = Color(0xFF03DAC6)

data class SecureIoTState(
    val temp: Double = 0.0,
    val humidity: Double = 0.0,
    val motion: Boolean = false,
    val armed: Boolean = false,
    val alarm: Boolean = false,
    val lightOn: Boolean = false,
    val connectionStatus: String = "Desconectado...",
    val brokerUrl: String = "tcp://broker.hivemq.com:1883",
    val isConfiguring: Boolean = false
)

class SecureIoTViewModel : ViewModel() {
    private val _state = MutableStateFlow(SecureIoTState())
    val state: StateFlow<SecureIoTState> = _state.asStateFlow()

    private var client: MqttClient? = null
    // removed hardcoded broker in favor of state.brokerUrl
    private val topicSensor = "pos_iot/sensor"
    private val topicCommand = "pos_iot/command"

    init {
        connect()
    }

    private fun connect() {
        viewModelScope.launch {
            try {
                // Disconnect existing client if any
                try {
                    if (client?.isConnected == true) {
                        client?.disconnect()
                    }
                } catch (e: Exception) { e.printStackTrace() }

                _state.value = _state.value.copy(connectionStatus = "Conectando a ${_state.value.brokerUrl}...")
                val clientId = "android_" + UUID.randomUUID().toString().substring(0, 8)
                client = MqttClient(_state.value.brokerUrl, clientId, MemoryPersistence())

                val options = MqttConnectOptions()
                options.isCleanSession = true
                
                client?.connect(options)
                
                if (client?.isConnected == true) {
                    _state.value = _state.value.copy(connectionStatus = "🟢 Conectado à Nuvem (MQTT)")
                    subscribe()
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(connectionStatus = "🔴 Falha na conexão: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun subscribe() {
        try {
            client?.subscribe(topicSensor) { _, message ->
                val payload = String(message.payload)
                parseMessage(payload)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseMessage(payload: String) {
        try {
            val json = JSONObject(payload)
            val temp = json.optDouble("temp", 0.0)
            val humidity = json.optDouble("humidity", 0.0)
            val motion = json.optInt("motion", 0) == 1
            val armed = json.optBoolean("armed", false)
            val alarm = json.optBoolean("alarm", false)
            
            _state.value = _state.value.copy(
                temp = temp,
                humidity = humidity,
                motion = motion,
                armed = armed,
                alarm = alarm,
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleArm() {
        val newState = !_state.value.armed
        sendCommand(if (newState) "ARM" else "DISARM")
    }

    fun toggleLight() {
        val newState = !_state.value.lightOn
        _state.value = _state.value.copy(lightOn = newState)
        sendCommand(if (newState) "LIGHT_ON" else "LIGHT_OFF")
    }
    
    fun retryConnection() {
        connect()
    }

    fun updateBrokerUrl(newUrl: String) {
        _state.value = _state.value.copy(brokerUrl = newUrl, isConfiguring = false)
        retryConnection()
    }

    fun toggleConfig() {
        _state.value = _state.value.copy(isConfiguring = !_state.value.isConfiguring)
    }

    private fun sendCommand(cmd: String) {
        viewModelScope.launch {
            try {
                if (client?.isConnected == true) {
                    val message = MqttMessage(cmd.toByteArray())
                    client?.publish(topicCommand, message)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

@Composable
fun SecureIoTApp(viewModel: SecureIoTViewModel) {
    val state by viewModel.state.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BgColor
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(BgColor, Color.Black)
                    )
                )
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔐 SecureIoT Home",
                    color = AccentColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    IconButton(onClick = { viewModel.retryConnection() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reconnect", tint = TextColor)
                    }
                    IconButton(onClick = { viewModel.toggleConfig() }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Config", tint = AccentColor)
                    }
                }
            }

            if (state.isConfiguring) {
                var newUrl by remember { mutableStateOf(state.brokerUrl) }
                AlertDialog(
                    onDismissRequest = { viewModel.toggleConfig() },
                    title = { Text("Configurar Servidor MQTT") },
                    text = {
                        Column {
                            Text("URL do Broker:", color = TextColor)
                            Spacer(modifier = Modifier.height(8.dp))
                            TextField(
                                value = newUrl,
                                onValueChange = { newUrl = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.updateBrokerUrl(newUrl) }) {
                            Text("Salvar e Conectar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.toggleConfig() }) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            StatusCard(state)

            Spacer(modifier = Modifier.height(15.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                SensorBox(label = "Temperatura", value = "%.1f°C".format(state.temp), modifier = Modifier.weight(1f))
                SensorBox(label = "Umidade", value = "%.0f%%".format(state.humidity), modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { viewModel.toggleArm() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.armed) CardBg else AccentColor,
                    contentColor = if (state.armed) TextColor else Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp)
                    .then(
                        if (state.armed) Modifier.border(2.dp, AccentColor, RoundedCornerShape(8.dp)) else Modifier
                    )
            ) {
                Text(
                    text = if (state.armed) "DESARMAR SISTEMA" else "🛡️ ARMAR SISTEMA",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = { viewModel.toggleLight() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuccessColor,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp)
            ) {
                Text(
                    text = if (state.lightOn) "💡 LUZ: ON" else "💡 LUZ: OFF",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = state.connectionStatus,
                color = TextColor.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
            
            if (state.connectionStatus.contains("Falha")) {
                 Row(
                     horizontalArrangement = Arrangement.spacedBy(8.dp)
                 ) {
                     TextButton(onClick = { viewModel.retryConnection() }) {
                         Text("Tentar novamente", color = AccentColor)
                     }
                     TextButton(onClick = { viewModel.toggleConfig() }) {
                         Text("Configurar Servidor", color = TextColor)
                     }
                 }
            }
        }
    }
}

@Composable
fun StatusCard(state: SecureIoTState) {
    val isAlarm = state.alarm
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val borderColor = if (isAlarm) DangerColor.copy(alpha = alpha) else Color.Transparent
    val shadowElevation = if (isAlarm) 12.dp else 6.dp

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = shadowElevation),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isAlarm) Modifier.border(3.dp, borderColor, RoundedCornerShape(12.dp)) else Modifier)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isAlarm) "🚨 ALARME DISPARADO 🚨" else if (state.armed) "🛡️ SISTEMA ARMADO" else "🔓 Sistema Desarmado",
                color = if (isAlarm) DangerColor else TextColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (state.motion) "⚠️ MOVIMENTO DETECTADO!" else "Nenhum Movimento",
                color = if (state.motion) DangerColor else TextColor,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun SensorBox(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(15.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                color = SuccessColor,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                color = TextColor.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        }
    }
}
