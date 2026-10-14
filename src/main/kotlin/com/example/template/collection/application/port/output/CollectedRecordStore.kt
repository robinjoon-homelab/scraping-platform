package com.example.template.collection.application.port.output

import com.example.template.collection.domain.run.CollectedRecord

// Records are values of a run's batch, not a root, so this contract never receives or returns a run. Where the records
// live is the adapter's choice; callers rely only on the rules below.
interface CollectedRecordStore {
    /**
     * Stores the records whose (source, source record key, revision hash) is not stored yet and returns how many were
     * stored. A repeated content version is ignored without error, so the stored run is the run that first observed it.
     * Safe against a concurrent store of the same version.
     */
    fun saveNew(records: List<CollectedRecord>): Int
}
