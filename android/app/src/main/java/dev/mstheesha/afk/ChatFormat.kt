package dev.mstheesha.afk

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * Converts Minecraft `§` color/style markers (`§0..§f`, `§l`, `§o`, `§n`, `§m`,
 * `§r`, and extended `§#rrggbb`) into a Compose [AnnotatedString], keeping
 * contrast readable in both dark and light themes.
 */
fun formatMinecraftChat(
    styled: String,
    defaultColor: Color,
    isDark: Boolean,
): AnnotatedString {
    if (styled.indexOf('§') < 0) return AnnotatedString(styled)
    return buildAnnotatedString {
        var color = defaultColor
        var bold = false
        var italic = false
        var underline = false
        var strike = false
        var spanStart = 0
        val chunk = StringBuilder()

        fun flush() {
            if (chunk.isEmpty()) return
            val text = chunk.toString()
            chunk.setLength(0)
            val deco = when {
                underline && strike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                underline -> TextDecoration.Underline
                strike -> TextDecoration.LineThrough
                else -> null
            }
            pushStyle(
                SpanStyle(
                    color = color,
                    fontWeight = if (bold) FontWeight.Bold else null,
                    fontStyle = if (italic) FontStyle.Italic else null,
                    textDecoration = deco,
                ),
            )
            append(text)
            pop()
            spanStart += text.length
        }

        var i = 0
        while (i < styled.length) {
            if (styled[i] == '§' && i + 1 < styled.length) {
                val code = styled[i + 1]
                if (code == '#' && i + 7 < styled.length) {
                    val hex = styled.substring(i + 2, i + 8)
                    val parsed = hex.toIntOrNull(16)
                    if (parsed != null) {
                        flush()
                        color = adjustContrast(Color(0xFF000000L or parsed.toLong()), isDark)
                        bold = false
                        italic = false
                        underline = false
                        strike = false
                        i += 8
                        continue
                    }
                }
                flush()
                when (code.lowercaseChar()) {
                    '0' -> { color = if (isDark) Color(0xFF9E9E9E) else Color(0xFF1A1A1A); bold = false; italic = false; underline = false; strike = false }
                    '1' -> { color = if (isDark) Color(0xFF5C85FF) else Color(0xFF0000AA); bold = false; italic = false; underline = false; strike = false }
                    '2' -> { color = if (isDark) Color(0xFF4CAF50) else Color(0xFF007A00); bold = false; italic = false; underline = false; strike = false }
                    '3' -> { color = if (isDark) Color(0xFF26C6DA) else Color(0xFF00838F); bold = false; italic = false; underline = false; strike = false }
                    '4' -> { color = if (isDark) Color(0xFFEF5350) else Color(0xFFAA0000); bold = false; italic = false; underline = false; strike = false }
                    '5' -> { color = if (isDark) Color(0xFFCE93D8) else Color(0xFF8E24AA); bold = false; italic = false; underline = false; strike = false }
                    '6' -> { color = if (isDark) Color(0xFFFFB74D) else Color(0xFFD47A00); bold = false; italic = false; underline = false; strike = false }
                    '7' -> { color = if (isDark) Color(0xFFBDBDBD) else Color(0xFF616161); bold = false; italic = false; underline = false; strike = false }
                    '8' -> { color = if (isDark) Color(0xFF8D8D8D) else Color(0xFF424242); bold = false; italic = false; underline = false; strike = false }
                    '9' -> { color = if (isDark) Color(0xFF7986CB) else Color(0xFF3949AB); bold = false; italic = false; underline = false; strike = false }
                    'a' -> { color = if (isDark) Color(0xFF69F0AE) else Color(0xFF2E7D32); bold = false; italic = false; underline = false; strike = false }
                    'b' -> { color = if (isDark) Color(0xFF64FFDA) else Color(0xFF00897B); bold = false; italic = false; underline = false; strike = false }
                    'c' -> { color = if (isDark) Color(0xFFFF7070) else Color(0xFFC62828); bold = false; italic = false; underline = false; strike = false }
                    'd' -> { color = if (isDark) Color(0xFFF48FB1) else Color(0xFFAD1457); bold = false; italic = false; underline = false; strike = false }
                    'e' -> { color = if (isDark) Color(0xFFFFF176) else Color(0xFFB28900); bold = false; italic = false; underline = false; strike = false }
                    'f' -> { color = defaultColor; bold = false; italic = false; underline = false; strike = false }
                    'l' -> bold = true
                    'o' -> italic = true
                    'n' -> underline = true
                    'm' -> strike = true
                    'r' -> {
                        color = defaultColor
                        bold = false
                        italic = false
                        underline = false
                        strike = false
                    }
                    else -> Unit // ignore 'k' (obfuscated) or unknown
                }
                i += 2
                continue
            }
            chunk.append(styled[i++])
        }
        flush()
    }
}

private fun adjustContrast(c: Color, isDark: Boolean): Color {
    val luma = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
    if (isDark && luma < 0.22f) {
        return Color(
            red = (c.red + 0.35f).coerceAtMost(1f),
            green = (c.green + 0.35f).coerceAtMost(1f),
            blue = (c.blue + 0.35f).coerceAtMost(1f),
        )
    }
    if (!isDark && luma > 0.85f) {
        return Color(
            red = (c.red * 0.65f),
            green = (c.green * 0.65f),
            blue = (c.blue * 0.65f),
        )
    }
    return c
}
