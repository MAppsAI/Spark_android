package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ── Spark identity ────────────────────────────────────────────────
// A refined azure→cyan duo on an obsidian canvas. Linear-grade dark UI:
// restrained saturation, layered surfaces, precise typography.
val SparkBlue = Color(0xFF4D7CFF)          // primary — confident azure
val SparkBlueBright = Color(0xFF79A0FF)    // hover / emphasis
val SparkBlueDeep = Color(0xFF2E5BDB)      // pressed / gradient tail
val CyberCyan = Color(0xFF3FC8F5)          // secondary accent (kept name for call-sites)
val CyberCyanDark = Color(0xFF2196C4)
val ElectricBlue = Color(0xFF6FA8FF)
val NeonIndigo = Color(0xFF7A7CFF)

// Semantic status colors — desaturated just enough to feel premium
val TerminalGreen = Color(0xFF3DDC97)      // online / success
val TerminalAmber = Color(0xFFF5A623)      // latency / warning
val TerminalRed = Color(0xFFFF5A6E)        // offline / destructive

// ── Layered dark surfaces ─────────────────────────────────────────
val DarkCanvas = Color(0xFF07090F)         // app background — obsidian, blue-cast
val DarkSurface = Color(0xFF0C101A)        // cards
val DarkSurfaceVariant = Color(0xFF131826) // insets, inputs
val DarkSurfaceElevated = Color(0xFF161C2C)// raised sheets, dialogs
val DarkSurfaceHigh = Color(0xFF1C2338)    // highest elevation, hover states
val DarkBorder = Color(0xFF232C42)         // hairlines
val DarkBorderSubtle = Color(0xFF171E2E)   // quieter dividers

// Scrim / overlay tints used by gradients & glass fills
val ScrimBlue = Color(0xFF0B1830)          // subtle blue wash behind hero headers
val GlassFill = Color(0xFF1A2133)          // glass chip fill base

// ── Hermes gold (Nous Hermes) — slightly warmer, muted ───────────
val HermesGold = Color(0xFFE0B25C)
val HermesGoldBright = Color(0xFFF5CD7A)
val HermesGoldDark = Color(0xFF8A6B32)
val HermesSurfaceGold = Color(0xFF1D1810)
val HermesBorderGold = Color(0xFF4A3B18)

// ── Typography ramp ───────────────────────────────────────────────
val TextPrimary = Color(0xFFF2F5FA)
val TextSecondary = Color(0xFFA9B3C6)
val TextMuted = Color(0xFF66718A)

// ── Light scheme counterparts ─────────────────────────────────────
val LightCanvas = Color(0xFFF7F8FC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEFF2F8)
val LightBorder = Color(0xFFE3E8F0)

// Aliases kept so existing call-sites compile unchanged
val SparkPrimary = SparkBlue
val SparkOnPrimary = Color(0xFFFFFFFF)
