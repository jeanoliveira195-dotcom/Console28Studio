package com.jean.console28.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jean.console28.audio.ConsoleSettings
import com.jean.console28.audio.SpectrumFrame
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Instrumentação temática hi-fi: preto fosco, leds verdes/vermelhos e bordas âmbar.
private val Obsidian = Color(0xFF090C0D)
private val NearBlack = Color(0xFF111617)
private val Amber = Color(0xFFF4C65D)
private val Ivory = Color(0xFFF1E7CE)
private val Green = Color(0xFF57E454)
private val Red = Color(0xFFFF4E42)
private val AmberDark = Color(0xFF806025)

@Composable
fun RetroLedRail(isPlaying: Boolean, latest: () -> SpectrumFrame) {
    val latestValue by rememberUpdatedState(latest)
    var energy by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        while (true) {
            val f = latestValue()
            val target = if (isPlaying) max(f.left, f.right) else 0f
            energy += (target - energy) * if (target > energy) 0.6f else 0.2f
            delay(75)
        }
    }
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        drawRoundRect(Obsidian, cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
        val count = 26
        val gap = 4.dp.toPx()
        val barWidth = (size.width - gap * (count + 1)) / count
        for (i in 0 until count) {
            val x = gap + i * (barWidth + gap)
            val on = i < (energy.coerceIn(0f, 1f) * count * 2.8f).toInt()
            val color = when {
                i >= 22 -> Red
                i >= 16 -> Amber
                else -> Green
            }
            drawRoundRect(
                color = color.copy(alpha = if (on) 0.92f else 0.11f),
                topLeft = Offset(x, 3.dp.toPx()),
                size = Size(barWidth.coerceAtLeast(1f), size.height - 6.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            )
        }
    }
}

/** Analisador baseado no PCM real. Não representa níveis de engenharia calibrados. */
@Composable
fun AudioMonitorPanel(isPlaying: Boolean, latest: () -> SpectrumFrame) {
    val latestValue by rememberUpdatedState(latest)
    var visible by remember { mutableStateOf(SpectrumFrame(FloatArray(16), 0f, 0f, 0L)) }
    LaunchedEffect(isPlaying) {
        val smoothed = FloatArray(16)
        var left = 0f
        var right = 0f
        while (true) {
            val raw = latestValue()
            for (i in smoothed.indices) {
                val desired = if (isPlaying) raw.bands.getOrElse(i){0f}.coerceIn(0f, 1f) else 0f
                val k = if (desired > smoothed[i]) 0.75f else 0.19f
                smoothed[i] += (desired - smoothed[i]) * k
            }
            val vl = if (isPlaying) raw.left else 0f
            val vr = if (isPlaying) raw.right else 0f
            left += (vl-left) * if (vl>left) 0.72f else 0.20f
            right += (vr-right) * if (vr>right) 0.72f else 0.20f
            visible = SpectrumFrame(smoothed.copyOf(), left, right, raw.sequence)
            delay(50L)
        }
    }
    Column(
        Modifier.fillMaxWidth()
            .border(1.dp, AmberDark, RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(NearBlack, Obsidian)), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("SPECTRUM  ·  PCM", color = Amber,
                fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(6.dp).background(if (isPlaying) Green else Color.DarkGray,
                RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(5.dp))
            Text(if (isPlaying) "LIVE" else "IDLE", color = if (isPlaying) Green else AmberDark,
                fontSize = 9.sp, letterSpacing = 1.4.sp)
        }
        Canvas(Modifier.fillMaxWidth().height(105.dp)) {
            val bars = visible.bands
            val gap = 5.dp.toPx()
            val w = (size.width-gap*(bars.size-1))/bars.size
            val cells = 15
            val cellGap = 2.dp.toPx()
            val cellH = (size.height-cellGap*(cells-1))/cells
            for (i in bars.indices) {
                val activeCells = (bars[i]*cells+0.5f).toInt().coerceIn(0,cells)
                for (level in 0 until cells) {
                    val lit = level < activeCells
                    val tint = when {
                        level > 11 -> Red
                        level > 7 -> Amber
                        else -> Green
                    }
                    val y = size.height-(level+1)*(cellH+cellGap)+cellGap
                    drawRoundRect(
                        color = tint.copy(alpha = if (lit) 0.97f else 0.075f),
                        topLeft = Offset(i*(w+gap), y),
                        size = Size(w.coerceAtLeast(2f), cellH.coerceAtLeast(2f)),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("40", color=AmberDark,fontSize=9.sp)
            Text("125",color=AmberDark,fontSize=9.sp)
            Text("500",color=AmberDark,fontSize=9.sp)
            Text("2k",color=AmberDark,fontSize=9.sp)
            Text("8k+ Hz",color=AmberDark,fontSize=9.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            AnalogVuMeter("LEFT", visible.left, Modifier.weight(1f))
            AnalogVuMeter("RIGHT", visible.right, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AnalogVuMeter(label: String, value: Float, modifier: Modifier = Modifier) {
    Column(modifier.border(1.dp, AmberDark.copy(alpha=0.5f), RoundedCornerShape(10.dp))
        .background(Color(0xFF1C170D),RoundedCornerShape(10.dp))
        .padding(horizontal=8.dp,vertical=5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Amber, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp)
        Canvas(Modifier.fillMaxWidth().height(63.dp)) {
            val center = Offset(size.width/2f, size.height*0.93f)
            val radius = min(size.width*0.44f, size.height*0.86f)
            val start=207f
            val sweep=126f
            for(i in 0..16) {
                val a=(start+sweep*i/16.0)*PI/180.0
                val first = Offset(center.x+cos(a).toFloat()*radius*0.83f,
                    center.y+sin(a).toFloat()*radius*0.83f)
                val second = Offset(center.x+cos(a).toFloat()*radius,
                    center.y+sin(a).toFloat()*radius)
                drawLine(if(i>13) Red else Amber,first,second,strokeWidth=1.5.dp.toPx())
            }
            val db = 20f*(ln(value.coerceAtLeast(0.0001f))/ln(10f))
            val normalized = ((db+33f)/33f).coerceIn(0f,1f)
            val a=(start+sweep*normalized)*PI/180.0
            val needleEnd=Offset(center.x+cos(a).toFloat()*radius*0.9f,
                center.y+sin(a).toFloat()*radius*0.9f)
            drawLine(Red,center,needleEnd,strokeWidth=2.1.dp.toPx(),cap=StrokeCap.Round)
            drawCircle(Amber,3.3.dp.toPx(),center)
        }
        Text("VU  ·  SIGNAL", color = Ivory.copy(alpha=0.8f), fontSize = 8.sp,
            letterSpacing=0.5.sp)
    }
}

/** Knobs substituem os faders no painel de mixagem; deslize para cima/baixo. */
@Composable
fun RetroKnobBoard(s: ConsoleSettings, update: (ConsoleSettings)->Unit, enabled: Boolean) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            RetroKnob("GRAVES",s.warmth,{update(s.copy(warmth=it))},enabled,Modifier.weight(1f))
            RetroKnob("PRESENÇA",s.presence,{update(s.copy(presence=it))},enabled,Modifier.weight(1f))
            RetroKnob("AGUDOS",s.air,{update(s.copy(air=it))},enabled,Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            RetroKnob("COMPRESSÃO",s.bassCompression,{update(s.copy(bassCompression=it))},enabled,Modifier.weight(1f))
            RetroKnob("SATURAÇÃO",s.drive,{update(s.copy(drive=it))},enabled,Modifier.weight(1f))
            RetroKnob("MISTURA",s.mix,{update(s.copy(mix=it))},enabled,Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            RetroKnob("SAÍDA",((s.outputDb+18f)/24f).coerceIn(0f,1f),
                { update(s.copy(outputDb=-18f+it*24f)) },enabled,Modifier.weight(1f),
                valueText="${"%+.1f".format(java.util.Locale.US,s.outputDb)} dB")
            Column(Modifier.weight(2f).padding(start=12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text("ANALOG COLOR ENGINE", color=Amber,fontSize=10.sp,fontWeight=FontWeight.Bold)
                Text("Gire os controles deslizando o dedo para cima ou para baixo.",
                    color=Ivory.copy(alpha=0.73f),fontSize=11.sp)
                Text("Ajustes em tempo real · Motor DSP Rust",color=Green,fontSize=10.sp)
            }
        }
    }
}

@Composable
private fun RetroKnob(
    label: String, value:Float, onValue:(Float)->Unit, enabled:Boolean,
    modifier:Modifier = Modifier, valueText:String="${(value*100).toInt()}%"
) {
    val valueNow by rememberUpdatedState(value)
    val action by rememberUpdatedState(onValue)
    Column(modifier, horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text(label, color=Amber,fontSize=9.sp,fontWeight=FontWeight.Bold,
            maxLines=1,softWrap=false)
        Canvas(Modifier.size(74.dp).pointerInput(enabled) {
            if (enabled) detectVerticalDragGestures { change, amount ->
                action((valueNow-amount/240f).coerceIn(0f,1f))
                change.consume()
            }
        }) {
            val c=Offset(size.width/2f,size.height/2f)
            val r=size.minDimension*0.36f
            drawCircle(Brush.radialGradient(listOf(Color(0xFF43494A),Color(0xFF131717),Color(0xFF080A0B))),r,c)
            drawCircle(AmberDark,r,c,style=Stroke(2.dp.toPx()))
            for (i in 0..16) {
                val angle=(135.0 + 270.0*i/16.0)*PI/180.0
                val inRad=r*1.2f
                val outRad=r*1.43f
                val selected=i<=value*16
                val tint=when {
                    i>13 -> Red
                    i>8 -> Amber
                    else -> Green
                }
                drawLine(if (selected) tint else AmberDark.copy(alpha=0.4f),
                    Offset(c.x+cos(angle).toFloat()*inRad,c.y+sin(angle).toFloat()*inRad),
                    Offset(c.x+cos(angle).toFloat()*outRad,c.y+sin(angle).toFloat()*outRad),
                    strokeWidth=1.35.dp.toPx(),cap=StrokeCap.Round)
            }
            val angle=(135.0+value.coerceIn(0f,1f)*270.0)*PI/180.0
            drawLine(Amber,c,Offset(c.x+cos(angle).toFloat()*r*0.8f,
                c.y+sin(angle).toFloat()*r*0.8f),strokeWidth=3.dp.toPx(),cap=StrokeCap.Round)
            drawCircle(Color(0xFF171D1D),r*0.11f,c)
        }
        Text(valueText, color=if(enabled)Green else Ivory.copy(alpha=0.5f),
            fontSize=11.sp,fontWeight=FontWeight.Medium,textAlign=TextAlign.Center)
    }
}
