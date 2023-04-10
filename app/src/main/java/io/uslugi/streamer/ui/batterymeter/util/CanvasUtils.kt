package io.uslugi.streamer.ui.batterymeter.util

import android.graphics.Canvas
import android.graphics.Path

/**
 * Wrap the specified [block] in calls to [Canvas.save]
 * and [Canvas.restoreToCount].
 */
inline fun Canvas.withSave(block: Canvas.() -> Unit) {
    val checkpoint = save()
    try {
        block()
    } finally {
        restoreToCount(checkpoint)
    }
}

internal fun Canvas.clipOutPathCompat(path: Path) {
    clipOutPath(path)
}