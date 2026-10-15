package com.example.template.collection.adapter.outbound.http

import com.example.template.collection.application.port.output.CollectionConnectorException
import org.apache.hc.core5.http.ClassicHttpResponse
import java.io.ByteArrayOutputStream

// Reads a 2xx body of at most maxBytes. Any other status, or a body that grows past the limit, fails with a reason that
// names only the status or the limit. Closing an HttpClient response reads the rest of its body so the connection can be
// reused, so abort is called first: it closes the connection and the rest of the body is never read.
internal object BoundedResponseBody {
    private const val CHUNK_BYTES = 8192
    private val successStatuses = 200..299
    private val redirectStatuses = 300..399

    fun read(
        response: ClassicHttpResponse,
        maxBytes: Int,
        abort: () -> Unit,
    ): ByteArray {
        rejection(response.code)?.let { reason ->
            abort()
            throw reason
        }
        val content = response.entity?.content ?: return ByteArray(0)
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(CHUNK_BYTES)
        var read = content.read(chunk)
        while (read >= 0) {
            if (buffer.size().toLong() + read > maxBytes) {
                abort()
                throw CollectionConnectorException("response body exceeds $maxBytes bytes")
            }
            buffer.write(chunk, 0, read)
            read = content.read(chunk)
        }
        return buffer.toByteArray()
    }

    // Redirects are never followed, because the target of a redirect has not passed the address check.
    private fun rejection(status: Int): CollectionConnectorException? =
        when (status) {
            in successStatuses -> null
            in redirectStatuses -> CollectionConnectorException("redirect not followed: HTTP status $status")
            else -> CollectionConnectorException("unexpected HTTP status $status")
        }
}
