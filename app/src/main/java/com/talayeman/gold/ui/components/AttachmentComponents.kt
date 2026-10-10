package com.talayeman.gold.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

/** Square thumbnail for a photo/invoice image. [model] can be a File or a Uri. */
@Composable
fun AttachmentThumbnail(
    model: Any,
    contentDescription: String?,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    /** If set, a small optional "save to gallery" button is shown on the thumbnail. */
    onSaveToGallery: (() -> Unit)? = null,
    savedToGallery: Boolean = false
) {
    val errorPainter: Painter = rememberVectorPainter(Icons.Default.BrokenImage)
    Box(
        Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            error = errorPainter,
            modifier = Modifier.fillMaxSize()
        )
        if (onSaveToGallery != null) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .size(28.dp)
            ) {
                IconButton(onClick = onSaveToGallery, enabled = !savedToGallery, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (savedToGallery) Icons.Default.CheckCircle else Icons.Default.SaveAlt,
                        contentDescription = "ذخیره در گالری",
                        tint = if (savedToGallery) Color(0xFF7BE0A0) else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        if (onRemove != null) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(28.dp)
            ) {
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "حذف تصویر",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/** Full-screen preview of an attached image. */
@Composable
fun ImagePreviewDialog(
    model: Any,
    onDismiss: () -> Unit,
    /** If set, shows an optional "save to gallery" button. */
    onSaveToGallery: (() -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onDismiss() }
        ) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().align(Alignment.Center)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(Icons.Default.Close, contentDescription = "بستن", tint = Color.White)
            }
            if (onSaveToGallery != null) {
                androidx.compose.material3.FilledTonalButton(
                    onClick = onSaveToGallery,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
                ) {
                    Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                    androidx.compose.material3.Text("ذخیره در گالری")
                }
            }
        }
    }
}
