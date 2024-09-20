// ClientTickHandler.kt
package com.github.tappy27.pythonbridge.client

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import org.apache.logging.log4j.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object ClientTickHandler {

    private val logger = LogManager.getLogger(ClientTickHandler::class.java)

    // キャプチャ頻度を20FPSに設定（毎ティック）
    var captureInterval = 1 // 1ティックごとにキャプチャ（20FPS）

    private var tickCount = 0

    // CoroutineScopeを定義（IOディスパッチャを使用）
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Pythonからのbool値を受け取るための変数
    var isCaptureEnabled = true // テストのためにtrue

    fun register() {
        // HudRenderCallback を使用して描画後にキャプチャ
        HudRenderCallback.EVENT.register { matrices, tickDelta ->
            val client = MinecraftClient.getInstance()
            if (client.world != null) {
                tickCount++

                // キャプチャのタイミング
                if (isCaptureEnabled && tickCount % captureInterval == 0) {
                    // メインスレッドでスクリーンショットをキャプチャ
                    try {
                        // RenderDispatch を使用してメインスレッドで実行
                        ioScope.launch {
                            ScreenshotHandler.captureAndSendScreenshot()
                        }
                    } catch (e: Exception) {
                        logger.error("Error in captureAndSendScreenshot", e)
                    }
                }
            }
        }
    }
}
