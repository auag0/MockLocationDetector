package io.github.auag0.mocklocationdetector

object AnyUtils {
    fun Any?.safeString(): String {
        return toString()
    }
}