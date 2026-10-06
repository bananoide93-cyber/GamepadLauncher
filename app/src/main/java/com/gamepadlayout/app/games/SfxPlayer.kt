package com.gamepadlayout.app.games

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread

/**
 * Toca os efeitos e a musica de um jogo com audio ([MiniGame.usesAudio]).
 *
 * Usa UM unico AudioTrack em fluxo e uma thread que mistura tudo (efeitos + musica em loop): poucos recursos nativos.
 * Os sons sao sintetizados em Kotlin ([Synth]). Para trocar um por um arquivo seu, coloque um WAV de 16 bits em
 * app/src/main/assets/audio/ com o nome "sfx_<nome>.wav" ou "music_<nome>.wav" (nomes em [Sfx.NAMES] e [Mus.NAMES]).
 */
class SfxPlayer(private val ctx: Context) {
    @Volatile var duck = false
    @Volatile private var released = false

    private val rate = Synth.RATE
    private val sfxClips = arrayOfNulls<ShortArray>(Sfx.COUNT)
    @Volatile private var sfxReady = false

    private class Voice(val d: ShortArray, var pos: Int, val vol: Float)
    private val voices = ArrayList<Voice>()
    private val lock = Any()

    private val musicClips = HashMap<Int, ShortArray>()
    private val musicLoading = HashSet<Int>()
    @Volatile private var musicWanted = -1
    @Volatile private var musicVol = 0f
    @Volatile private var sfxVol = 1f

    private var track: AudioTrack? = null

    init {
        thread(name = "sfx-build", isDaemon = true) {
            try {
                for (i in 0 until Sfx.COUNT) {
                    if (released) return@thread
                    try { sfxClips[i] = loadWav("audio/sfx_${Sfx.NAMES[i]}.wav") ?: Synth.sfx(i) } catch (e: Throwable) { }
                }
            } catch (e: Throwable) { }
            sfxReady = true
        }
        thread(name = "sfx-mix", isDaemon = true) { mixLoop() }
    }

    private fun loadWav(path: String): ShortArray? = try {
        ctx.assets.open(path).use { parseWav(it.readBytes()) }
    } catch (e: Throwable) { null }

    /** Converte para mono 22050 Hz. */
    private fun parseWav(b: ByteArray): ShortArray? {
        if (b.size < 44 || String(b, 0, 4) != "RIFF") return null
        fun u16(o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
        fun u32(o: Int) = u16(o) or (u16(o + 2) shl 16)
        var pos = 12; var sr = 22050; var ch = 1; var bits = 16
        while (pos + 8 <= b.size) {
            val id = String(b, pos, 4); val size = u32(pos + 4)
            if (id == "fmt ") { ch = u16(pos + 10); sr = u32(pos + 12); bits = u16(pos + 22) }
            else if (id == "data") {
                if (bits != 16 || ch !in 1..2 || sr <= 0) return null
                val frames = minOf(size, b.size - pos - 8) / (2 * ch)
                val outN = (frames.toLong() * rate / sr).toInt()
                val out = ShortArray(outN)
                for (i in 0 until outN) {
                    val src = (i.toLong() * sr / rate).toInt().coerceIn(0, frames - 1)
                    var v = 0
                    for (c in 0 until ch) v += u16(pos + 8 + (src * ch + c) * 2).toShort().toInt()
                    out[i] = (v / ch).toShort()
                }
                return out
            }
            pos += 8 + size + (size and 1)
        }
        return null
    }

    private fun mixLoop() {
        try {
            val minB = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minB <= 0) return
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(minB * 2, 4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track = t
            if (released) { t.release(); return }
            t.play()
            val n = 1024
            val acc = FloatArray(n)
            val out = ShortArray(n)
            var mId = -1; var mPos = 0; var mGain = 0f
            while (!released) {
                java.util.Arrays.fill(acc, 0f)
                val sv = if (duck) 0.3f else 1f
                synchronized(lock) {
                    val it = voices.iterator()
                    while (it.hasNext()) {
                        val v = it.next()
                        val cnt = minOf(n, v.d.size - v.pos)
                        for (i in 0 until cnt) acc[i] += v.d[v.pos + i] * v.vol
                        v.pos += cnt
                        if (v.pos >= v.d.size) it.remove()
                    }
                }
                val want = musicWanted
                if (want != mId) { mGain -= 0.08f; if (mGain <= 0f) { mGain = 0f; mId = want; mPos = 0 } }
                else if (mGain < 1f) mGain = minOf(1f, mGain + 0.04f)
                val clip = if (mId >= 0) synchronized(lock) { musicClips[mId] } else null
                if (clip != null && clip.isNotEmpty() && mGain > 0f) {
                    val g = mGain * musicVol * (if (duck) 0.4f else 1f) * 0.55f
                    for (i in 0 until n) { acc[i] += clip[mPos] * g; mPos++; if (mPos >= clip.size) mPos = 0 }
                }
                val sg = sfxVol * sv
                for (i in 0 until n) {
                    val x = acc[i] * (if (sg < 0.999f) 1f else 1f)
                    out[i] = x.coerceIn(-32000f, 32000f).toInt().toShort()
                }
                t.write(out, 0, n)
            }
            runCatching { t.stop() }
            runCatching { t.release() }
        } catch (e: Throwable) { /* som e opcional: nunca derruba o jogo */ }
    }

    private fun startMusic(id: Int) {
        synchronized(lock) { if (musicClips.containsKey(id) || !musicLoading.add(id)) return }
        thread(name = "music-build", isDaemon = true) {
            try {
                val clip = loadWav("audio/music_${Mus.NAMES.getOrElse(id) { "x" }}.wav") ?: Synth.music(id)
                synchronized(lock) {
                    if (musicClips.size >= 3) {
                        val drop = musicClips.keys.firstOrNull { it != musicWanted }
                        if (drop != null) musicClips.remove(drop)
                    }
                    musicClips[id] = clip
                }
            } catch (e: Throwable) { }
            synchronized(lock) { musicLoading.remove(id) }
        }
    }

    /** Chamado uma vez por quadro: toca os efeitos pendentes e troca a musica quando o jogo pede. */
    fun sync(game: MiniGame) {
        if (released) return
        sfxVol = game.sfxVolume.coerceIn(0f, 1f)
        musicVol = game.musicVolume.coerceIn(0f, 1f)
        while (true) {
            val id = game.sfxQueue.poll() ?: break
            if (!duck && sfxReady) {
                val d = sfxClips.getOrNull(id)
                if (d != null) synchronized(lock) { if (voices.size < 12) voices.add(Voice(d, 0, sfxVol)) }
            }
        }
        val want = game.musicId
        musicWanted = want
        if (want >= 0) startMusic(want)
    }

    fun release() {
        released = true
    }
}
