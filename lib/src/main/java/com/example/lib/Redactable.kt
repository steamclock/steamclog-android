package com.example.lib

import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField


/**
 * getRedactedDescription Iterates over all class properties and generates a string for us to
 * send for analytics/logging purposes which utilizes our Redactable interface to determine which
 * property values are safe to print and which should be redacted from the output.
 */
fun <T : Any> T.getRedactedDescription(): String {
    // If a class does not implement Redactable, this boolean allows us to control if we default
    // show or redact the properties of those classes. This enables us to turn on app-wide redaction
    // for all classes.
    val redactedRequired = SteamcLog.config.requireRedacted

    val clazz = this.javaClass.kotlin
    val clazzName = this.javaClass.simpleName
    val redactable = this as? Redactable
    val safeProperties = redactable?.safeProperties

    val params = clazz.declaredMemberProperties
        .filter { it.name != "safeProperties" }
        .map { property ->
            // Enable us to access private variables.
            property.isAccessible = true

            // Don't recursively call getRedactedDescription on primatives/Strings
            val isPrimitive = property.javaField?.type?.let { type -> type.isPrimitive || type == String::class.java } ?: run { false }

            // If class is not redactable, use redactedRequired bool to determine if we want to show/redact the value.
            val showValue = safeProperties?.contains(property.name) ?: !redactedRequired

            // If not dealing with a primitive object, then we may need to recurse down to get full description.
            val recurseRequired = !isPrimitive && showValue

            when {
                recurseRequired -> "${property.name}=${property.get(this)?.getRedactedDescription()}"
                showValue -> "${property.name}=${property.get(this)}"
                else -> "${property.name}=<redacted>"
            }
        }

    return "${clazzName}(${params.joinToString(", ")})"
}

/**
 * Redactable
 *
 * Created by shayla on 2020-01-23
 */

interface Redactable {
    // Opt-in "whitelist" of all property names that are considered "safe" to print.
    val safeProperties: Set<String>

    // Commenting out for now, I think our Any() version here is better.
//    fun getDebugDescription(): String {
//        val clazz = this.javaClass.kotlin
//        val clazzName = this.javaClass.simpleName
//        val params = clazz.declaredMemberProperties
//            .filter { it.name != "safeProperties"}
//            .map { property ->
//                // Check if property is a class that also requires its own redaction.
//                val redactable = property.get(this) as? Redactable
//                when {
//                    redactable != null -> "${property.name}=${redactable.getDebugDescription()}"
//                    safeProperties.contains(property.name) -> "${property.name}=${property.get(this)}"
//                    else -> "${property.name}=<redacted>"
//                }
//            }
//
//        return "${clazzName}(${params.joinToString(", ")})"
//    }
}