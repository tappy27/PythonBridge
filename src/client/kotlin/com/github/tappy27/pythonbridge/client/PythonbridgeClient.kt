// PythonbridgeClient.kt
package com.github.tappy27.pythonbridge.client

import net.fabricmc.api.ClientModInitializer

class PythonbridgeClient : ClientModInitializer {

    override fun onInitializeClient() {
        // クライアントの初期化時にClientTickHandlerを登録
        ClientTickHandler.register()

        // アプリケーション終了時にリソースを解放
        Runtime.getRuntime().addShutdownHook(Thread {
            ScreenshotHandler.shutdown()
        })
    }
}
