package com.example.ap.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * 语音转文字：用安卓自带的语音识别（SpeechRecognizer），不加库。
 * - start()：开始听。说的话实时通过 onText 给出来（整句替换，不是追加）。
 * - stop()：不再听，等系统把最后结果送回来（4 秒没动静就强制收尾）。
 * - 出错或不支持时通过 onMessage 给一句人话提示。
 * 麦克风权限由外面先要好再调 start()。识别语言跟着手机系统语言走。
 */
class VoiceInput(
    private val context: Context,
    private val onText: (String) -> Unit,
    private val onMessage: (String) -> Unit,
) {
    /** 是不是正在听（界面用它显示"正在听"的按钮）。 */
    var listening by mutableStateOf(false)
        private set

    private var recognizer: SpeechRecognizer? = null
    private var sherpa: SherpaVoiceInput? = null
    private var gotText = false
    private val handler = Handler(Looper.getMainLooper())
    private val forceEnd = Runnable { release() }
    private var ready = false
    private var useSherpaOnly = false
    /** 系统识别服务 3 秒内没进入"准备好"状态 = 服务是坏的/没反应，换离线引擎。 */
    private val noResponse = Runnable {
        if (recognizer != null && !ready && !gotText) fallbackToSherpa()
    }

    private fun fallbackToSherpa() {
        release()
        useSherpaOnly = true   // 本次运行后面都直接用离线引擎
        startSherpa()
    }

    fun start() {
        if (listening) return
        if (useSherpaOnly || !SpeechRecognizer.isRecognitionAvailable(context)) {
            // 这台手机没有系统语音服务 → 用 App 自带的离线引擎
            startSherpa()
            return
        }
        val r = try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            null
        }
        if (r == null) {
            // 系统识别器创建失败 → 同样退到离线引擎
            fallbackToSherpa()
            return
        }
        gotText = false
        ready = false
        r.setRecognitionListener(listenerFor(r))
        recognizer = r
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        listening = true
        try {
            r.startListening(intent)
            handler.postDelayed(noResponse, 3000)
        } catch (e: Exception) {
            fallbackToSherpa()
        }
    }

    /** 主动结束：让系统把已经听到的送回来。 */
    fun stop() {
        sherpa?.let { if (it.busy()) { it.stop(); return } }
        val r = recognizer ?: return
        try {
            r.stopListening()
        } catch (e: Exception) {
            release()
            return
        }
        handler.removeCallbacks(forceEnd)
        handler.postDelayed(forceEnd, 4000)
    }

    /** 立刻收尾并释放麦克风（切到后台、退出页面时用）。已经识别出来的字保留在输入框。 */
    fun release() {
        handler.removeCallbacks(forceEnd)
        handler.removeCallbacks(noResponse)
        sherpa?.release()
        val r = recognizer
        recognizer = null
        listening = false
        if (r != null) handler.post { try { r.destroy() } catch (e: Exception) { } }
    }

    /** 离线兜底：系统没有语音服务时，用打包在 App 里的 sherpa-onnx 引擎。 */
    private fun startSherpa() {
        val s = sherpa ?: SherpaVoiceInput(
            context,
            onText,
            onMessage,
            onState = { active -> handler.post { listening = active } },
        ).also { sherpa = it }
        if (!s.start()) return   // 正忙就不重复启动
        // 首次启动模型要拷贝+加载一两秒，先给用户一个"正在启动"的反馈
        if (s.firstStart) {
            s.firstStart = false
            onMessage("正在启动离线语音引擎，下次就快了")
        }
    }

    private fun listenerFor(r: SpeechRecognizer) = object : RecognitionListener {
        private fun alive() = recognizer === r

        private fun firstText(b: Bundle?): String? =
            b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!alive()) return
            firstText(partialResults)?.let { gotText = true; onText(it) }
        }

        override fun onResults(results: Bundle?) {
            if (!alive()) return
            firstText(results)?.let { gotText = true; onText(it) }
            release()
        }

        override fun onError(error: Int) {
            if (!alive()) return
            val hadText = gotText
            // 还没真正开始听就报错（国产 ROM 常见：服务缺失/断开/不支持中文）→ 直接换离线引擎
            if (!ready && !hadText && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                fallbackToSherpa()
                return
            }
            release()
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> if (!hadText) onMessage("没听清，再说一次")
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> onMessage("语音识别需要联网")
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> onMessage("需要允许麦克风才能语音输入")
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> onMessage("语音识别正忙，稍后再试")
                SpeechRecognizer.ERROR_CLIENT -> Unit
                else -> onMessage("语音输入出错了（$error）")
            }
        }

        override fun onReadyForSpeech(params: Bundle?) {
            if (alive()) { ready = true; handler.removeCallbacks(noResponse) }
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}

/** 在界面里拿一个语音输入；退出页面或切到后台时自动收尾，不会一直占着麦克风。 */
@Composable
fun rememberVoiceInput(onText: (String) -> Unit, onMessage: (String) -> Unit): VoiceInput {
    val context = LocalContext.current
    val latestText by rememberUpdatedState(onText)
    val latestMessage by rememberUpdatedState(onMessage)
    val voice = remember { VoiceInput(context, { latestText(it) }, { latestMessage(it) }) }
    DisposableEffect(voice) {
        val owner = context as? ComponentActivity
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) voice.release()
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose {
            owner?.lifecycle?.removeObserver(observer)
            voice.release()
        }
    }
    return voice
}
