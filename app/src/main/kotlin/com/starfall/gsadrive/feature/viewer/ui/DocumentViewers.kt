package com.starfall.gsadrive.feature.viewer.ui

import com.starfall.gsadrive.R

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.starfall.gsadrive.shared.i18n.localized

@Composable
internal fun TextDocumentViewer(
    fileId: String,
    text: String,
    saving: Boolean,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit
) {
    var editing by remember(fileId) { mutableStateOf(false) }
    var menuOpen by remember(fileId) { mutableStateOf(false) }
    var originalText by remember(fileId) { mutableStateOf(text) }

    BackHandler(enabled = editing && !saving) {
        onTextChange(originalText)
        editing = false
    }

    if (editing) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth().weight(1f),
                enabled = !saving,
                textStyle = MaterialTheme.typography.bodyMedium,
                label = { Text(localized(R.string.content)) }
            )
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        onTextChange(originalText)
                        editing = false
                    }
                ) { Text(localized(R.string.cancel)) }
                FilledTonalButton(
                    enabled = !saving,
                    onClick = {
                        onSave()
                        editing = false
                    }
                ) { Text(if (saving) localized(R.string.saving) else localized(R.string.save_changes)) }
            }
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            SelectionContainer {
                Column(
                    Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 72.dp)
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                IconButton(enabled = !saving, onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, localized(R.string.options))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(localized(R.string.edit)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        enabled = !saving,
                        onClick = {
                            originalText = text
                            menuOpen = false
                            editing = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun ImageViewer(path: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val decoded by androidx.compose.runtime.produceState<Pair<Boolean, androidx.compose.ui.graphics.ImageBitmap?>>(false to null, path) {
        value = true to kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                fun input() = if (path.startsWith("content://"))
                    context.contentResolver.openInputStream(android.net.Uri.parse(path))
                    else java.io.File(path).inputStream()
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                input()?.use { BitmapFactory.decodeStream(it, null, options) }
                options.inSampleSize = 1
                while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 4096) options.inSampleSize *= 2
                options.inJustDecodeBounds = false
                input()?.use { BitmapFactory.decodeStream(it, null, options)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    val bitmap = decoded.second
    if (bitmap == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!decoded.first) CircularProgressIndicator()
            else Text(localized(R.string.could_not_decode_this_image), color = Color.White)
        }
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().padding(12.dp),
            contentScale = ContentScale.Fit
        )
    }
}

/** A page owns only its media surface; the expanding pager draws one shared controls layer. */
