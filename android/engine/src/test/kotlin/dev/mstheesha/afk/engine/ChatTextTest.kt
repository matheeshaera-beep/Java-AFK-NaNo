package dev.mstheesha.afk.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatTextTest {
    @Test
    fun flattensJsonComponents() {
        val json = """{"text":"Hello ","extra":[{"text":"world","color":"red"}]}"""
        assertEquals("Hello world", ChatText.fromJson(json))
    }

    @Test
    fun translatesChatType() {
        val json = """{"translate":"chat.type.text","with":["Steve","hi"]}"""
        assertEquals("<Steve> hi", ChatText.fromJson(json))
    }

    @Test
    fun fallsBackToRawJson() {
        assertEquals("not json", ChatText.fromJson("not json"))
    }

    @Test
    fun readsAnonymousNbt() {
        val writer = PacketWriter()
        writer.writeByte(10) // compound
        writer.writeByte(8) // string
        writer.writeString("text")
        writer.writeString("hello nbt")
        writer.writeByte(0) // end
        val reader = PacketReader(writer.buf, 0, writer.size)
        assertEquals("hello nbt", ChatText.fromNbt(reader, namedRoot = false))
    }

    @Test
    fun readsNamedNbt() {
        val writer = PacketWriter()
        writer.writeByte(10)
        writer.writeShort(0) // empty root name
        writer.writeByte(8)
        writer.writeString("text")
        writer.writeString("named")
        writer.writeByte(0)
        val reader = PacketReader(writer.buf, 0, writer.size)
        assertEquals("named", ChatText.fromNbt(reader, namedRoot = true))
    }

    @Test
    fun readsNbtWithListExtra() {
        val writer = PacketWriter()
        writer.writeByte(10)
        writer.writeByte(8)
        writer.writeString("text")
        writer.writeString("a")
        writer.writeByte(9) // list
        writer.writeString("extra")
        writer.writeByte(10) // of compounds
        writer.writeInt(1)
        writer.writeByte(8)
        writer.writeString("text")
        writer.writeString("b")
        writer.writeByte(0)
        writer.writeByte(0)
        val reader = PacketReader(writer.buf, 0, writer.size)
        assertEquals("ab", ChatText.fromNbt(reader, namedRoot = false))
    }
}
