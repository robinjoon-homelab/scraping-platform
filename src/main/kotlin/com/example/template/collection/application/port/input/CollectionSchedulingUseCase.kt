package com.example.template.collection.application.port.input

interface CollectionSchedulingUseCase {
    // Claims up to limit due slots, creating one pending run per claimed slot, and returns the number of created runs.
    // Enabled tasks without a next run time are first given one and do not create a run in the same call.
    fun scheduleDue(limit: Int): Int
}
