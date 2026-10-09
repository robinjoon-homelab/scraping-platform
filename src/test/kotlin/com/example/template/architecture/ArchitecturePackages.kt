package com.example.template.architecture

internal enum class ArchitectureLayer(
    val segments: List<String>,
) {
    DOMAIN(listOf("domain")),
    MODEL(listOf("application", "model")),
    INPUT_PORT(listOf("application", "port", "input")),
    OUTPUT_PORT(listOf("application", "port", "output")),
    SERVICE(listOf("application", "service")),
    INBOUND(listOf("adapter", "inbound")),
    OUTBOUND(listOf("adapter", "outbound")),
    CONFIG(listOf("config")),
}

internal data class ArchitectureLocation(
    val feature: List<String>,
    val layer: ArchitectureLayer,
    val path: List<String>,
) {
    val isAdapter: Boolean get() = layer == ArchitectureLayer.INBOUND || layer == ArchitectureLayer.OUTBOUND
    val isPort: Boolean get() = layer == ArchitectureLayer.INPUT_PORT || layer == ArchitectureLayer.OUTPUT_PORT
    val isApplication: Boolean get() = isPort || layer == ArchitectureLayer.MODEL || layer == ArchitectureLayer.SERVICE
    val isPersistence: Boolean get() = layer == ArchitectureLayer.OUTBOUND && "persistence" in path

    fun matchesPort(port: ArchitectureLocation): Boolean =
        (feature == port.feature || port.feature.isEmpty()) && path.containsPath(port.path)

    fun sharesAdapterWith(other: ArchitectureLocation): Boolean =
        isAdapter && other.isAdapter && feature == other.feature && layer == other.layer && path.firstOrNull() == other.path.firstOrNull()
}

internal class ArchitecturePackages(
    private val root: String,
) {
    fun location(packageName: String): ArchitectureLocation? {
        if (!packageName.startsWith("$root.")) return null
        val segments = packageName.removePrefix("$root.").split('.')
        val boundary = segments.indexOfFirst { it in layerNames }
        if (boundary < 0) return null
        val remainder = segments.drop(boundary)
        val layer = ArchitectureLayer.entries.firstOrNull { remainder.take(it.segments.size) == it.segments } ?: return null
        return ArchitectureLocation(segments.take(boundary), layer, remainder.drop(layer.segments.size))
    }

    private companion object {
        val layerNames = setOf("domain", "application", "adapter", "config")
    }
}

private fun List<String>.containsPath(path: List<String>): Boolean =
    path.isEmpty() || (0..size - path.size).any { offset -> subList(offset, offset + path.size) == path }
