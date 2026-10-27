package com.hexa.core.metrics

/** Latency metrics collected while the launcher is used. All values in milliseconds. */
enum class Metric(val label: String) {
    TOUCH_TO_RECOGNITION("Toque → reconhecimento (total)"),
    RECOGNITION_PROCESSING("Processamento do reconhecimento"),
    COMBO_RECOGNITION("Reconhecimento de combinação"),
    LAUNCH_DISPATCH("Pedido de abertura → comando enviado ao Android"),
    TOUCH_TO_LAUNCH("Toque → comando enviado ao Android"),
    SEARCH_QUERY("Processamento da pesquisa"),
    SEARCH_OPEN_TO_SELECT("Abrir pesquisa → selecionar resultado"),
    SEARCH_KEY_TO_SELECT("Última tecla → selecionar resultado"),
    APP_INDEX_LOAD("Carregamento do índice de apps"),
    CONFIG_LOAD("Carregamento da configuração"),
}

data class MetricSummary(
    val metric: Metric,
    val count: Int,
    val last: Double,
    val mean: Double,
    val p50: Double,
    val p95: Double,
    val max: Double,
)

/** Fixed-size ring buffers per metric. Recording is O(1) and allocation-free. */
class PerfStats(private val capacity: Int = 256) {

    private class Ring(capacity: Int) {
        val values = DoubleArray(capacity)
        var size = 0
        var next = 0
        var last = 0.0
    }

    private val rings = Metric.entries.associateWith { Ring(capacity) }

    fun record(metric: Metric, millis: Double) {
        val ring = rings.getValue(metric)
        synchronized(ring) {
            ring.values[ring.next] = millis
            ring.next = (ring.next + 1) % capacity
            if (ring.size < capacity) ring.size++
            ring.last = millis
        }
    }

    fun recordNanos(metric: Metric, nanos: Long) = record(metric, nanos / 1_000_000.0)

    fun summary(metric: Metric): MetricSummary? {
        val ring = rings.getValue(metric)
        val copy: DoubleArray
        val last: Double
        synchronized(ring) {
            if (ring.size == 0) return null
            copy = ring.values.copyOf(ring.size)
            last = ring.last
        }
        copy.sort()
        return MetricSummary(
            metric = metric,
            count = copy.size,
            last = last,
            mean = copy.average(),
            p50 = percentile(copy, 0.5),
            p95 = percentile(copy, 0.95),
            max = copy.last(),
        )
    }

    fun all(): List<MetricSummary> = Metric.entries.mapNotNull(::summary)

    fun clear() {
        rings.values.forEach { synchronized(it) { it.size = 0; it.next = 0 } }
    }

    private fun percentile(sorted: DoubleArray, p: Double): Double {
        val idx = ((sorted.size - 1) * p).let { kotlin.math.round(it).toInt() }
        return sorted[idx.coerceIn(0, sorted.size - 1)]
    }
}
