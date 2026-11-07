package dev.mstheesha.afk

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * Parses Minecraft formatting codes (§0-§f, §k-§o, §r) into Jetpack Compose AnnotatedString.
 * Adheres strictly to Minecraft formatting rules:
 * - Color codes (§0-§f) reset any active font styling (§l, §m, §n, §o).
 * - Reset (§r) restores default color and clears all styles.
 * - Dark colors (§0 Black, §8 Dark Gray) are given minimum contrast against dark backgrounds.
 */
object ChatFormatter {

    // Minecraft standard 16 color palette with slight contrast adjustments for dark themes
    private val MC_COLORS = mapOf(
        '0' to Color(0xFF555555), // §0 Black (lifted to readable charcoal for dark theme)
        '1' to Color(0xFF0000AA), // §1 Dark Blue
        '2' to Color(0xFF00AA00), // §2 Dark Green
        '3' to Color(0xFF00AAAA), // §3 Dark Aqua
        '4' to Color(0xFFAA0000), // §4 Dark Red
        '5' to Color(0xFFAA00AA), // §5 Dark Purple
        '6' to Color(0xFFFFAA00), // §6 Gold
        '7' to Color(0xFFAAAAAA), // §7 Gray
        '8' to Color(0xFF777777), // §8 Dark Gray
        '9' to Color(0xFF5555FF), // §9 Blue
        'a' to Color(0xFF55FF55), // §a Green
        'b' to Color(0xFF55FFFF), // §b Aqua
        'c' to Color(0xFFFF5555), // §c Red
        'd' to Color(0xFFFF55FF), // §d Light Purple
        'e' to Color(0xFFFFFF55), // §e Yellow
        'f' to Color(0xFFFFFFFF), // §f White
    )

    fun format(text: String, defaultColor: Color): AnnotatedString {
        if (!text.contains('§') && !text.contains('&')) {
            return AnnotatedString(text, SpanStyle(color = defaultColor))
        }

        return buildAnnotatedString {
            var currentColor = defaultColor
            var isBold = false
            var isItalic = false
            var isUnderline = false
            var isStrikethrough = false

            var i = 0
            val len = text.length

            while (i < len) {
                val c = text[i]
                // Support both § and & formatting codes commonly used in server chat
                val isCode = (c == '§' || c == '&') && i + 1 < len
                if (isCode) {
                    val code = text[i + 1].lowercaseChar()
                    when {
                        code in MC_COLORS -> {
                            currentColor = MC_COLORS[code] ?: defaultColor
                            // In Minecraft specification, changing color resets active formatting
                            isBold = false
                            isItalic = false
                            isUnderline = false
                            isStrikethrough = false
                        }
                        code == 'l' -> isBold = true
                        code == 'o' -> isItalic = true
                        code == 'n' -> isUnderline = true
                        code == 'm' -> isStrikethrough = true
                        code == 'r' -> {
                            currentColor = defaultColor
                            isBold = false
                            isItalic = false
                            isUnderline = false
                            isStrikethrough = false
                        }
                        code == 'k' -> {
                            // Obfuscated: keep formatting, nothing special needed
                        }
                        else -> {
                            // Unrecognized code, treat as literal characters
                            val spanStyle = currentSpanStyle(currentColor, isBold, isItalic, isUnderline, isStrikethrough)
                            pushStyle(spanStyle)
                            append("$c$code")
                            pop()
                            i += 2
                            continue
                        }
                    }
                    i += 2
                } else {
                    // Accumulate plain text until next formatting code
                    val start = i
                    while (i < len && !( (text[i] == '§' || text[i] == '&') && i + 1 < len && isFormatChar(text[i + 1]) )) {
                        i++
                    }
                    val chunk = text.substring(start, i)
                    val spanStyle = currentSpanStyle(currentColor, isBold, isItalic, isUnderline, isStrikethrough)
                    pushStyle(spanStyle)
                    append(chunk)
                    pop()
                }
            }
        }
    }

    private fun isFormatChar(c: Char): Boolean {
        val lower = c.lowercaseChar()
        return lower in '0'..'9' || lower in 'a'..'f' || lower in listOf('l', 'm', 'n', 'o', 'r', 'k')
    }

    private fun currentSpanStyle(
        color: Color,
        bold: Boolean,
        italic: Boolean,
        underline: Boolean,
        strikethrough: Boolean,
    ): SpanStyle {
        val decorations = mutableListOf<TextDecoration>()
        if (underline) decorations.add(TextDecoration.Underline)
        if (strikethrough) decorations.add(TextDecoration.LineThrough)

        return SpanStyle(
            color = color,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = when {
                decorations.size == 2 -> TextDecoration.combine(decorations)
                decorations.size == 1 -> decorations[0]
                else -> TextDecoration.None
            },
        )
    }
}
