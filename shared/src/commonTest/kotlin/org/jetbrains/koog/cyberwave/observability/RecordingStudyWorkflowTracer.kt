package org.jetbrains.koog.cyberwave.observability

class RecordingStudyWorkflowTracer : StudyWorkflowTracer {
    private val _events = mutableListOf<StudyWorkflowTraceEvent>()

    val events: List<StudyWorkflowTraceEvent>
        get() = _events

    override fun record(event: StudyWorkflowTraceEvent) {
        _events += event
    }
}
