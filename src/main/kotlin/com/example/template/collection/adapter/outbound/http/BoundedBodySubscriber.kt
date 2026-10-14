package com.example.template.collection.adapter.outbound.http

import com.example.template.collection.application.port.output.CollectionConnectorException
import java.io.ByteArrayOutputStream
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Flow

// Collects a 2xx body of at most maxBytes. Any other status, or a body that grows past the limit, cancels the
// subscription so the rest of the body is never read, and fails with a reason that names only the status or the limit.
internal class BoundedBodySubscriber private constructor(
    private val maxBytes: Int,
    private val rejection: CollectionConnectorException?,
) : HttpResponse.BodySubscriber<ByteArray> {
    private val result = CompletableFuture<ByteArray>()
    private val buffer = ByteArrayOutputStream()
    private var subscription: Flow.Subscription? = null

    override fun getBody(): CompletionStage<ByteArray> = result

    override fun onSubscribe(subscription: Flow.Subscription) {
        this.subscription = subscription
        if (rejection == null) {
            subscription.request(Long.MAX_VALUE)
        } else {
            subscription.cancel()
            result.completeExceptionally(rejection)
        }
    }

    override fun onNext(item: List<ByteBuffer>) {
        if (result.isDone) return
        val size = buffer.size().toLong() + item.sumOf { it.remaining().toLong() }
        if (size > maxBytes) {
            subscription?.cancel()
            result.completeExceptionally(CollectionConnectorException("response body exceeds $maxBytes bytes"))
            return
        }
        item.forEach { chunk ->
            val bytes = ByteArray(chunk.remaining())
            chunk.get(bytes)
            buffer.write(bytes)
        }
    }

    override fun onError(throwable: Throwable) {
        result.completeExceptionally(throwable)
    }

    override fun onComplete() {
        result.complete(buffer.toByteArray())
    }

    companion object {
        private val successStatuses = 200..299
        private val redirectStatuses = 300..399

        fun handler(maxBytes: Int): HttpResponse.BodyHandler<ByteArray> =
            HttpResponse.BodyHandler { info -> BoundedBodySubscriber(maxBytes, rejection(info.statusCode())) }

        // Redirects are never followed, because the target of a redirect has not passed the address check.
        private fun rejection(status: Int): CollectionConnectorException? =
            when (status) {
                in successStatuses -> null
                in redirectStatuses -> CollectionConnectorException("redirect not followed: HTTP status $status")
                else -> CollectionConnectorException("unexpected HTTP status $status")
            }
    }
}
