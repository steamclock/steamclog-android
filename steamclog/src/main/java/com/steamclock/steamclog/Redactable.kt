package com.steamclock.steamclog

import java.lang.reflect.InvocationTargetException
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible


/**
 * getRedactedDescription Iterates over all class properties and generates a string for us to
 * send for analytics/logging purposes which utilizes our Redactable interface to determine which
 * property values are safe to print and which should be redacted from the output.
 *
 * This never throws (#146): an object that was already described is printed as a placeholder,
 * so cyclic references terminate, and a failure while describing an object (reflection, a
 * throwing getter) is printed in place of that object's description.
 */
fun <T : Any> T.getRedactedDescription(): String = runCatching {
    // If a class does not implement Redactable, this boolean allows us to control if we default
    // show or redact the properties of those classes. This enables us to turn on app-wide redaction
    // for all classes.
    getRedactedDescription(SteamcLog.config.requireRedacted)
}.getOrElse { describeFailure(this, it) }

internal fun Any.getRedactedDescription(redactedRequired: Boolean): String = when {
    // A leaf value logged directly cannot implement Redactable, so redactedRequired alone decides
    // whether its value is shown, the same rule a leaf property of a non-Redactable class follows.
    isLeafValue() -> if (redactedRequired) "<redacted>" else toString()
    else -> describe(this, redactedRequired, Collections.newSetFromMap(IdentityHashMap()))
}

private fun describe(obj: Any, redactedRequired: Boolean, visited: MutableSet<Any>): String {
    // Track visited objects by identity, so a cycle (or a repeated reference) is not described twice.
    if (!visited.add(obj)) return "<visited ${obj.javaClass.simpleName}>"

    return runCatching {
        val clazz = obj.javaClass.kotlin
        val clazzName = obj.javaClass.simpleName
        val redactable = obj as? Redactable
        val safeProperties = redactable?.safeProperties

        val params = clazz.declaredMemberProperties
            .filter { it.name != "safeProperties" }
            .map { property ->
                // Enable us to access private variables.
                property.isAccessible = true

                // If class is not redactable, use redactedRequired bool to determine if we want to show/redact the value.
                val showValue = safeProperties?.contains(property.name) ?: !redactedRequired

                if (showValue) {
                    val value = property.get(obj)
                    // Don't recursively call getRedactedDescription on primitives/Strings and other leaf values.
                    // If not dealing with a leaf value, then we may need to recurse down to get full description.
                    val description = if (value == null || value.isLeafValue()) {
                        value.toString()
                    } else {
                        describe(value, redactedRequired, visited)
                    }
                    "${property.name}=$description"
                } else {
                    "${property.name}=<redacted>"
                }
            }

        "${clazzName}(${params.joinToString(", ")})"
    }.getOrElse { describeFailure(obj, it) }
}

/**
 * Values printed with toString() rather than described property by property. Their declared Kotlin
 * properties do not hold their value (boxed primitives and enums have none), so recursing prints
 * `Integer()` or `Kind()`.
 */
private fun Any.isLeafValue(): Boolean =
    this is Number || this is Boolean || this is Char || this is CharSequence || this is Enum<*>

private fun describeFailure(obj: Any, error: Throwable): String {
    // Reflection wraps an exception thrown by a getter; name the getter's exception instead.
    val cause = (error as? InvocationTargetException)?.targetException ?: error
    return "${obj.javaClass.simpleName}(<description failed: ${cause.javaClass.simpleName}>)"
}

/**
 * Redactable
 *
 * Created by shayla on 2020-01-23
 *
 * If application using Proguard or R8 to do code obfuscation then the following must be added to the
 * proguard-rules.pro file to enable the redaction to correctly work with the safeProperties set:
 *   -keep class * extends com.steamclock.steamclog.Redactable { *; }
 */

interface Redactable {
    // Opt-in set of all property names that are considered "safe" to print.
    val safeProperties: Set<String>
}