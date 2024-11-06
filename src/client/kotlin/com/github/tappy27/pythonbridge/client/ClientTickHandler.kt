// ClientTickHandler.kt
package com.github.tappy27.pythonbridge.client

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.MinecraftClient
import org.apache.logging.log4j.LogManager

object ClientTickHandler {

    private val logger = LogManager.getLogger(ClientTickHandler::class.java)

    // 1ティックごとにキャプチャ（20FPS）
    var captureInterval = 1

    private var tickCount = 0

    // Pythonからのbool値を受け取るための変数
    var isCaptureEnabled = true // 一旦true固定

    fun register() {
        // ワールドの描画完了後に呼び出されるイベントを登録
        WorldRenderEvents.END.register { context ->
            val client = MinecraftClient.getInstance()
            // ゲームが実行中で、ウィンドウが有効であることを確認
            if (client.isRunning && client.world != null && client.window.handle != 0L && client.window.isFullscreen) {
                tickCount++

                // キャプチャのタイミング
                if (isCaptureEnabled && tickCount % captureInterval == 0) {
                    try {
                        // メインスレッドでスクリーンショットを撮影
                        ScreenshotHandler.captureAndSendScreenshot()
                    } catch (e: Exception) {
                        logger.error("Error in captureAndSendScreenshot", e)
                    }
                }
            }
        }
    }
}
