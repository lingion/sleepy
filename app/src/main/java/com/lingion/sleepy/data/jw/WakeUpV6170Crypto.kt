package com.lingion.sleepy.data.jw

import java.security.MessageDigest
import java.util.Base64

/**
 * WakeUp 分享口令协议的底层字节原语 — 拾光 GLOBAL_TOOLS/wake_up.js v6.1.70 的 Kotlin 等价实现。
 *
 * 出处: shiguang_warehouse `schools/resources/GLOBAL_TOOLS/wake_up.js`（致谢见 LicenseScreen）。
 * 每个函数的输出与 vendor JS 逐字节相等，由 app/src/test/resources/jw/wakeup/vectors.json
 * （直接跑 vendor JS 生成的金标向量）锁定 — 教务跨仓验证 SOP 跨语言 invariant 铁律。
 *
 * 私有 DES 并非标准 DES（IP/FP/E/P/PC1/PC2 表与位序来自 WakeUP v6.1.70 官方协议，
 * PC2 含重复索引，位序 LSB-first），禁止替换为 javax.crypto 标准实现。
 */
internal object WakeUpV6170Crypto {

    fun md5Bytes(text: String): ByteArray =
        MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))

    fun md5Hex(text: String): String = md5Bytes(text).toHexLower()

    fun md5Upper(text: String): String = md5Hex(text).uppercase()

    fun bytesToBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun base64ToBytes(text: String): ByteArray = Base64.getDecoder().decode(text)

    fun bytesToLatin1(bytes: ByteArray): String = String(bytes, Charsets.ISO_8859_1)

    fun bytesToUtf8(bytes: ByteArray): String = String(bytes, Charsets.UTF_8)

    /** JS encodeURIComponent 后按 Android URLEncoder 语义改写: !'()* 转 %XX, %20 转 +。 */
    fun androidQuote(value: String): String = buildString {
        for (ch in value) {
            when {
                ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' ||
                    ch == '-' || ch == '_' || ch == '.' || ch == '~' -> append(ch)
                ch == ' ' -> append('+')
                else -> for (b in ch.toString().toByteArray(Charsets.UTF_8)) {
                    append('%')
                    val v = b.toInt() and 0xFF
                    append(HEX_UPPER[v shr 4]); append(HEX_UPPER[v and 0xF])
                }
            }
        }
    }

    /** vendor 逐字面对应 wakeupFormEncode: 键不编码, 值走 androidQuote, & 连接。 */
    fun formEncode(items: List<Pair<String, String>>): String =
        items.joinToString("&") { (key, value) -> "$key=${androidQuote(value)}" }

    /** 每字节两个 nibble 各自反序后各写 2 位小写十六进制: 4 字符/字节。 */
    fun nativeHexEncode(bytes: ByteArray): String = buildString(bytes.size * 4) {
        for (byte in bytes) {
            val b = byte.toInt() and 0xFF
            append(reverseNibble(b and 0x0F).toHexByte())
            append(reverseNibble((b shr 4) and 0x0F).toHexByte())
        }
    }

    /** 只读每组第 2/4 字符; 尾部不足 4 字符的残组丢弃(与 vendor slice 语义一致)。 */
    fun nativeHexDecode(text: String): ByteArray {
        val usable = text.length / 4 * 4
        return ByteArray(usable / 4) { i ->
            val low = reverseNibble(hexDigit(text[i * 4 + 1]))
            val high = reverseNibble(hexDigit(text[i * 4 + 3]))
            (low or (high shl 4)).toByte()
        }
    }

    private fun reverseNibble(value: Int): Int =
        ((value and 1) shl 3) or ((value and 2) shl 1) or ((value and 4) shr 1) or ((value and 8) shr 3)

    private fun hexDigit(ch: Char): Int = ch.digitToIntOrNull(16) ?: 0

    private fun ByteArray.toHexLower(): String = joinToString("") { (it.toInt() and 0xFF).toHexByte() }

    private fun Int.toHexByte(): String {
        val v = this and 0xFF
        return "${HEX_LOWER[v shr 4]}${HEX_LOWER[v and 0xF]}"
    }

    /** 标准 RC4, KSA 密钥走 UTF-8 字节(vendor wakeupToBytes 对字符串即 TextEncoder)。 */
    fun rc4(data: String, key: String): ByteArray = rc4Raw(data.toByteArray(Charsets.UTF_8), key)

    /** RC4 字节输入版(解密响应 data 段用; KSA/PRGA 与字符串版共享)。 */
    fun rc4Raw(data: ByteArray, key: String): ByteArray {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        val state = ByteArray(256) { it.toByte() }
        var j = 0
        for (i in 0 until 256) {
            j = (j + (state[i].toInt() and 0xFF) + (keyBytes[i % keyBytes.size].toInt() and 0xFF)) and 0xFF
            val tmp = state[i]; state[i] = state[j]; state[j] = tmp
        }
        val input = data
        val output = ByteArray(input.size)
        var x = 0; var y = 0
        for (k in input.indices) {
            x = (x + 1) and 0xFF
            y = (y + (state[x].toInt() and 0xFF)) and 0xFF
            val tmp = state[x]; state[x] = state[y]; state[y] = tmp
            output[k] = (input[k].toInt() xor
                (state[(state[x].toInt() and 0xFF) + (state[y].toInt() and 0xFF) and 0xFF].toInt() and 0xFF)).toByte()
        }
        return output
    }

    private val HEX_LOWER = "0123456789abcdef"
    private val HEX_UPPER = "0123456789ABCDEF"

    // ---- 私有 DES (非标准! 表值与 vendor wake_up.js 逐字相等) ----

    private val DES_IP = intArrayOf(57, 49, 41, 33, 25, 17, 9, 1, 59, 51, 43, 35, 27, 19, 11, 3, 61, 53, 45, 37, 29, 21, 13, 5, 63, 55, 47, 39, 31, 23, 15, 7, 56, 48, 40, 32, 24, 16, 8, 0, 58, 50, 42, 34, 26, 18, 10, 2, 60, 52, 44, 36, 28, 20, 12, 4, 62, 54, 46, 38, 30, 22, 14, 6)
    private val DES_FP = intArrayOf(39, 7, 47, 15, 55, 23, 63, 31, 38, 6, 46, 14, 54, 22, 62, 30, 37, 5, 45, 13, 53, 21, 61, 29, 36, 4, 44, 12, 52, 20, 60, 28, 35, 3, 43, 11, 51, 19, 59, 27, 34, 2, 42, 10, 50, 18, 58, 26, 33, 1, 41, 9, 49, 17, 57, 25, 32, 0, 40, 8, 48, 16, 56, 24)
    private val DES_E = intArrayOf(31, 0, 1, 2, 3, 4, 3, 4, 5, 6, 7, 8, 7, 8, 9, 10, 11, 12, 11, 12, 13, 14, 15, 16, 15, 16, 17, 18, 19, 20, 19, 20, 21, 22, 23, 24, 23, 24, 25, 26, 27, 28, 27, 28, 29, 30, 31, 0)
    private val DES_P = intArrayOf(15, 6, 19, 20, 28, 11, 27, 16, 0, 14, 22, 25, 4, 17, 30, 9, 1, 7, 23, 13, 31, 26, 2, 8, 18, 12, 29, 5, 21, 10, 3, 24)
    private val DES_PC1 = intArrayOf(56, 48, 40, 32, 24, 16, 8, 0, 57, 49, 41, 33, 25, 17, 9, 1, 58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35, 62, 54, 46, 38, 30, 22, 14, 6, 61, 53, 45, 37, 29, 21, 13, 5, 60, 52, 44, 36, 28, 20, 12, 4, 27, 19, 11, 3)
    private val DES_PC2 = intArrayOf(13, 16, 10, 23, 0, 4, 2, 27, 14, 5, 20, 9, 22, 18, 11, 3, 25, 7, 15, 6, 26, 19, 12, 1, 40, 51, 30, 36, 46, 54, 29, 39, 50, 44, 32, 46, 43, 48, 38, 55, 33, 52, 45, 41, 49, 35, 28, 31)
    private val DES_SHIFTS = intArrayOf(1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1)
    private val DES_SBOX = arrayOf(
        arrayOf(intArrayOf(14,4,13,1,2,15,11,8,3,10,6,12,5,9,0,7), intArrayOf(0,15,7,4,14,2,13,1,10,6,12,11,9,5,3,8), intArrayOf(4,1,14,8,13,6,2,11,15,12,9,7,3,10,5,0), intArrayOf(15,12,8,2,4,9,1,7,5,11,3,14,10,0,6,13)),
        arrayOf(intArrayOf(15,1,8,14,6,11,3,4,9,7,2,13,12,0,5,10), intArrayOf(3,13,4,7,15,2,8,14,12,0,1,10,6,9,11,5), intArrayOf(0,14,7,11,10,4,13,1,5,8,12,6,9,3,2,15), intArrayOf(13,8,10,1,3,15,4,2,11,6,7,12,0,5,14,9)),
        arrayOf(intArrayOf(10,0,9,14,6,3,15,5,1,13,12,7,11,4,2,8), intArrayOf(13,7,0,9,3,4,6,10,2,8,5,14,12,11,15,1), intArrayOf(13,6,4,9,8,15,3,0,11,1,2,12,5,10,14,7), intArrayOf(1,10,13,0,6,9,8,7,4,15,14,3,11,5,2,12)),
        arrayOf(intArrayOf(7,13,14,3,0,6,9,10,1,2,8,5,11,12,4,15), intArrayOf(13,8,11,5,6,15,0,3,4,7,2,12,1,10,14,9), intArrayOf(10,6,9,0,12,11,7,13,15,1,3,14,5,2,8,4), intArrayOf(3,15,0,6,10,1,13,8,9,4,5,11,12,7,2,14)),
        arrayOf(intArrayOf(2,12,4,1,7,10,11,6,8,5,3,15,13,0,14,9), intArrayOf(14,11,2,12,4,7,13,1,5,0,15,10,3,9,8,6), intArrayOf(4,2,1,11,10,13,7,8,15,9,12,5,6,3,0,14), intArrayOf(11,8,12,7,1,14,2,13,6,15,0,9,10,4,5,3)),
        arrayOf(intArrayOf(12,1,10,15,9,2,6,8,0,13,3,4,14,7,5,11), intArrayOf(10,15,4,2,7,12,9,5,6,1,13,14,0,11,3,8), intArrayOf(9,14,15,5,2,8,12,3,7,0,4,10,1,13,11,6), intArrayOf(4,3,2,12,9,5,15,10,11,14,1,7,6,0,8,13)),
        arrayOf(intArrayOf(4,11,2,14,15,0,8,13,3,12,9,7,5,10,6,1), intArrayOf(13,0,11,7,4,9,1,10,14,3,5,12,2,15,8,6), intArrayOf(1,4,11,13,12,3,7,14,10,15,6,8,0,5,9,2), intArrayOf(6,11,13,8,1,4,10,7,9,5,0,15,14,2,3,12)),
        arrayOf(intArrayOf(13,2,8,4,6,15,11,1,10,9,3,14,5,0,12,7), intArrayOf(1,15,13,8,10,3,7,4,12,5,6,11,0,14,9,2), intArrayOf(7,11,4,1,9,12,14,2,0,6,10,13,15,3,5,8), intArrayOf(2,1,14,7,4,10,8,13,15,12,9,0,3,5,6,11))
    )

    private fun bitsFromBytes(bytes: ByteArray): IntArray =
        IntArray(bytes.size * 8) { i -> (bytes[i / 8].toInt() shr (i % 8)) and 1 }

    private fun bytesFromBits(bits: IntArray): ByteArray =
        ByteArray(bits.size / 8) { i ->
            var v = 0
            for (b in 0 until 8) v = v or ((bits[i * 8 + b] and 1) shl b)
            v.toByte()
        }

    private fun permute(bits: IntArray, table: IntArray): IntArray =
        IntArray(table.size) { i -> bits[table[i]] and 1 }

    private fun subkeys(key: String): List<IntArray> {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        check(keyBytes.size == 8) { "WakeUP 私有 DES 密钥长度必须为 8 字节。" }
        val scheduled = permute(bitsFromBytes(keyBytes), DES_PC1)
        var left = scheduled.copyOfRange(0, 28)
        var right = scheduled.copyOfRange(28, 56)
        return DES_SHIFTS.map { shift ->
            left = rotateLeftBits(left, shift)
            right = rotateLeftBits(right, shift)
            permute(left + right, DES_PC2)
        }
    }

    private fun rotateLeftBits(bits: IntArray, shift: Int): IntArray =
        bits.copyOfRange(shift, bits.size) + bits.copyOfRange(0, shift)

    private fun desFunction(right: IntArray, subkey: IntArray): IntArray {
        val expanded = permute(right, DES_E)
        val mixed = IntArray(48) { i -> expanded[i] xor subkey[i] }
        val output = IntArray(32)
        for (box in 0 until 8) {
            val row = mixed[box * 6] * 2 + mixed[box * 6 + 5]
            val column = mixed[box * 6 + 1] * 8 + mixed[box * 6 + 2] * 4 +
                mixed[box * 6 + 3] * 2 + mixed[box * 6 + 4]
            val value = DES_SBOX[box][row][column]
            output[box * 4] = (value shr 3) and 1
            output[box * 4 + 1] = (value shr 2) and 1
            output[box * 4 + 2] = (value shr 1) and 1
            output[box * 4 + 3] = value and 1
        }
        return permute(output, DES_P)
    }

    private fun desBlock(block: ByteArray, keys: List<IntArray>): ByteArray {
        val bits = permute(bitsFromBytes(block), DES_IP)
        var left = bits.copyOfRange(0, 32)
        var right = bits.copyOfRange(32, 64)
        for (subkey in keys) {
            val mixed = desFunction(right, subkey)
            val nextRight = IntArray(32) { i -> left[i] xor mixed[i] }
            left = right
            right = nextRight
        }
        return bytesFromBits(permute(right + left, DES_FP))
    }

    /** 填充: 补齐到 8 的倍数(整块也补满一块), 末字节 = 填充长度。 */
    fun desEncrypt(plain: String, key: String): ByteArray {
        val source = plain.toByteArray(Charsets.UTF_8)
        val paddedLength = (source.size / 8 * 8) + 8
        val padded = ByteArray(paddedLength).also { source.copyInto(it) }
        padded[paddedLength - 1] = (paddedLength - source.size).toByte()
        val keys = subkeys(key)
        val out = ByteArray(paddedLength)
        for (offset in 0 until paddedLength step 8) {
            desBlock(padded.copyOfRange(offset, offset + 8), keys)
                .copyInto(out, offset)
        }
        return out
    }

    fun desDecrypt(cipher: ByteArray, key: String): ByteArray {
        check(cipher.size % 8 == 0) { "WakeUP 私有 DES 密文长度无效。" }
        val keys = subkeys(key).reversed()
        val plain = ByteArray(cipher.size)
        for (offset in cipher.indices step 8) {
            desBlock(cipher.copyOfRange(offset, offset + 8), keys)
                .copyInto(plain, offset)
        }
        val padLength = plain[plain.size - 1].toInt() and 0xFF
        check(padLength in 1..plain.size) { "WakeUP 私有 DES 填充无效。" }
        return plain.copyOfRange(0, plain.size - padLength)
    }
}

internal fun ByteArray.toHexLowerString(): String =
    joinToString("") { ((it.toInt() and 0xFF).toString(16)).padStart(2, '0') }
