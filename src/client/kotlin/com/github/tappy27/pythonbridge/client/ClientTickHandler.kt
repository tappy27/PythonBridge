package com.github.tappy27.pythonbridge.client

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import java.net.Socket
import java.io.DataInputStream

object ClientTickHandler {

    // キャプチャ頻度（ティックごとに何回キャプチャするか）
    var captureInterval = 20 // 20ティック毎（= 1秒毎）
    private var tickCount = 0

    // Pythonからのbool値を受け取るための変数
    var isCaptureEnabled = false

    // Pythonからの信号を受け取るメソッド
    fun receiveSignalFromPython() {
        try {
            val socket = Socket("localhost", 5000) // Pythonがboolを送るサーバー
            val inputStream = DataInputStream(socket.getInputStream())

            // Pythonからのbool値を受け取る（`True`ならスクリーンショットを有効に）
            isCaptureEnabled = inputStream.readBoolean()

            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { client ->
            if (client.world != null) {
                tickCount++

                // Pythonからの信号を受け取る
                receiveSignalFromPython()

                // Pythonがスクリーンショットを許可した場合にのみ撮影
                if (isCaptureEnabled && tickCount % captureInterval == 0) {
                    val screenshot = ScreenshotHandler.captureScreenshot()
                    ScreenshotHandler.sendToPython(screenshot) // スクリーンショットをPythonに送信
                }
            }
        })
    }
}