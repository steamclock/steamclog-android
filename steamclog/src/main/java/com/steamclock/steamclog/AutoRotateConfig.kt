package com.steamclock.steamclog

/**
 * Config
 *
 * Created by jake on 2021-09-01
 */
data class AutoRotateConfig(
    /**
     * The number of seconds before a log file is rotated
     */
    val fileRotationSeconds: Long = 600 // matching iOS
)