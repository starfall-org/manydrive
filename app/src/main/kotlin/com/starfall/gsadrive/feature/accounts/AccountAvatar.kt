package com.starfall.gsadrive.feature.accounts

import com.starfall.gsadrive.R

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starfall.gsadrive.shared.i18n.localized
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AccountAvatar(account: AccountEntry?, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 44.dp) {
    val avatar = rememberRemoteAvatar(account?.avatarUrl)
    Surface(
        modifier = Modifier.size(size).clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        if (avatar != null) {
            Image(bitmap = avatar, contentDescription = localized(R.string.account), modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop)
        } else {
            Box(contentAlignment = Alignment.Center) {
                val letter = account?.title?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                Text(letter, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun rememberRemoteAvatar(url: String?): androidx.compose.ui.graphics.ImageBitmap? {
    val state = produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, url) {
        value = if (url.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                URL(url).openConnection().apply { connectTimeout = 5_000; readTimeout = 5_000 }
                    .getInputStream().use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    return state.value
}
