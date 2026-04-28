package com.example.qrattendance.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

@Composable
fun LoadingShimmer(modifier: Modifier = Modifier) {
  val transition = rememberInfiniteTransition(label = "shimmer")
  val alpha by transition.animateFloat(
    initialValue = 0.35f,
    targetValue = 0.8f,
    animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
    label = "alpha",
  )
  Box(modifier.fillMaxWidth().height(72.dp).alpha(alpha).background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)))
}
