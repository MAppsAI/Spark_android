package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Spark shape scale — generous, consistent rounding
val SparkShapes =
  Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

object SparkRadii {
  val card = 18.dp
  val sheet = 24.dp
  val chip = 10.dp
  val pill = 50
  val button = 14.dp
}
