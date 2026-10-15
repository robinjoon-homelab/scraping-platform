package com.example.template.collection.adapter.outbound.http

import org.apache.hc.client5.http.io.DetachedSocketFactory
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

// Creates and owns the raw sockets of one exchange so that an abort can close them itself. HttpClient checks for
// cancellation only before it starts connecting. Cancelling the request first closes the managed connection, without
// the connection manager's lock, and then waits for that lock to release the endpoint; the connecting thread holds the
// lock through connect and TLS. That first close only closes a socket already bound to the connection, so a socket
// created after the cancellation check but bound after the close would be attached to the already closed connection
// and keep connecting while the cancelling thread waits for the lock. Creation and cancellation here share one lock: a
// socket created after cancel is closed before it is returned, so HttpClient fails on it without connecting, and cancel
// closes every socket already created, which ends a blocked connect, TLS handshake or read at once and lets the
// connecting thread release the manager's lock.
//
// Sockets bypass the JVM's default proxy selector, which would otherwise apply socksProxyHost.
internal class ExchangeSockets : DetachedSocketFactory {
    private val lock = ReentrantLock()
    private val sockets = mutableListOf<Socket>()
    private var cancelled = false

    override fun create(proxy: Proxy?): Socket {
        val socket = Socket(Proxy.NO_PROXY)
        val owned = lock.withLock { !cancelled && sockets.add(socket) }
        if (!owned) socket.close()
        return socket
    }

    fun cancel() {
        val created =
            lock.withLock {
                cancelled = true
                sockets.toList()
            }
        created.forEach(Socket::close)
    }
}
