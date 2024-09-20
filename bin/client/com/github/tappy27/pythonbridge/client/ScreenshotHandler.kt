// ScreenshotHandler.kt
package com.github.tappy27.pythonbridge.client

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import javax.imageio.ImageIO
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.minecraft.client.MinecraftClient
import org.apache.logging.log4j.LogManager
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11

object ScreenshotHandler {

    private val logger = LogManager.getLogger(ScreenshotHandler::class.java)

    // I/O 用の CoroutineScope
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ソケット接続管理
    @Volatile
    private var socket: Socket? = null

    @Volatile
    private var dataOutputStream: DataOutputStream? = null

    // 書き込みの同期化用 Mutex
    private val writeMutex = Mutex()

    // Python サーバーへの接続を確立
    suspend fun connectToPythonServer(host: String = "localhost", port: Int = 5000) {
        while (socket == null || socket?.isClosed == true) {
            try {
                socket = Socket()
                socket?.connect(InetSocketAddress(host, port), 5000) // 5秒のタイムアウト
                dataOutputStream = DataOutputStream(socket!!.getOutputStream())
                logger.info("Connected to Python server at $host:$port")
            } catch (e: IOException) {
                logger.warn("Connection failed: ${e.message}. Retrying in 2 seconds...")
                delay(2000)
            }
        }
    }

    // ソケット接続を閉じる
    fun closeConnection() {
        try {
            dataOutputStream?.close()
            socket?.close()
            logger.info("Connection to Python server closed.")
        } catch (e: IOException) {
            logger.error("Error while closing connection: ${e.message}", e)
        }
    }

    // アプリケーション終了時に接続を閉じる
    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            closeConnection()
        })
    }

    // スクリーンショットをキャプチャ（メインスレッドで呼び出す）
// ScreenshotHandler.kt
    fun captureScreenshot(): BufferedImage? {
        val client = MinecraftClient.getInstance()
        val width = client.window.framebufferWidth
        val height = client.window.framebufferHeight

        logger.debug("Capturing screenshot with dimensions: ${width}x${height}")

        return try {
            // 描画の完了を待つ
            GL11.glFinish()
            logger.debug("glFinish called")

            // ピクセルの行揃えを1に設定
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1)
            logger.debug("glPixelStorei set to 1")

            // バックバッファからピクセルデータを読み取る
            GL11.glReadBuffer(GL11.GL_BACK)
            val errorAfterReadBuffer = GL11.glGetError()
            if (errorAfterReadBuffer != GL11.GL_NO_ERROR) {
                logger.error("OpenGL Error after glReadBuffer(GL_BACK): $errorAfterReadBuffer")
            } else {
                logger.debug("glReadBuffer(GL_BACK) called")
            }

            val buffer = BufferUtils.createByteBuffer(width * height * 4)
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer)
            logger.debug("glReadPixels called")

            // OpenGL エラーチェック
            val errorAfterReadPixels = GL11.glGetError()
            if (errorAfterReadPixels != GL11.GL_NO_ERROR) {
                logger.error("OpenGL Error after glReadPixels: $errorAfterReadPixels")
            } else {
                logger.debug("glReadPixels completed without errors")
            }

            // BufferedImage に変換
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val i = (x + (height - y - 1) * width) * 4
                    val r = buffer.get(i).toInt() and 0xFF
                    val g = buffer.get(i + 1).toInt() and 0xFF
                    val b = buffer.get(i + 2).toInt() and 0xFF
                    val a = buffer.get(i + 3).toInt() and 0xFF
                    val color = (a shl 24) or (r shl 16) or (g shl 8) or b
                    image.setRGB(x, y, color)
                }
            }
            logger.debug("BufferedImage created successfully")

            // デバッグ用に画像をローカルに保存（後で削除可能）
            ImageIO.write(image, "png", java.io.File("debug_screenshot.png"))
            logger.debug("Screenshot saved locally for debugging.")

            image
        } catch (e: Exception) {
            logger.error("Failed to capture screenshot", e)
            null
        }
    }


    // 画像を Python サーバーに送信（IO スレッドで呼び出す）
    suspend fun sendToPython(image: BufferedImage) {
        // ソケットが接続されていなければ接続を確立
        if (socket == null || socket?.isClosed == true) {
            logger.info("Socket is not connected. Attempting to connect...")
            connectToPythonServer()
        }

        // 画像を PNG フォーマットにエンコード
        val imageData = ByteArrayOutputStream().use { baos ->
            ImageIO.write(image, "png", baos)
            baos.toByteArray()
        }

        // Mutex を使用して書き込みを同期化
        writeMutex.withLock {
            try {
                dataOutputStream?.writeInt(imageData.size)
                dataOutputStream?.write(imageData)
                dataOutputStream?.flush()
                logger.debug("Image sent successfully. Size: ${imageData.size} bytes")
            } catch (e: IOException) {
                logger.error("Failed to send image, closing socket and will attempt to reconnect.", e)
                closeConnection()
            }
        }
    }

    // スクリーンショットのキャプチャと送信を行う
    suspend fun captureAndSendScreenshot() {
        withContext(RenderDispatch) {
            val image = captureScreenshot()
            if (image != null) {
                // 送信部分を非同期で実行
                ioScope.launch {
                    sendToPython(image)
                }
            } else {
                logger.warn("Captured image is null.")
            }
        }
    }

    // サポートされている画像フォーマットを確認
    fun checkSupportedFormats() {
        val formats = ImageIO.getWriterFormatNames()
        logger.info("Supported formats: ${formats.joinToString(", ")}")
    }
}
