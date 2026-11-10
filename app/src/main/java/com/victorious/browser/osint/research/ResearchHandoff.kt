package com.victorious.browser.osint.research

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory hand-off from the browser (e.g. image long-press) into the Deep Research hub. */
object ResearchHandoff {
    data class ImageRequest(val url: String, val fromPrivateTab: Boolean)

    private val _image = MutableStateFlow<ImageRequest?>(null)
    val image: StateFlow<ImageRequest?> = _image.asStateFlow()

    fun reverseImage(url: String, fromPrivateTab: Boolean) { _image.value = ImageRequest(url, fromPrivateTab) }
    fun consumeImage(): ImageRequest? = _image.value.also { _image.value = null }
}
