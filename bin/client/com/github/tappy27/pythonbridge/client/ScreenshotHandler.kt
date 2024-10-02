package com.github.tappy27.pythonbridge.client

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.Framebuffer
import org.apache.logging.log4j.LogManager
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO
import kotlin.concurrent.thread

object ScreenshotHandler {
    private val logger = LogManager.getLogger(ScreenshotHandler::class.java)
    private var socket: Socket? = null

    // 画像処理と送信を別スレッドで実行
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()

    fun connectSocket() {
        try {
            socket = Socket("localhost", 5000)
            logger.info("Connected to Python server.")
        } catch (e: IOException) {
            logger.error("Failed to connect to Python server.", e)
        }
    }

    fun captureAndSendScreenshot() {
        val client = MinecraftClient.getInstance()
        val framebuffer: Framebuffer = client.framebuffer

        val width = framebuffer.textureWidth
        val height = framebuffer.textureHeight

        val bufferSize = width * height

        val pixels = IntArray(bufferSize)
        val byteBuffer = ByteBuffer.allocateDirect(bufferSize * 4).order(ByteOrder.nativeOrder())
        byteBuffer.clear()

        // メインスレッドでフレームバッファからピクセルを読み取る
        byteBuffer.position(0)
        org.lwjgl.opengl.GL11.glReadPixels(
            0,
            0,
            width,
            height,
            org.lwjgl.opengl.GL12.GL_BGRA,
            org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE,
            byteBuffer
        )

        byteBuffer.asIntBuffer().get(pixels)

        // ピクセルデータをコピーして別スレッドで処理
        val pixelsCopy = pixels.copyOf()
        executor.submit {
            processAndSendImage(pixelsCopy, width, height)
        }
    }

    private fun processAndSendImage(pixels: IntArray, width: Int, height: Int) {
        try {
            // 垂直方向にピクセルを反転
            val flippedPixels = IntArray(pixels.size)
            for (y in 0 until height) {
                val srcPos = y * width
                val destPos = (height - y - 1) * width
                System.arraycopy(pixels, srcPos, flippedPixels, destPos, width)
            }

            // ピクセルデータをBufferedImageに変換
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            image.setRGB(0, 0, width, height, flippedPixels, 0, width)

            // 画像をPNG形式に圧縮
            val outputStream = ByteArrayOutputStream()
            ImageIO.write(image, "png", outputStream)
            val imageData = outputStream.toByteArray()

            // ソケットが接続されていない場合は再接続
            if (socket == null || socket!!.isClosed) {
                connectSocket()
            }

            // 画像データを送信
            socket?.let {
                val out = it.getOutputStream()
                val sizeBuffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(imageData.size)
                out.write(sizeBuffer.array())
                out.write(imageData)
                out.flush()
            }
        } catch (e: IOException) {
            logger.error("Failed to send screenshot.", e)
            socket?.close()
            socket = null
        }
    }
}
