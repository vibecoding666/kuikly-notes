package com.example.kuiklynotes

/**
 * Tiny dependency-free JSON codec for the note list. Only the shapes used by
 * this app are supported, which keeps the APK small and avoids pulling in a
 * serialization runtime.
 */
object JsonCodec {

    fun encode(notes: List<Note>): String {
        val sb = StringBuilder()
        sb.append('[')
        notes.forEachIndexed { index, n ->
            if (index > 0) sb.append(',')
            sb.append('{')
            field(sb, "id", n.id)
            field(sb, "title", n.title)
            field(sb, "content", n.content)
            sb.append("\"tags\":")
            stringArray(sb, n.tags)
            sb.append(',')
            num(sb, "color", n.color.toLong())
            bool(sb, "pinned", n.pinned)
            bool(sb, "starred", n.starred)
            bool(sb, "archived", n.archived)
            bool(sb, "deleted", n.deleted)
            sb.append("\"checklist\":[")
            n.checklist.forEachIndexed { i, c ->
                if (i > 0) sb.append(',')
                sb.append('{')
                field(sb, "text", c.text)
                sb.append("\"done\":").append(if (c.done) "true" else "false")
                sb.append('}')
            }
            sb.append("],")
            num(sb, "createdAt", n.createdAt)
            num(sb, "updatedAt", n.updatedAt)
            num(sb, "reminderAt", n.reminderAt)
            sb.append('}')
        }
        sb.append(']')
        return sb.toString()
    }

    fun decode(json: String): List<Note> {
        val p = Parser(json)
        val result = mutableListOf<Note>()
        p.ws()
        if (p.peek() != '[') return result
        p.next()
        p.ws()
        while (p.peek() != ']' && !p.end()) {
            if (p.peek() == ',') { p.next(); p.ws(); continue }
            p.ws()
            if (p.peek() != '{') break
            p.next()
            val map = p.readObject()
            result.add(fromMap(map))
            p.ws()
        }
        return result
    }

    private fun fromMap(m: Map<String, Any?>): Note {
        val tags = (m["tags"] as? List<*>)?.map { it.toString() }?.toMutableList() ?: mutableListOf()
        val checks = mutableListOf<CheckItem>()
        (m["checklist"] as? List<*>)?.forEach { raw ->
            @Suppress("UNCHECKED_CAST")
            val cm = raw as? Map<String, Any?> ?: return@forEach
            checks.add(CheckItem(cm["text"].toString(), cm["done"] == true || cm["done"] == "true"))
        }
        return Note(
            id = m["id"].toString(),
            title = m["title"]?.toString() ?: "",
            content = m["content"]?.toString() ?: "",
            tags = tags,
            color = (m["color"] as? Number)?.toInt() ?: 0,
            pinned = m["pinned"] == true || m["pinned"] == "true",
            starred = m["starred"] == true || m["starred"] == "true",
            archived = m["archived"] == true || m["archived"] == "true",
            deleted = m["deleted"] == true || m["deleted"] == "true",
            checklist = checks,
            createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: 0L,
            reminderAt = (m["reminderAt"] as? Number)?.toLong() ?: 0L,
        )
    }

    private fun field(sb: StringBuilder, name: String, value: String) {
        sb.append('"').append(name).append("\":")
        quote(sb, value)
        sb.append(',')
    }

    private fun num(sb: StringBuilder, name: String, value: Long) {
        sb.append('"').append(name).append("\":").append(value).append(',')
    }

    private fun bool(sb: StringBuilder, name: String, value: Boolean) {
        sb.append('"').append(name).append("\":").append(if (value) "true" else "false").append(',')
    }

    private fun stringArray(sb: StringBuilder, values: List<String>) {
        sb.append('[')
        values.forEachIndexed { i, v ->
            if (i > 0) sb.append(',')
            quote(sb, v)
        }
        sb.append(']')
    }

    private fun quote(sb: StringBuilder, value: String) {
        sb.append('"')
        for (c in value) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20) sb.append("\\u").append(c.code.toString(16).padStart(4, '0')) else sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Parser(private val s: String) {
        private var i = 0
        fun end(): Boolean = i >= s.length
        fun peek(): Char = if (i < s.length) s[i] else '\u0000'
        fun next(): Char = s[i++]
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }

        fun readObject(): Map<String, Any?> {
            val map = mutableMapOf<String, Any?>()
            ws()
            while (!end()) {
                ws()
                if (peek() == '}') { next(); break }
                if (peek() == ',') { next(); continue }
                val key = readString()
                ws()
                if (peek() == ':') next()
                map[key] = readValue()
            }
            return map
        }

        private fun readValue(): Any? {
            ws()
            return when (peek()) {
                '"' -> readString()
                '{' -> { next(); readObject() }
                '[' -> readArray()
                't' -> { skipWord("true"); true }
                'f' -> { skipWord("false"); false }
                'n' -> { skipWord("null"); null }
                else -> readNumber()
            }
        }

        private fun readArray(): List<Any?> {
            next()
            val list = mutableListOf<Any?>()
            while (!end()) {
                ws()
                if (peek() == ']') { next(); break }
                if (peek() == ',') { next(); continue }
                list.add(readValue())
            }
            return list
        }

        private fun readString(): String {
            ws()
            if (peek() != '"') return ""
            next()
            val sb = StringBuilder()
            while (!end()) {
                val c = next()
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (end()) break
                        when (val e = next()) {
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                if (i + 4 <= s.length) {
                                    val hex = s.substring(i, i + 4)
                                    i += 4
                                    hex.toIntOrNull(16)?.let { sb.append(it.toChar()) }
                                }
                            }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
            return sb.toString()
        }

        private fun readNumber(): Any? {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '-' || s[i] == '+' || s[i] == '.')) i++
            val raw = s.substring(start, i)
            return raw.toLongOrNull() ?: raw.toDoubleOrNull() ?: 0L
        }

        private fun skipWord(word: String) {
            if (s.startsWith(word, i)) i += word.length else next()
        }
    }
}
