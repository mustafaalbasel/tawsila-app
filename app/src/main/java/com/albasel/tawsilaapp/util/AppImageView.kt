package com.albasel.tawsilaapp.util

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun AppImageView(
    data: Any?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val clickableModifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier

    if (data == null) {
        PlaceholderBox(modifier)
        return
    }

    if (data is ImageVector) {
        Image(
            imageVector = data,
            contentDescription = null,
            modifier = modifier.then(clickableModifier),
            contentScale = contentScale
        )
        return
    }

    val dataStr = data.toString()
    if (dataStr.isBlank()) {
        PlaceholderBox(modifier)
        return
    }

    // Direct Uri handling (Gallery/Camera selections)
    if (data is android.net.Uri || dataStr.startsWith("content:") || dataStr.startsWith("file:") || dataStr.startsWith("android.resource:")) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(data)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = modifier.then(clickableModifier),
            contentScale = contentScale
        )
        return
    }

    // Safe detection
    val isUrl = remember(dataStr) { dataStr.startsWith("http") }
    val isDriveId = remember(dataStr) { !isUrl && dataStr.length in 20..60 }
    
    if (isUrl || isDriveId) {
        val finalUrl = if (isUrl) dataStr else "https://drive.google.com/thumbnail?id=$dataStr&sz=w1000"
        
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(finalUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = modifier.then(clickableModifier),
            contentScale = contentScale
        )
    } else if (dataStr.length > 100) {
        // Base64 handling
        val bitmap = remember(dataStr) {
            try {
                val cleanData = if (dataStr.contains(",")) dataStr.split(",")[1] else dataStr
                val imageBytes = Base64.decode(cleanData, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            } catch (e: Exception) { null }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = modifier.then(clickableModifier),
                contentScale = contentScale
            )
        } else {
            PlaceholderBox(modifier)
        }
    } else {
        PlaceholderBox(modifier)
    }
}

@Composable
fun PlaceholderBox(modifier: Modifier) {
    Box(modifier = modifier.background(Color.LightGray.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Person, null, tint = Color.Gray)
    }
}
