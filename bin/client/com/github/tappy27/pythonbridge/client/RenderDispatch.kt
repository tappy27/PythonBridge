// RenderDispatch.kt
package com.github.tappy27.pythonbridge.client

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import net.minecraft.client.MinecraftClient
import kotlin.coroutines.CoroutineContext

object RenderDispatch : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        MinecraftClient.getInstance().execute {
            block.run()
        }
    }
}
