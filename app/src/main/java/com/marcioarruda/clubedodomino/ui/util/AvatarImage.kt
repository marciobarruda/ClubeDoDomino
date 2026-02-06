package com.marcioarruda.clubedodomino.ui.util

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.marcioarruda.clubedodomino.ui.theme.RoyalGold

@Composable
fun AvatarImage(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    borderColor: Color = RoyalGold,
    borderWidth: Dp = 1.dp
) {
    val finalModifier = modifier
        .size(size)
        .clip(CircleShape)
        .border(borderWidth, borderColor, CircleShape)
        .background(Color.Gray)

    if (url.isNullOrBlank()) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = "Avatar",
            modifier = finalModifier.background(Color.LightGray),
            tint = Color.White
        )
        return
    }

    // Check if it's Base64
    if (url.startsWith("data:image") || !url.startsWith("http")) {
        val bitmap = remember(url) {
            try {
                val cleanBase64 = if (url.contains(",")) url.substringAfter(",") else url
                val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }

        if (bitmap != null) {
            Image(
                painter = BitmapPainter(bitmap),
                contentDescription = "Avatar",
                modifier = finalModifier,
                contentScale = ContentScale.Crop
            )
        } else {
             // Fallback to AsyncImage or Placeholder if decoding fails
             AsyncImage(
                model = url,
                contentDescription = "Avatar",
                modifier = finalModifier,
                contentScale = ContentScale.Crop
            )
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = "Avatar",
            modifier = finalModifier,
            contentScale = ContentScale.Crop
        )
    }
}
