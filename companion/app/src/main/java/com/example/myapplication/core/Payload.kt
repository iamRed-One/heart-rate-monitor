package com.example.myapplication.core

import android.util.Base64
import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
import com.google.gson.reflect.TypeToken
import java.util.TreeMap

/**
 * Turns vendor values (SDK model objects, HashMaps, byte/int arrays) into plain, JSON-safe trees
 * with sorted keys, so the same datum always serialises to the same bytes (stable record ids).
 */
object Payload {

    private val gson = GsonBuilder()
        .serializeSpecialFloatingPointValues()
        .disableHtmlEscaping()
        .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)   // keep 72 as 72, not 72.0
        .create()
    private val mapType = object : TypeToken<Map<String, Any?>>() {}.type

    /** Dump an SDK model object with every field it has (no hand-picked subset). */
    fun ofObject(obj: Any?): Map<String, Any?> {
        if (obj == null) return emptyMap()
        val tree: Map<String, Any?> = runCatching { gson.fromJson<Map<String, Any?>>(gson.toJson(obj), mapType) }
            .getOrNull() ?: mapOf("toString" to obj.toString())
        return sanitize(tree) as Map<String, Any?>
    }

    /** Normalise a vendor map/list/array tree: string keys, sorted, arrays → lists, bytes → base64. */
    fun sanitize(v: Any?): Any? = when (v) {
        null -> null
        is ByteArray -> mapOf("base64" to Base64.encodeToString(v, Base64.NO_WRAP), "length" to v.size)
        is IntArray -> v.toList()
        is LongArray -> v.toList()
        is FloatArray -> v.toList()
        is DoubleArray -> v.toList()
        is ShortArray -> v.toList()
        is BooleanArray -> v.toList()
        is Array<*> -> v.map { sanitize(it) }
        is Map<*, *> -> TreeMap<String, Any?>().apply { v.forEach { (k, x) -> put(k.toString(), sanitize(x)) } }
        is Iterable<*> -> v.map { sanitize(it) }
        is Number, is Boolean, is String -> v
        is Enum<*> -> v.name
        else -> ofObject(v)
    }

    fun toJson(v: Any?): String = gson.toJson(sanitize(v))
}
