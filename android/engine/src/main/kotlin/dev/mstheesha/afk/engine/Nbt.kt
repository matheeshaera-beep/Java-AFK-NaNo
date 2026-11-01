package dev.mstheesha.afk.engine

/**
 * Network NBT reader, only as complete as chat components need: compounds,
 * lists, strings, numbers and arrays. Anything else is skipped safely.
 */
internal object Nbt {
    private const val TAG_END = 0
    private const val TAG_BYTE = 1
    private const val TAG_SHORT = 2
    private const val TAG_INT = 3
    private const val TAG_LONG = 4
    private const val TAG_FLOAT = 5
    private const val TAG_DOUBLE = 6
    private const val TAG_BYTE_ARRAY = 7
    private const val TAG_STRING = 8
    private const val TAG_LIST = 9
    private const val TAG_COMPOUND = 10
    private const val TAG_INT_ARRAY = 11
    private const val TAG_LONG_ARRAY = 12

    /**
     * Reads one tag. [named] is true for the pre-1.20.2 network form, which
     * carries a root name; the modern form is anonymous.
     */
    fun read(r: PacketReader, named: Boolean): Any? {
        val type = r.readUnsignedByte()
        if (type == TAG_END) return null
        if (named) {
            val nameLength = r.readUnsignedShort()
            if (nameLength > 0) r.skip(nameLength)
        }
        return payload(r, type)
    }

    private fun payload(r: PacketReader, type: Int): Any? = when (type) {
        TAG_BYTE -> r.readByte()
        TAG_SHORT -> r.readShort()
        TAG_INT -> r.readInt()
        TAG_LONG -> r.readLong()
        TAG_FLOAT -> r.readFloat()
        TAG_DOUBLE -> r.readDouble()
        TAG_BYTE_ARRAY -> r.readByteArray(1 shl 20)
        TAG_STRING -> r.readString(1 shl 20)
        TAG_LIST -> {
            val itemType = r.readUnsignedByte()
            val len = r.readInt().coerceIn(0, 1024)
            val items = ArrayList<Any?>(len)
            repeat(len) { items.add(if (itemType == TAG_END) null else payload(r, itemType)) }
            items
        }
        TAG_COMPOUND -> {
            val map = LinkedHashMap<String, Any?>()
            while (true) {
                val childType = r.readUnsignedByte()
                if (childType == TAG_END) break
                val name = r.readString(1 shl 16)
                map[name] = payload(r, childType)
            }
            map
        }
        TAG_INT_ARRAY -> {
            val len = r.readInt().coerceIn(0, 1024)
            IntArray(len) { r.readInt() }
        }
        TAG_LONG_ARRAY -> {
            val len = r.readInt().coerceIn(0, 1024)
            LongArray(len) { r.readLong() }
        }
        else -> throw ProtocolException("unknown NBT tag $type")
    }
}
