package com.gamepadlayout.app.cast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.gamepadlayout.app.MainActivity
import com.gamepadlayout.app.data.CastQuality
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Transmissão da tela com a API oficial MediaProjection (o Android pede a permissão ao usuário).
 * Serviço em primeiro plano do tipo "mediaProjection" (exigido desde o Android 10/14).
 * Só codifica quadros quando há alguém assistindo, para economizar CPU e bateria.
 */
class ScreenCastService : Service() {

    companion object {
        private const val CHANNEL = "cast"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        private const val EXTRA_W = "w"
        private const val EXTRA_H = "h"
        private const val EXTRA_DPI = "dpi"
        private const val EXTRA_Q = "q"
        private const val EXTRA_FPS = "fps"

        fun start(ctx: Context, code: Int, data: Intent, w: Int, h: Int, dpi: Int, q: CastQuality, fps: Int) {
            val i = Intent(ctx, ScreenCastService::class.java)
                .putExtra(EXTRA_CODE, code)
                .putExtra(EXTRA_DATA, data)
                .putExtra(EXTRA_W, w)
                .putExtra(EXTRA_H, h)
                .putExtra(EXTRA_DPI, dpi)
                .putExtra(EXTRA_Q, q.name)
                .putExtra(EXTRA_FPS, fps)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, ScreenCastService::class.java))
        }
    }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var server: MjpegServer? = null
    private var callback: MediaProjection.Callback? = null
    private var stopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) { stopSelf(); return START_NOT_STICKY }
        showNotification()

        val code = intent.getIntExtra(EXTRA_CODE, 0)
        @Suppress("DEPRECATION")
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        else intent.getParcelableExtra(EXTRA_DATA)
        if (data == null) { fail("Permissão de captura ausente"); return START_NOT_STICKY }

        val q = CastQuality.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_Q) } ?: CastQuality.Q720
        val fps = intent.getIntExtra(EXTRA_FPS, 24)
        val sw = intent.getIntExtra(EXTRA_W, 1280)
        val sh = intent.getIntExtra(EXTRA_H, 720)
        val dpi = intent.getIntExtra(EXTRA_DPI, 320)
        try {
            begin(code, data, q, fps, sw, sh, dpi)
        } catch (e: Exception) {
            fail("Não foi possível iniciar a captura: ${e.javaClass.simpleName}")
        }
        return START_NOT_STICKY
    }

    private fun showNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Transmissão de tela", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("Gamepad Layout")
            .setContentText("Transmitindo a tela pela rede Wi-Fi")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    private fun begin(code: Int, data: Intent, q: CastQuality, fps: Int, sw: Int, sh: Int, dpi: Int) {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val proj = mpm.getMediaProjection(code, data) ?: throw IllegalStateException("projection null")
        projection = proj

        val t = HandlerThread("gamepad-cast").also { it.start() }
        thread = t
        val handler = Handler(t.looper)

        // Obrigatório no Android 14+: registrar o callback antes de criar o VirtualDisplay.
        val cb = object : MediaProjection.Callback() {
            override fun onStop() { if (!stopping) stopSelf() }
        }
        callback = cb
        proj.registerCallback(cb, handler)

        val scale = min(1f, q.maxSide / max(sw, sh).toFloat())
        val w = max(16, ((sw * scale).toInt() / 16) * 16)
        val h = max(16, ((sh * scale).toInt() / 16) * 16)

        // Servidor: tenta portas 8080..8085
        val key = (1000..9999).random().toString()
        var port = 8080
        var srv: MjpegServer? = null
        while (port <= 8085 && srv == null) {
            val candidate = MjpegServer(port, key) { n ->
                CastState.status.value = CastState.status.value.copy(clients = n)
            }
            if (candidate.start()) srv = candidate else port++
        }
        val httpServer: MjpegServer = srv ?: throw IllegalStateException("porta indisponível")
        server = httpServer

        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader = r
        val minInterval = 1000L / max(1, fps)
        var last = 0L
        var counter = 0
        var counterStart = SystemClock.elapsedRealtime()
        var reusable: Bitmap? = null
        val baos = ByteArrayOutputStream(64 * 1024)

        var queued = false
        fun process() {
            val img = r.acquireLatestImage() ?: return
            try {
                val now = SystemClock.elapsedRealtime()
                if (httpServer.clientCount == 0) return
                last = now
                val plane = img.planes[0]
                val bw = w + (plane.rowStride - plane.pixelStride * w) / plane.pixelStride
                val bmp = reusable?.takeIf { it.width == bw } ?: Bitmap.createBitmap(bw, h, Bitmap.Config.ARGB_8888).also { reusable = it }
                plane.buffer.rewind()
                bmp.copyPixelsFromBuffer(plane.buffer)
                val src = if (bw == w) bmp else Bitmap.createBitmap(bmp, 0, 0, w, h)
                baos.reset()
                src.compress(Bitmap.CompressFormat.JPEG, q.jpeg, baos)
                if (src !== bmp) src.recycle()
                httpServer.publish(baos.toByteArray())
                counter++
                if (now - counterStart >= 1000) {
                    CastState.status.value = CastState.status.value.copy(realFps = counter)
                    counter = 0
                    counterStart = now
                }
            } finally {
                img.close()
            }
        }

        // Quadro que chega antes do intervalo é ADIADO (não descartado): evita a imagem "congelada"
        // em cenas paradas, que dava a sensação de atraso.
        r.setOnImageAvailableListener({
            val now = SystemClock.elapsedRealtime()
            val wait = minInterval - (now - last)
            if (wait <= 0L) process()
            else if (!queued) {
                queued = true
                handler.postDelayed({ queued = false; process() }, wait)
            }
        }, handler)

        display = proj.createVirtualDisplay(
            "GamepadCast", w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface, null, handler
        )

        val ip = NetworkInfo.localIp(this) ?: "IP-DO-CELULAR"
        CastState.status.value = CastStatus(
            running = true,
            url = "http://$ip:$port/$key",
            width = w,
            height = h,
            targetFps = fps,
            startedAt = System.currentTimeMillis()
        )
    }

    private fun fail(msg: String) {
        CastState.status.value = CastStatus(error = msg)
        stopSelf()
    }

    override fun onDestroy() {
        stopping = true
        runCatching { display?.release() }
        runCatching { reader?.close() }
        runCatching { server?.stop() }
        runCatching {
            callback?.let { projection?.unregisterCallback(it) }
            projection?.stop()
        }
        runCatching { thread?.quitSafely() }
        val err = CastState.status.value.error
        CastState.status.value = CastStatus(error = err)
        super.onDestroy()
    }
}
