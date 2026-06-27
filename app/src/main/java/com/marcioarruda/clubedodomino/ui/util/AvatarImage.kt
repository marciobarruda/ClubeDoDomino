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

    val resId = when (url) {
        "avatar_1" -> com.marcioarruda.clubedodomino.R.drawable.avatar_1
        "avatar_2" -> com.marcioarruda.clubedodomino.R.drawable.avatar_2
        "avatar_3" -> com.marcioarruda.clubedodomino.R.drawable.avatar_3
        "avatar_4" -> com.marcioarruda.clubedodomino.R.drawable.avatar_4
        "avatar_5" -> com.marcioarruda.clubedodomino.R.drawable.avatar_5
        "avatar_6" -> com.marcioarruda.clubedodomino.R.drawable.avatar_6
        "avatar_7" -> com.marcioarruda.clubedodomino.R.drawable.avatar_7
        "avatar_8" -> com.marcioarruda.clubedodomino.R.drawable.avatar_8
        "avatar_9" -> com.marcioarruda.clubedodomino.R.drawable.avatar_9
        "avatar_10" -> com.marcioarruda.clubedodomino.R.drawable.avatar_10
        "avatar_11" -> com.marcioarruda.clubedodomino.R.drawable.avatar_11
        "avatar_12" -> com.marcioarruda.clubedodomino.R.drawable.avatar_12
        "avatar_13" -> com.marcioarruda.clubedodomino.R.drawable.avatar_13
        "avatar_14" -> com.marcioarruda.clubedodomino.R.drawable.avatar_14
        "avatar_15" -> com.marcioarruda.clubedodomino.R.drawable.avatar_15
        "avatar_16" -> com.marcioarruda.clubedodomino.R.drawable.avatar_16
        "avatar_17" -> com.marcioarruda.clubedodomino.R.drawable.avatar_17
        "avatar_18" -> com.marcioarruda.clubedodomino.R.drawable.avatar_18
        "avatar_19" -> com.marcioarruda.clubedodomino.R.drawable.avatar_19
        "avatar_20" -> com.marcioarruda.clubedodomino.R.drawable.avatar_20
        "avatar_21" -> com.marcioarruda.clubedodomino.R.drawable.avatar_21
        "avatar_22" -> com.marcioarruda.clubedodomino.R.drawable.avatar_22
        "avatar_23" -> com.marcioarruda.clubedodomino.R.drawable.avatar_23
        "avatar_24" -> com.marcioarruda.clubedodomino.R.drawable.avatar_24
        "avatar_25" -> com.marcioarruda.clubedodomino.R.drawable.avatar_25
        else -> null
    }

    if (resId != null) {
        Image(
            painter = androidx.compose.ui.res.painterResource(id = resId),
            contentDescription = "Avatar",
            modifier = finalModifier,
            contentScale = ContentScale.Crop
        )
        return
    }

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
