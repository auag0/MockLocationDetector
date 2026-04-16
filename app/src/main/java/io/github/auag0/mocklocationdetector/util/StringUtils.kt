package io.github.auag0.mocklocationdetector.util

object StringUtils {
    fun Any?.safeString(): String {
        return toString()
    }
}