package com.privatecaller.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlin.math.abs

/**
 * Circular contact avatar. Shows the contact photo when one is set (URI from
 * the system Contacts provider), otherwise an initial letter on a colour
 * derived from the name.
 */
@Composable
fun Avatar(
    name: String?,
    number: String?,
    modifier: Modifier = Modifier,
    photoUri: String? = null,
    sizeDp: Int = 44,
) {
    val seed = name ?: number ?: "?"
    val initial = (
        name?.trim()?.firstOrNull { it.isLetter() }
            ?: number?.firstOrNull { it.isDigit() }
            ?: '#'
        ).uppercaseChar()

    Surface(
        shape = CircleShape,
        color = avatarColor(seed),
        modifier = modifier.size(sizeDp.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Always draw the initial first; the photo (if any) covers it once
            // loaded, so contacts without a (loadable) photo still show a letter.
            Text(
                text = initial.toString(),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
            )
            if (photoUri != null) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            }
        }
    }
}

private val PALETTE = listOf(
    Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFF8E24AA), Color(0xFFF4511E),
    Color(0xFF00897B), Color(0xFFD81B60), Color(0xFF3949AB), Color(0xFF6D4C41),
)

private fun avatarColor(seed: String): Color =
    PALETTE[abs(seed.hashCode()) % PALETTE.size]
