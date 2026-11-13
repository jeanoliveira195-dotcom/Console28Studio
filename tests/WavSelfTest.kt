import com.jean.console28.audio.WavWriter
import java.io.File

fun main() {
    val rate=48_000
    val file=File.createTempFile("console28-wav", ".wav")
    try {
        WavWriter(file,rate,1).use { it.append(ShortArray(rate){ 1234 },rate) }
        val bytes=file.readBytes()
        check(String(bytes.copyOfRange(0,4))=="RIFF")
        check(String(bytes.copyOfRange(8,12))=="WAVE")
        check(String(bytes.copyOfRange(36,40))=="data")
        check(bytes.size==44+rate*2)
        val dataSize=(0..3).sumOf { (bytes[40+it].toInt() and 255) shl (8*it) }
        check(dataSize==rate*2)
        println("PASS cabeçalho RIFF/WAV e tamanho de dados")
    } finally {file.delete()}
}
