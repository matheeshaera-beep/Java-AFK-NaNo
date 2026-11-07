package dev.mstheesha.afk

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatFormatterTest {

    @Test
    fun formatsPlainString() {
        val defaultColor = Color.White
        val result = ChatFormatter.format("Hello world", defaultColor)
        assertEquals("Hello world", result.text)
    }

    @Test
    fun parsesColorCodes() {
        val result = ChatFormatter.format("§aGreen §cRed §6Gold", Color.White)
        assertEquals("Green Red Gold", result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun parsesResetCodeProperly() {
        val result = ChatFormatter.format("§aGreen§rDefault", Color.Gray)
        assertEquals("GreenDefault", result.text)
        assertEquals(2, result.spanStyles.size)
        assertEquals(Color(0xFF55FF55), result.spanStyles[0].item.color)
        assertEquals(Color.Gray, result.spanStyles[1].item.color)
    }

    @Test
    fun colorCodeResetsStyles() {
        // According to Minecraft specification, changing color resets bold/italic styles
        val result = ChatFormatter.format("§a§lBoldGreen§cNormalRed", Color.White)
        assertEquals("BoldGreenNormalRed", result.text)
        assertEquals(FontWeight.Bold, result.spanStyles[0].item.fontWeight)
        assertEquals(FontWeight.Normal, result.spanStyles[1].item.fontWeight)
    }

    @Test
    fun parsesStylesCorrectly() {
        val result = ChatFormatter.format("§lBold §oItalic §nUnderline §mStrike", Color.White)
        assertEquals("Bold Italic Underline Strike", result.text)
    }

    @Test
    fun handlesTrailingSectionSymbol() {
        val result = ChatFormatter.format("Hello §", Color.White)
        assertEquals("Hello §", result.text)
    }
}
