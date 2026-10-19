package com.stitchdb.logging

import com.stitchdb.StitchClient
import com.stitchdb.StitchLoggingProcessorFactory
import com.stitchdb.plugins.MainConfig

/**
 * An interface for logging in StitchDB plugins.
 * @param level The minimum log level to handle for this logger. If null, [StitchClient.DEFAULT_LOG_LEVEL] will be used.
 * @param processor The logging processor used for the actual logging
 */
class StitchLogger(
    val level: LogLevel,
    val tag: String,
    processorFactory: StitchLoggingProcessorFactory
) {

    val processor = processorFactory(level)

    /**
     * Log a message with the given [level] and [message]. An optional [throwable] can be provided.
     * @param level The log level
     * @param throwable An optional throwable
     * @param message The message to log
     */
    fun log(level: LogLevel, throwable: Throwable? = null, message: String) = processor.log(level, tag, throwable) {
        message
    }

    /**
     * Log a message with the given [level] and [message]. An optional [throwable] can be provided.
     * @param level The log level
     * @param throwable An optional throwable
     * @param message The message to log
     */
    inline fun log(level: LogLevel, throwable: Throwable? = null, message: () -> String) = processor.log(level, tag, throwable, message)

    /**
     * Creates a new logger with the new [tag], but with the same [processor]
     * @param tag The new tag
     */
    fun withTag(tag: String) = StitchLogger(level, tag, { _ -> processor })

    /**
     * Creates a new logger and appends [tag] to the current tag
     * @param tag The tag to append
     */
    fun appendTag(tag: String) = withTag(this.tag + tag)

}

/**
 * Log a debug message with an optional [throwable]
 * @param throwable An optional throwable
 * @param message The message to log
 */
inline fun StitchLogger.d(throwable: Throwable? = null, message: () -> String) {
    log(LogLevel.DEBUG, throwable, message)
}

/**
 * Log an info message with an optional [throwable]
 * @param throwable An optional throwable
 * @param message The message to log
 */
inline fun StitchLogger.i(throwable: Throwable? = null, message: () -> String) {
    log(LogLevel.INFO, throwable, message)
}

/**
 * Log a warning message with an optional [throwable]
 * @param throwable An optional throwable
 * @param message The message to log
 */
inline fun StitchLogger.w(throwable: Throwable? = null, message: () -> String) {
    log(LogLevel.WARNING, throwable, message)
}

/**
 * Log an error message with an optional [throwable]
 * @param throwable An optional throwable
 * @param message The message to log
 */
inline fun StitchLogger.e(throwable: Throwable? = null, message: () -> String) {
    log(LogLevel.ERROR, throwable, message)
}

fun StitchClient.createLogger(
    tag: String,
    logLevel: LogLevel?,
    loggingFactory: StitchLoggingProcessorFactory?
) = StitchLogger(logLevel ?: logger.level, tag, loggingFactory ?: config.loggingConfig.defaultLoggingFactory)

fun StitchClient.createLogger(
    tag: String,
    config: MainConfig
) = createLogger(tag, config.logLevel,config.loggingFactory)