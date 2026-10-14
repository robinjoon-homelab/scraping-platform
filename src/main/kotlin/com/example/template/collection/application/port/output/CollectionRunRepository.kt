package com.example.template.collection.application.port.output

import com.example.template.collection.domain.run.CollectionRun
import com.example.template.collection.domain.run.CollectionRunStatus
import java.util.UUID

interface CollectionRunRepository {
    fun insert(run: CollectionRun): CollectionRun

    fun findById(id: UUID): CollectionRun?

    // Stores the run only while the stored status is still expectedStatus. Returns null when the run is missing, its stored
    // status differs, or expectedStatus is already final, so a stale object never overwrites a newer state.
    fun update(
        run: CollectionRun,
        expectedStatus: CollectionRunStatus,
    ): CollectionRun?
}
