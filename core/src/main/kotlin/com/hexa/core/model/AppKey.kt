package com.hexa.core.model

/**
 * Stable identifier for a launchable activity: `package/activityClass` plus `#serial` for
 * non-primary user profiles (work profile). Survives app updates as long as the activity name stays.
 */
object AppKey {
    fun of(packageName: String, className: String, userSerial: Long = 0): String =
        if (userSerial == 0L) "$packageName/$className" else "$packageName/$className#$userSerial"

    fun packageOf(key: String): String = key.substringBefore('/')

    fun classOf(key: String): String = key.substringAfter('/').substringBefore('#')

    fun userSerialOf(key: String): Long =
        key.substringAfter('#', missingDelimiterValue = "0").toLongOrNull() ?: 0L
}
