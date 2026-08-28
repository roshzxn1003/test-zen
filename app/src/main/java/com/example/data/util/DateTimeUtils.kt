package com.example.data.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object IsoDateUtils {
    private val isoFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private val fallbackPatterns = arrayOf(
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )

    fun toIsoString(epochMillis: Long): String {
        return try {
            isoFormat.get()?.format(Date(epochMillis)) ?: Date(epochMillis).toString()
        } catch (_: Exception) {
            Date(epochMillis).toString()
        }
    }

    fun fromIsoString(isoString: String?): Long {
        if (isoString.isNullOrBlank()) return System.currentTimeMillis()
        try {
            val parsed = isoFormat.get()?.parse(isoString)
            if (parsed != null) return parsed.time
        } catch (_: Exception) {}

        for (pattern in fallbackPatterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = sdf.parse(isoString)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {}
        }
        return System.currentTimeMillis()
    }
}
