package com.example.ap.chat

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File
import kotlin.concurrent.thread

/**
 * 离线语音转文字：sherpa-onnx + zipformer 中文流式模型，打包在 App 里。
 * 不联网、不要 API Key，任何手机都能用。
 * 首次点麦克风时把模型从 assets 拷到私有目录并加载（约一两秒），
 * 之后常驻内存，再按秒开。
 */
class SherpaVoiceInput(
    private val context: Context,
    private val onText: (String) -> Unit,
    private val onMessage: (String) -> Unit,
    private val onState: (Boolean) -> Unit,
) {
    private var record: AudioRecord? = null
    private var stream: OnlineStream? = null
    private var running = false
    private var starting = false
    private var cancelRequested = false
    private var hardRelease = false
    private var spoke = false
    /** 第一次启动要拷贝+加载模型，外面用它给用户一句提示。 */
    @Volatile var firstStart = true

    /** 开始听。返回 false 表示正忙（启动中或录音中）。 */
    fun start(): Boolean {
        if (busy()) return false
        starting = true
        cancelRequested = false
        hardRelease = false
        spoke = false
        thread(name = "sherpa-asr") {
            var rec: OnlineRecognizer? = null
            var st: OnlineStream? = null
            var mic: AudioRecord? = null
            try {
                rec = ensureRecognizer()
                if (cancelRequested) throw InterruptedException()
                st = rec.createStream()
                mic = createRecord()
                mic.startRecording()
                synchronized(this) {
                    stream = st
                    record = mic
                    running = true
                    starting = false
                }
                onState(true)
                readLoop(rec, st, mic)
            } catch (e: InterruptedException) {
                // 启动中途被取消，静默收摊
            } catch (e: Exception) {
                onMessage("语音引擎启动失败：${e.message ?: "未知错误"}")
            } finally {
                val finalize = !hardRelease
                try { mic?.stop() } catch (_: Exception) {}
                try { mic?.release() } catch (_: Exception) {}
                if (finalize && st != null && rec != null && spoke) {
                    try { st.inputFinished() } catch (_: Exception) {}
                    try {
                        while (rec.isReady(st)) rec.decode(st)
                        val text = rec.getResult(st).text
                        if (text.isNotBlank()) onText(text)
                    } catch (_: Exception) {}
                }
                synchronized(this) {
                    running = false
                    starting = false
                    stream = null
                    record = null
                }
                onState(false)
            }
        }
        return true
    }

    private fun readLoop(rec: OnlineRecognizer, st: OnlineStream, mic: AudioRecord) {
        val chunk = ShortArray(SAMPLE_RATE / 10) // 100ms
        val floats = FloatArray(chunk.size)
        while (true) {
            if (cancelRequested) break
            val n = mic.read(chunk, 0, chunk.size)
            if (n <= 0) break
            for (i in 0 until n) floats[i] = chunk[i] / 32768f
            st.acceptWaveform(floats.copyOf(n), SAMPLE_RATE)
            while (rec.isReady(st)) rec.decode(st)
            val text = rec.getResult(st).text
            if (text.isNotBlank()) {
                spoke = true
                onText(text)
            }
            if (rec.isEndpoint(st)) break // 说完停顿即自动收尾
        }
    }

    /** 用户主动停止：把听到的话收尾送出去。 */
    fun stop() {
        if (!running) return
        synchronized(this) { cancelRequested = true }
    }

    /** 立刻停止并释放麦克风，不收尾（切后台/离开页面用）。模型常驻，下次秒开。 */
    fun release() {
        synchronized(this) {
            cancelRequested = true
            hardRelease = true
        }
    }

    /** 是否正在启动或录音中（外面防重复点用）。 */
    fun busy(): Boolean = synchronized(this) { running || starting }

    private fun ensureRecognizer(): OnlineRecognizer {
        companionRecognizer?.let { return it }
        synchronized(this) {
            companionRecognizer?.let { return it }
            val dir = File(context.filesDir, "asr-model")
            if (!modelReady(dir)) {
                dir.mkdirs()
                for (name in MODEL_FILES) {
                    context.assets.open("asr-model/$name").use { input ->
                        File(dir, name).outputStream().use { input.copyTo(it) }
                    }
                }
            }
            val rec = OnlineRecognizer(
                assetManager = null,
                config = configFor(dir),
            )
            companionRecognizer = rec
            return rec
        }
    }

    private fun modelReady(dir: File): Boolean =
        MODEL_FILES.all { name -> File(dir, name).let { it.exists() && it.length() > 0L } }

    private fun configFor(dir: File) = OnlineRecognizerConfig(
        featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
        modelConfig = OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = File(dir, "encoder-epoch-99-avg-1.int8.onnx").absolutePath,
                decoder = File(dir, "decoder-epoch-99-avg-1.int8.onnx").absolutePath,
                joiner = File(dir, "joiner-epoch-99-avg-1.int8.onnx").absolutePath,
            ),
            tokens = File(dir, "tokens.txt").absolutePath,
            numThreads = 2,
            debug = false,
            provider = "cpu",
        ),
        enableEndpoint = true,
        endpointConfig = EndpointConfig(
            rule1 = EndpointRule(mustContainNonSilence = true, minTrailingSilence = 1.2f, minUtteranceLength = 0f),
            rule2 = EndpointRule(mustContainNonSilence = true, minTrailingSilence = 1.8f, minUtteranceLength = 8f),
            rule3 = EndpointRule(mustContainNonSilence = false, minTrailingSilence = 0f, minUtteranceLength = 25f),
        ),
    )

    private fun createRecord(): AudioRecord {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = maxOf(minBuf, SAMPLE_RATE / 10 * 2 * 4)
        val sources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT,
        )
        var lastError: Exception? = null
        for (src in sources) {
            try {
                val r = AudioRecord(src, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
                if (r.state == AudioRecord.STATE_INITIALIZED) return r
                try { r.release() } catch (_: Exception) {}
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("麦克风初始化失败")
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private val MODEL_FILES = listOf(
            "tokens.txt",
            "encoder-epoch-99-avg-1.int8.onnx",
            "decoder-epoch-99-avg-1.int8.onnx",
            "joiner-epoch-99-avg-1.int8.onnx",
        )
        @Volatile
        private var companionRecognizer: OnlineRecognizer? = null
    }
}
