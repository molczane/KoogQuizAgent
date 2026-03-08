package org.jetbrains.koog.cyberwave.observability

import kotlinx.coroutines.CancellationException
import kotlin.time.TimeMark
import kotlin.time.TimeSource

fun interface StudyWorkflowTracer {
    fun record(event: StudyWorkflowTraceEvent)
}

data class StudyWorkflowTraceEvent(
    val spanName: String,
    val status: StudyWorkflowTraceStatus,
    val attributes: Map<String, String> = emptyMap(),
)

enum class StudyWorkflowTraceStatus {
    STARTED,
    SUCCEEDED,
    FAILED,
}

object NoOpStudyWorkflowTracer : StudyWorkflowTracer {
    override fun record(event: StudyWorkflowTraceEvent) = Unit
}

class ConsoleStudyWorkflowTracer(
    private val emitter: (String) -> Unit = ::println,
) : StudyWorkflowTracer {
    override fun record(event: StudyWorkflowTraceEvent) {
        val attributes =
            event.attributes
                .entries
                .sortedBy { entry -> entry.key }
                .joinToString(separator = ", ") { entry -> "${entry.key}=${entry.value}" }

        val line =
            if (attributes.isEmpty()) {
                "[study-trace] ${event.status.name} ${event.spanName}"
            } else {
                "[study-trace] ${event.status.name} ${event.spanName} | $attributes"
            }

        emitter(line)
    }
}

suspend inline fun <T> StudyWorkflowTracer.traceSpan(
    name: String,
    attributes: Map<String, String> = emptyMap(),
    crossinline successAttributes: (T) -> Map<String, String> = { emptyMap() },
    block: suspend () -> T,
): T {
    record(
        StudyWorkflowTraceEvent(
            spanName = name,
            status = StudyWorkflowTraceStatus.STARTED,
            attributes = attributes,
        ),
    )

    val startMark = TimeSource.Monotonic.markNow()

    return try {
        val result = block()
        record(
            StudyWorkflowTraceEvent(
                spanName = name,
                status = StudyWorkflowTraceStatus.SUCCEEDED,
                attributes =
                    attributes +
                        successAttributes(result) +
                        mapOf("duration_ms" to startMark.elapsedNow().inWholeMilliseconds.toString()),
            ),
        )
        result
    } catch (exception: CancellationException) {
        recordFailure(
            name = name,
            baseAttributes = attributes,
            startMark = startMark,
            exception = exception,
            cancelled = true,
        )
        throw exception
    } catch (exception: Throwable) {
        recordFailure(
            name = name,
            baseAttributes = attributes,
            startMark = startMark,
            exception = exception,
            cancelled = false,
        )
        throw exception
    }
}

@PublishedApi
internal fun StudyWorkflowTracer.recordFailure(
    name: String,
    baseAttributes: Map<String, String>,
    startMark: TimeMark,
    exception: Throwable,
    cancelled: Boolean,
) {
    record(
        StudyWorkflowTraceEvent(
            spanName = name,
            status = StudyWorkflowTraceStatus.FAILED,
            attributes =
                baseAttributes +
                    mapOf(
                        "duration_ms" to startMark.elapsedNow().inWholeMilliseconds.toString(),
                        "cancelled" to cancelled.toString(),
                        "error_type" to exception::class.simpleName.orEmpty().ifBlank { "UnknownError" },
                    ) +
                    exception.message
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                        ?.let { message -> mapOf("error_message" to message) }
                        .orEmpty(),
        ),
    )
}
