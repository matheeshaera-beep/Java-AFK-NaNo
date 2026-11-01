package dev.mstheesha.afk.engine

/** Bounded ring buffer used for both the log and the chat tab. */
internal class LogRing(private val capacity: Int) {
    private val items = ArrayDeque<String>(capacity)

    fun add(line: String) {
        if (items.size == capacity) items.removeFirst()
        items.addLast(line)
    }

    fun clear() = items.clear()

    fun shrink() {
        while (items.size > capacity) items.removeFirst()
    }

    fun toList(): List<String> = items.toList()

    fun size(): Int = items.size
}
