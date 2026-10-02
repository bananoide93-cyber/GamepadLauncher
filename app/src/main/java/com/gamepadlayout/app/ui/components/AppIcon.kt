package com.gamepadlayout.app.ui.components

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppIconCache {
    val cache = LruCache<String, ImageBitmap>(80)
}

/** Ícone oficial do app instalado (via PackageManager), carregado fora da thread principal. */
@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(initialValue = AppIconCache.cache.get(packageName), packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.packageManager.getApplicationIcon(packageName).toBitmap(160, 160).asImageBitmap()
                }.getOrNull()
            }?.also { AppIconCache.cache.put(packageName, it) }
        }
    }
    val b = bmp
    val shape = RoundedCornerShape(14.dp)
    if (b != null) {
        Image(bitmap = b, contentDescription = null, modifier = modifier.clip(shape), contentScale = ContentScale.Fit)
    } else {
        Box(modifier.clip(shape).background(Color.White.copy(alpha = 0.08f)))
    }
}
