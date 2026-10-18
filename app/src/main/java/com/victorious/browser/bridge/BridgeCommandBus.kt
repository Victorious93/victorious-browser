package com.victorious.browser.bridge

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-process bus so the bound service can ask the UI to open a URL / panel
 * in the WebView. MainActivity collects [commands].
 */
object BridgeCommandBus {
    sealed class Command {
        data class OpenUrl(val url: String) : Command()
    }

    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 8)
    val commands: SharedFlow<Command> = _commands.asSharedFlow()

    fun openUrl(url: String) {
        _commands.tryEmit(Command.OpenUrl(url))
    }
}
