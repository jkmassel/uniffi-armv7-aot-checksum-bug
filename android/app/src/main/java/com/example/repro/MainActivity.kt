package com.example.repro

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import uniffi.uniffi_repro.uniffiEnsureInitialized
import uniffi.uniffi_repro.getShortHighBit
import uniffi.uniffi_repro.getSignedShortHighBit
import uniffi.uniffi_repro.getByteHighBit
import uniffi.uniffi_repro.getSignedByteHighBit
import uniffi.uniffi_repro.getBoolTrue
import uniffi.uniffi_repro.getBoolFalse
import uniffi.uniffi_repro.getI16NegMin
import uniffi.uniffi_repro.getI16NegOne
import uniffi.uniffi_repro.getI16Neg127
import uniffi.uniffi_repro.getI16Neg257
import uniffi.uniffi_repro.getI16PosMax
import uniffi.uniffi_repro.getI16Zero
import uniffi.uniffi_repro.getI8NegMin
import uniffi.uniffi_repro.getI8NegOne
import uniffi.uniffi_repro.getI8Neg42
import uniffi.uniffi_repro.getI8PosMax
import uniffi.uniffi_repro.getI8Zero

private const val TAG = "AotRepro"

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "abis=${android.os.Build.SUPPORTED_ABIS.joinToString()}")

        val tv = TextView(this).apply {
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
        }
        setContentView(tv)

        val rows = mutableListOf<Row>()
        try {
            uniffiEnsureInitialized()
            Log.i(TAG, "uniffiEnsureInitialized OK")

            // --- u16 returns (0xD2FF) ---
            val u16 = getShortHighBit()                                // UShort
            rows += Row("u16 toInt()",                       "54015",   u16.toInt().toString())
            rows += Row("u16 toShort()",                     "-11521",  u16.toShort().toString())
            rows += Row("u16.toShort() == (-11521).toShort", "true",    (u16.toShort() == (-11521).toShort()).toString())
            rows += Row("u16.toInt() == 54015",              "true",    (u16.toInt() == 54015).toString())

            // --- i16 returns (-11521) ---
            val i16 = getSignedShortHighBit()                          // Short
            rows += Row("i16 toInt()",                       "-11521",  i16.toInt().toString())
            rows += Row("i16 == (-11521).toShort()",         "true",    (i16 == (-11521).toShort()).toString())

            // --- u8 returns (0xAB) ---
            val u8 = getByteHighBit()                                  // UByte
            rows += Row("u8 toInt()",                        "171",     u8.toInt().toString())
            rows += Row("u8 toByte()",                       "-85",     u8.toByte().toString())
            rows += Row("u8.toByte() == (-85).toByte()",     "true",    (u8.toByte() == (-85).toByte()).toString())
            rows += Row("u8.toInt() == 171",                 "true",    (u8.toInt() == 171).toString())

            // --- i8 returns (-85) ---
            val i8 = getSignedByteHighBit()                            // Byte
            rows += Row("i8 toInt()",                        "-85",     i8.toInt().toString())
            rows += Row("i8 == (-85).toByte()",              "true",    (i8 == (-85).toByte()).toString())

            // --- bool returns ---
            val bt = getBoolTrue()
            val bf = getBoolFalse()
            rows += Row("bool true",                         "true",    bt.toString())
            rows += Row("bool false",                        "false",   bf.toString())

            // --- i16 stress (signed sub-word, NOT widened by PR #2897) ---
            rows += Row("i16 -32768 (0x8000) toInt",         "-32768",  getI16NegMin().toInt().toString())
            rows += Row("i16 -1 (0xFFFF) toInt",             "-1",      getI16NegOne().toInt().toString())
            rows += Row("i16 -127 (0xFF81) toInt",           "-127",    getI16Neg127().toInt().toString())
            rows += Row("i16 -257 (0xFEFF) toInt",           "-257",    getI16Neg257().toInt().toString())
            rows += Row("i16 32767 (0x7FFF) toInt",          "32767",   getI16PosMax().toInt().toString())
            rows += Row("i16 0 toInt",                       "0",       getI16Zero().toInt().toString())

            // --- i8 stress (signed sub-word, NOT widened by PR #2897) ---
            rows += Row("i8 -128 (0x80) toInt",              "-128",    getI8NegMin().toInt().toString())
            rows += Row("i8 -1 (0xFF) toInt",                "-1",      getI8NegOne().toInt().toString())
            rows += Row("i8 -42 (0xD6) toInt",               "-42",     getI8Neg42().toInt().toString())
            rows += Row("i8 127 (0x7F) toInt",               "127",     getI8PosMax().toInt().toString())
            rows += Row("i8 0 toInt",                        "0",       getI8Zero().toInt().toString())

        } catch (t: Throwable) {
            Log.e(TAG, "FAILED", t)
            rows += Row("uniffiEnsureInitialized",           "OK",      "${t.javaClass.simpleName}: ${t.message}")
        }

        val output = formatTable(rows)
        Log.i(TAG, "results:\n$output")
        tv.text = output
    }

    private data class Row(val name: String, val expected: String, val actual: String) {
        val pass: Boolean get() = expected == actual
    }

    private fun formatTable(rows: List<Row>): String {
        if (rows.isEmpty()) return ""
        val w1 = maxOf(rows.maxOf { it.name.length }, "Case".length)
        val w2 = maxOf(rows.maxOf { it.expected.length }, "Expected".length)
        val w3 = maxOf(rows.maxOf { it.actual.length }, "Actual".length)
        val sb = StringBuilder()
        sb.append(String.format("%-${w1}s  %-${w2}s  %-${w3}s  %s", "Case", "Expected", "Actual", "PASS"))
        sb.append('\n')
        sb.append("-".repeat(w1 + w2 + w3 + 10)).append('\n')
        var fails = 0
        for (r in rows) {
            val mark = if (r.pass) "✓" else "✗"
            if (!r.pass) fails++
            sb.append(String.format("%-${w1}s  %-${w2}s  %-${w3}s  %s", r.name, r.expected, r.actual, mark))
            sb.append('\n')
        }
        sb.append("\n${rows.size - fails}/${rows.size} pass").append(if (fails > 0) " — $fails FAIL" else "")
        return sb.toString()
    }
}
