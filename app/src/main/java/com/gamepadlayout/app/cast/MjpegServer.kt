package com.gamepadlayout.app.cast

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.withLock
import java.util.concurrent.locks.ReentrantLock

/**
 * Servidor HTTP mínimo que entrega a tela como MJPEG.
 * Qualquer navegador da mesma rede Wi-Fi (TV, PC, outro celular) abre http://IP:porta/CHAVE.
 * A chave aleatória na URL evita que outros aparelhos da rede abram a transmissão por acaso.
 */
class MjpegServer(
    private val port: Int,
    private val key: String,
    private val onClients: (Int) -> Unit
) {
    @Volatile private var running = false
    private var server: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()
    private val lock = ReentrantLock()
    private val cond = lock.newCondition()
    private var frame: ByteArray? = null
    private var seq = 0L
    private val clients = AtomicInteger(0)

    val clientCount: Int get() = clients.get()

    fun start(): Boolean = try {
        server = ServerSocket(port)
        running = true
        pool.execute { acceptLoop() }
        true
    } catch (e: Exception) {
        false
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        lock.withLock { cond.signalAll() }
        pool.shutdownNow()
    }

    fun publish(jpeg: ByteArray) {
        lock.withLock {
            frame = jpeg
            seq++
            cond.signalAll()
        }
    }

    private fun acceptLoop() {
        while (running) {
            try {
                val s = server?.accept() ?: break
                pool.execute { handle(s) }
            } catch (e: Exception) {
                if (!running) break
            }
        }
    }

    private fun handle(s: Socket) {
        try {
            s.tcpNoDelay = true
            // Buffer de envio pequeno: se a rede atrasar, o atraso não se acumula (sempre vai o quadro mais novo).
            runCatching { s.sendBufferSize = 32 * 1024 }
            s.soTimeout = 8000
            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
            val line = reader.readLine() ?: return
            while (true) {
                val h = reader.readLine() ?: break
                if (h.isEmpty()) break
            }
            s.soTimeout = 0
            val path = line.split(" ").getOrNull(1) ?: "/"
            val out = s.getOutputStream()
            when {
                path == "/$key" || path == "/$key/" -> sendPage(out)
                path.startsWith("/$key/s") -> stream(out)
                else -> sendText(out, "404 Not Found", "Não encontrado")
            }
        } catch (e: Exception) {
        } finally {
            runCatching { s.close() }
        }
    }

    private fun sendText(out: OutputStream, status: String, body: String) {
        val b = body.toByteArray()
        out.write(("HTTP/1.1 $status\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${b.size}\r\nConnection: close\r\n\r\n").toByteArray())
        out.write(b)
        out.flush()
    }

    private fun sendPage(out: OutputStream) {
        val html = """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Gamepad Layout</title><style>html,body{margin:0;height:100%;background:#000}img{width:100vw;height:100vh;object-fit:contain;display:block}</style></head>
<body><img src="/$key/s"></body></html>"""
        val b = html.toByteArray()
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${b.size}\r\nConnection: close\r\n\r\n").toByteArray())
        out.write(b)
        out.flush()
    }

    private fun stream(out: OutputStream) {
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=frame\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n").toByteArray())
        out.flush()
        onClients(clients.incrementAndGet())
        try {
            var last = -1L
            while (running) {
                var data: ByteArray? = null
                lock.withLock {
                    if (seq == last) cond.await(1, TimeUnit.SECONDS)
                    if (seq != last) {
                        last = seq
                        data = frame
                    }
                }
                val d = data ?: continue
                out.write(("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${d.size}\r\n\r\n").toByteArray())
                out.write(d)
                out.write("\r\n".toByteArray())
                out.flush()
            }
        } finally {
            onClients(clients.decrementAndGet())
        }
    }
}
