package com.github.tappy27.pythonbridge.client

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.Framebuffer
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import javax.imageio.ImageIO

object ScreenshotHandler {

    // スクリーンショットをキャプチャして返す
    fun captureScreenshot(): BufferedImage {
        val client: MinecraftClient = MinecraftClient.getInstance()
        val framebuffer: Framebuffer = client.framebuffer
        val width = framebuffer.textureWidth
        val height = framebuffer.textureHeight

        // PBOのセットアップ
        val pboId = GL30.glGenBuffers()
        GL30.glBindBuffer(GL30.GL_PIXEL_PACK_BUFFER, pboId)
        GL30.glBufferData(GL30.GL_PIXEL_PACK_BUFFER, width * height * 3L, GL30.GL_STREAM_READ)
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, 0)
        val buffer = GL30.glMapBuffer(GL30.GL_PIXEL_PACK_BUFFER, GL30.GL_READ_ONLY) as ByteBuffer

        // ピクセルデータをBufferedImageに変換
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val intArray = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = (x + (height - 1 - y) * width) * 3
                val r = buffer.get(i).toInt() and 0xFF
                val g = buffer.get(i + 1).toInt() and 0xFF
                val b = buffer.get(i + 2).toInt() and 0xFF
                intArray[x + y * width] = (r shl 16) or (g shl 8) or b
            }
        }

        image.setRGB(0, 0, width, height, intArray, 0, width)

        // PBOをクリーンアップ
        GL30.glUnmapBuffer(GL30.GL_PIXEL_PACK_BUFFER)
        GL30.glBindBuffer(GL30.GL_PIXEL_PACK_BUFFER, 0)
        GL30.glDeleteBuffers(pboId)

        return image
    }

    // 画像をPythonに送信する処理
    fun sendToPython(image: BufferedImage) {
        val host = "localhost" // Pythonサーバーのホスト
        val port = 5000         // Pythonサーバーのポート

        Socket(host, port).use { socket ->
            val outputStream = DataOutputStream(socket.getOutputStream())
            val byteArrayOutputStream = ByteArrayOutputStream()

            // 画像をバイト配列に変換 (PNG形式)
            ImageIO.write(image, "png", byteArrayOutputStream)
            val imageBytes = byteArrayOutputStream.toByteArray()

            // 画像サイズを最初に送信
            outputStream.writeInt(imageBytes.size)

            // 画像データを送信
            outputStream.write(imageBytes)
        }
    }
}
