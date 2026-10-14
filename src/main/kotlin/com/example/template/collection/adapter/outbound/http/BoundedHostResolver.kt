package com.example.template.collection.adapter.outbound.http

import com.example.template.collection.application.port.output.CollectionConnectorException
import java.net.InetAddress
import java.net.UnknownHostException
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

// Runs the blocking resolver on a small pool so that every lookup ends within the caller's remaining time. The JVM
// resolver cannot be cancelled, so a timed-out lookup keeps its thread until the operating system answers. The pool
// therefore has at most maxThreads threads and no waiting queue: when every thread is stuck on a slow lookup, new
// lookups fail at once instead of piling up behind them.
class BoundedHostResolver(
    private val resolver: HostResolver,
    maxThreads: Int,
) {
    private val executor =
        ThreadPoolExecutor(
            0,
            maxThreads,
            IDLE_SECONDS,
            TimeUnit.SECONDS,
            SynchronousQueue(),
            Thread
                .ofPlatform()
                .daemon()
                .name("http-connector-resolver-", 0)
                .factory(),
        )

    fun resolve(
        host: String,
        timeout: Duration,
    ): List<InetAddress> {
        if (!timeout.isPositive) throw CollectionConnectorException(TIMED_OUT)
        val lookup =
            try {
                executor.submit<List<InetAddress>> { resolver.resolve(host) }
            } catch (exception: RejectedExecutionException) {
                throw CollectionConnectorException("too many pending host resolutions", exception)
            }
        try {
            return lookup.get(timeout.toNanos(), TimeUnit.NANOSECONDS)
        } catch (exception: TimeoutException) {
            lookup.cancel(true)
            throw CollectionConnectorException(TIMED_OUT, exception)
        } catch (exception: InterruptedException) {
            lookup.cancel(true)
            throw exception
        } catch (exception: ExecutionException) {
            throw failure(exception)
        }
    }

    // JVM errors and interruption are not failures of the source: they are rethrown as the same instance so the worker
    // ends its call and the lease expiry closes the run. Only lookup failures become a fixed collection reason.
    private fun failure(exception: ExecutionException): Throwable =
        when (val cause = exception.cause) {
            is Error, is InterruptedException -> cause
            is UnknownHostException -> CollectionConnectorException("destination host could not be resolved", exception)
            else -> CollectionConnectorException("destination host resolution failed with ${cause?.javaClass?.name}", exception)
        }

    private companion object {
        const val IDLE_SECONDS = 60L
        const val TIMED_OUT = "destination host resolution timed out"
    }
}
