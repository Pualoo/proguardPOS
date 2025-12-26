package com.example.arduinoposproject

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.util.UUID


val CyberBlack = Color(0xFF050505)
val CyberDark = Color(0xFF121212)
val NeonCyan = Color(0xFF00E5FF)
val NeonGreen = Color(0xFF00FF00)
val NeonRed = Color(0xFFFF0000)
val NeonBlue = Color(0xFF2979FF)

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

    private val topicSensor = "pos_iot/sensor"
    private val topicCommand = "pos_iot/command"

    init {
        connect()
    }

    private fun connect() {
        viewModelScope.launch {
            try {

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
                    _state.value = _state.value.copy(connectionStatus = "🟢 SISTEMA ONLINE (MQTT)")
                    subscribe()
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(connectionStatus = "🔴 CONEXÃO FALHOU: ${e.message}")
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


    Box(modifier = Modifier.fillMaxSize().background(CyberBlack)) {
        StarFieldBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Header(viewModel, state)

            Spacer(modifier = Modifier.height(20.dp))


            if (state.isConfiguring) {
                ConfigDialog(state, viewModel)
            }


            RadarStatus(state)

            Spacer(modifier = Modifier.height(30.dp))


            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val tempColor = if (state.temp > 30) NeonRed else NeonCyan
                InfoCard3D(
                    label = "TEMPERATURA",
                    value = "%.1f°C".format(state.temp),
                    icon = Icons.Default.Info,
                    color = tempColor,
                    modifier = Modifier.weight(1f)
                )
                InfoCard3D(
                    label = "UMIDADE",
                    value = "%.0f%%".format(state.humidity),
                    icon = Icons.Default.Place,
                    color = NeonBlue,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(30.dp))


            CyberButton(
                text = if (state.armed) "DESARMAR SISTEMA" else "ARMAR SISTEMA",
                icon = if (state.armed) Icons.Default.Home else Icons.Default.Lock,
                isActive = state.armed,
                activeColor = NeonRed,
                inactiveColor = NeonCyan,
                onClick = { viewModel.toggleArm() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            CyberButton(
                text = if (state.lightOn) "LUZ: LIGADA" else "LUZ: DESLIGADA",
                icon = if (state.lightOn) Icons.Default.Star else Icons.Default.Notifications,
                isActive = state.lightOn,
                activeColor = Color(0xFFFFD700),
                inactiveColor = Color.Gray,
                onClick = { viewModel.toggleLight() }
            )

            Spacer(modifier = Modifier.height(30.dp))
            

            GlassyCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusColor = if(state.connectionStatus.contains("ONLINE")) NeonGreen else NeonRed
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = state.connectionStatus, color = Color.White.copy(alpha=0.7f), fontSize = 12.sp)
                }
            }
             if (state.connectionStatus.contains("FALHOU") || state.connectionStatus.contains("Falha")) {
                 Spacer(modifier = Modifier.height(8.dp))
                 Button(onClick = { viewModel.retryConnection() }) { Text("RECONECTAR") }
             }
        }
    }
}

@Composable
fun StarFieldBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "stars")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing)),
        label = "t"
    )
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF000000), Color(0xFF1A1A2E))
            )
        )


        val gridCount = 20
        for(i in 0..gridCount) {
             val yPos = (height * 0.3f) + (height * 0.7f) * (i / gridCount.toFloat())
            drawLine(
                color = NeonCyan.copy(alpha = 0.05f),
                start = Offset(0f, yPos),
                end = Offset(width, yPos),
                strokeWidth = 1f
            )
        }
        

        val particles = 50
        for (i in 0 until particles) {
             val x = (i * 1234.56f + time * 500) % width
             val y = (i * 987.65f + time * 100) % height
             val particleSize = (i % 3 + 1).toFloat()
             
             drawCircle(
                 color = Color.White.copy(alpha = 0.2f),
                 radius = particleSize,
                 center = Offset(x, y)
             )
        }
    }
}

@Composable
fun Header(viewModel: SecureIoTViewModel, state: SecureIoTState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "SECURELO",
                color = NeonCyan,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.retryConnection() }) {
               Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Color.White)
            }
        }
    }
}

@Composable
fun RadarStatus(state: SecureIoTState) {
    val isAlarm = state.alarm
    val isArmed = state.armed
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    val mainColor = when {
        isAlarm -> NeonRed
        isArmed -> NeonBlue
        else -> NeonGreen
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        contentAlignment = Alignment.Center
    ) {

        Box(
            modifier = Modifier
                .size(220.dp)
                .scale(pulseScale)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(mainColor.copy(alpha = 0.2f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )
        

         Canvas(modifier = Modifier.size(240.dp)) {
             rotate(rotation) {
                 drawCircle(
                     brush = Brush.sweepGradient(
                         colors = listOf(Color.Transparent, mainColor.copy(alpha = 0.5f), mainColor)
                     ),
                     radius = size.minDimension / 2,
                     style = Stroke(width = 4.dp.toPx())
                 )
             }
         }


         Box(
             modifier = Modifier
                 .size(180.dp)
                 .graphicsLayer {
                     shadowElevation = 20.dp.toPx()
                 }
                 .background(
                     brush = Brush.linearGradient(
                         colors = listOf(Color(0xFF232323), Color.Black)
                     ),
                     shape = CircleShape
                 )
                 .border(2.dp, mainColor.copy(alpha = 0.6f), CircleShape),
             contentAlignment = Alignment.Center
         ) {
             Column(horizontalAlignment = Alignment.CenterHorizontally) {
                 Icon(
                    imageVector = if (isAlarm) Icons.Filled.Warning else if (isArmed) Icons.Filled.Lock else Icons.Filled.Home,
                    contentDescription = null,
                    tint = mainColor,
                    modifier = Modifier.size(48.dp)
                 )
                 Spacer(modifier = Modifier.height(8.dp))
                 Text(
                     text = if (isAlarm) "ALARME" else if (isArmed) "ARMADO" else "SEGURO",
                     color = Color.White,
                     fontSize = 24.sp,
                     fontWeight = FontWeight.Bold
                 )
                 
                 if (state.motion) {
                     Spacer(modifier = Modifier.height(4.dp))
                     Text(
                         text = "MOVIMENTO DETECTADO",
                         color = NeonRed,
                         fontSize = 12.sp,
                         fontWeight = FontWeight.Bold
                     )
                 }
             }
         }
    }
}

@Composable
fun InfoCard3D(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    GlassyCard(
        modifier = modifier
            .height(130.dp)
            .graphicsLayer {
                rotationX = 5f
            }
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(text = label, fontSize = 12.sp, color = Color.Gray, letterSpacing = 1.sp)
        }
    }
}

@Composable
fun CyberButton(text: String, icon: ImageVector, isActive: Boolean, activeColor: Color, inactiveColor: Color, onClick: () -> Unit) {
    val animateColor by animateColorAsState(targetValue = if (isActive) activeColor else inactiveColor)
    
    GlassyCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .clickable { onClick() }
    ) {
         Row(
             modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
             verticalAlignment = Alignment.CenterVertically,
             horizontalArrangement = Arrangement.SpaceBetween
         ) {
             Row(verticalAlignment = Alignment.CenterVertically) {
                 Icon(icon, contentDescription = null, tint = animateColor, modifier = Modifier.size(28.dp))
                 Spacer(modifier = Modifier.width(16.dp))
                 Text(
                     text = text,
                     fontSize = 18.sp,
                     fontWeight = FontWeight.Bold,
                     color = Color.White
                 )
             }
             

             Box(
                 modifier = Modifier
                     .size(12.dp)
                     .background(
                         color = animateColor,
                         shape = CircleShape
                     )
                     .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
             )
         }
         

         Box(
             modifier = Modifier
                 .align(Alignment.BottomStart)
                 .fillMaxWidth()
                 .height(2.dp)
                 .background(animateColor)
         )
    }
}

@Composable
fun GlassyCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF252525).copy(alpha = 0.9f), Color(0xFF1E1E1E).copy(alpha = 0.8f))
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.1f), Color.Transparent)
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp)),
        content = content
    )
}

@Composable
fun ConfigDialog(state: SecureIoTState, viewModel: SecureIoTViewModel) {
    var newUrl by remember { mutableStateOf(state.brokerUrl) }
    
    AlertDialog(
        containerColor = CyberDark,
        titleContentColor = NeonCyan,
        textContentColor = Color.LightGray,
        onDismissRequest = { viewModel.toggleConfig() },
        title = { Text("SYSTEM CONFIG") },
        text = {
            Column {
                Text("MQTT Broker URL:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newUrl,
                    onValueChange = { newUrl = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = NeonCyan,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.updateBrokerUrl(newUrl) }) {
                Text("CONNECT", color = NeonCyan)
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.toggleConfig() }) {
                Text("CANCEL", color = Color.Gray)
            }
        }
    )
}
