package com.example.template.domain

// Marks the only entry type of an aggregate. It has no runtime behavior; architecture tests read it.
@Target(AnnotationTarget.CLASS)
annotation class AggregateRoot
