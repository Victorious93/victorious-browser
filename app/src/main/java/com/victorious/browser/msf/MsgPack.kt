package com.victorious.browser.msf

import java.io.ByteArrayOutputStream

/**
 * Minimal, dependency-free MessagePack encoder/decoder — just enough for the
 * Metasploit msgrpc interface (nil/bool/int/float/str/bin/array/map). Hand-rolled
 * (rather than pulling in a library) and unit-tested (see MsgPackTest).
 *
 * Encoding of Kotlin types:
 *  - null -> nil, Boolean -> bool, Int/Long -> int family, Float/Double -> float,
 *    String -> str (UTF-8), ByteArray -> bin, List<*> -> array, Map<*,*> -> map.
 *
 * Decoding produces: null, Boolean, Long (all ints), Double (all floats), String
 * (str), ByteArray (bin), List<Any?> (array), LinkedHashMap<Any?,Any?> (map).
 * Map keys are decoded as-is (str -> String), matching MSF's string-keyed maps.
 */
object MsgPack {

    // ---- Encode ----

    fun encode(value: Any?): ByteArray {
        val out = ByteArrayOutputStream()
        writeValue(out, value)
        return out.toByteArray()
    }

    private fun writeValue(out: ByteArrayOutputStream, value: Any?) {
        when (value) {
            null -> out.write(0xc0)
            is Boolean -> out.write(if (value) 0xc3 else 0xc2)
            is Byte -> writeInt(out, value.toLong())
            is Short -> writeInt(out, value.toLong())
            is Int -> writeInt(out, value.toLong())
            is Long -> writeInt(out, value)
            is Float -> writeDouble(out, value.toDouble())
            is Double -> writeDouble(out, value)
            is String -> writeString(out, value)
            is ByteArray -> writeBinary(out, value)
            is Map<*, *> -> writeMap(out, value)
            is List<*> -> writeArray(out, value)
            is Array<*> -> writeArray(out, value.toList())
            else -> throw IllegalArgumentException("Unsupported msgpack type: ${value.javaClass}")
        }
    }

    private fun writeInt(out: ByteArrayOutputStream, v: Long) {
        when {
            v in 0..0x7f -> out.write(v.toInt())
            v in -32..-1 -> out.write((0xe0 or (v.toInt() and 0x1f)))
            v in 0..0xff -> { out.write(0xcc); out.write(v.toInt() and 0xff) }
            v in 0..0xffff -> { out.write(0xcd); writeBE(out, v, 2) }
            v in 0..0xffffffffL -> { out.write(0xce); writeBE(out, v, 4) }
            v >= 0 -> { out.write(0xcf); writeBE(out, v, 8) }
            v in -128..127 -> { out.write(0xd0); out.write(v.toInt() and 0xff) }
            v in -32768..32767 -> { out.write(0xd1); writeBE(out, v, 2) }
            v in -2147483648..2147483647 -> { out.write(0xd2); writeBE(out, v, 4) }
            else -> { out.write(0xd3); writeBE(out, v, 8) }
        }
    }

    private fun writeDouble(out: ByteArrayOutputStream, v: Double) {
        out.write(0xcb)
        writeBE(out, java.lang.Double.doubleToLongBits(v), 8)
    }

    private fun writeString(out: ByteArrayOutputStream, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        val n = bytes.size
        when {
            n < 32 -> out.write(0xa0 or n)
            n < 256 -> { out.write(0xd9); out.write(n) }
            n < 65536 -> { out.write(0xda); writeBE(out, n.toLong(), 2) }
            else -> { out.write(0xdb); writeBE(out, n.toLong(), 4) }
        }
        out.write(bytes)
    }

    private fun writeBinary(out: ByteArrayOutputStream, b: ByteArray) {
        val n = b.size
        when {
            n < 256 -> { out.write(0xc4); out.write(n) }
            n < 65536 -> { out.write(0xc5); writeBE(out, n.toLong(), 2) }
            else -> { out.write(0xc6); writeBE(out, n.toLong(), 4) }
        }
        out.write(b)
    }

    private fun writeArray(out: ByteArrayOutputStream, list: List<*>) {
        val n = list.size
        when {
            n < 16 -> out.write(0x90 or n)
            n < 65536 -> { out.write(0xdc); writeBE(out, n.toLong(), 2) }
            else -> { out.write(0xdd); writeBE(out, n.toLong(), 4) }
        }
        list.forEach { writeValue(out, it) }
    }

    private fun writeMap(out: ByteArrayOutputStream, map: Map<*, *>) {
        val n = map.size
        when {
            n < 16 -> out.write(0x80 or n)
            n < 65536 -> { out.write(0xde); writeBE(out, n.toLong(), 2) }
            else -> { out.write(0xdf); writeBE(out, n.toLong(), 4) }
        }
        map.forEach { (k, v) ->
            writeValue(out, k)
            writeValue(out, v)
        }
    }

    private fun writeBE(out: ByteArrayOutputStream, v: Long, bytes: Int) {
        for (i in bytes - 1 downTo 0) {
            out.write(((v ushr (i * 8)) and 0xff).toInt())
        }
    }

    // ---- Decode ----

    fun decode(data: ByteArray): Any? {
        val r = Reader(data)
        return r.read()
    }

    private class Reader(private val d: ByteArray) {
        private var pos = 0

        fun read(): Any? {
            val b = u8()
            return when {
                b <= 0x7f -> b.toLong()
                b >= 0xe0 -> (b - 0x100).toLong()
                b in 0x80..0x8f -> readMap(b and 0x0f)
                b in 0x90..0x9f -> readArray(b and 0x0f)
                b in 0xa0..0xbf -> readStr(b and 0x1f)
                else -> when (b) {
                    0xc0 -> null
                    0xc2 -> false
                    0xc3 -> true
                    0xc4 -> readBin(u8())
                    0xc5 -> readBin(be(2).toInt())
                    0xc6 -> readBin(be(4).toInt())
                    0xca -> java.lang.Float.intBitsToFloat(be(4).toInt()).toDouble()
                    0xcb -> java.lang.Double.longBitsToDouble(be(8))
                    0xcc -> be(1)
                    0xcd -> be(2)
                    0xce -> be(4)
                    0xcf -> be(8) // may be negative if top bit set; MSF values fit
                    0xd0 -> s8()
                    0xd1 -> sbe(2)
                    0xd2 -> sbe(4)
                    0xd3 -> be(8)
                    0xd9 -> readStr(u8())
                    0xda -> readStr(be(2).toInt())
                    0xdb -> readStr(be(4).toInt())
                    0xdc -> readArray(be(2).toInt())
                    0xdd -> readArray(be(4).toInt())
                    0xde -> readMap(be(2).toInt())
                    0xdf -> readMap(be(4).toInt())
                    else -> throw IllegalArgumentException("Unknown msgpack byte: 0x${b.toString(16)}")
                }
            }
        }

        private fun u8(): Int = d[pos++].toInt() and 0xff
        private fun s8(): Long = d[pos++].toLong()

        private fun be(n: Int): Long {
            var v = 0L
            repeat(n) { v = (v shl 8) or (d[pos++].toLong() and 0xff) }
            return v
        }

        private fun sbe(n: Int): Long {
            var v = 0L
            repeat(n) { v = (v shl 8) or (d[pos++].toLong() and 0xff) }
            val bits = n * 8
            val shift = 64 - bits
            return (v shl shift) shr shift
        }

        private fun readStr(n: Int): String {
            val s = String(d, pos, n, Charsets.UTF_8)
            pos += n
            return s
        }

        private fun readBin(n: Int): ByteArray {
            val b = d.copyOfRange(pos, pos + n)
            pos += n
            return b
        }

        private fun readArray(n: Int): List<Any?> {
            val list = ArrayList<Any?>(n)
            repeat(n) { list.add(read()) }
            return list
        }

        private fun readMap(n: Int): LinkedHashMap<Any?, Any?> {
            val m = LinkedHashMap<Any?, Any?>(n)
            repeat(n) {
                val k = read()
                val v = read()
                m[k] = v
            }
            return m
        }
    }
}
