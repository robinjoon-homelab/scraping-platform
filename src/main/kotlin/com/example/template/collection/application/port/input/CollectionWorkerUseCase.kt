package com.example.template.collection.application.port.input

interface CollectionWorkerUseCase {
    // Starts up to limit due pending runs, collects each one and closes it as SUCCEEDED or FAILED. Returns the number of runs
    // this call started; runs started first by another worker are skipped. One run's failure does not stop the others.
    fun runPending(limit: Int): Int

    // Closes up to limit running runs whose lease has expired as FAILED without retrying them. Returns the number closed.
    fun expireLeases(limit: Int): Int
}
