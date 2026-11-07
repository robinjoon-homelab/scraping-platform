package com.example.template.collection.adapter.outbound.http

import com.example.template.collection.application.port.output.CollectionConnectorException
import org.apache.hc.client5.http.HttpRoute
import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.apache.hc.core5.http.HttpException
import org.apache.hc.core5.http.HttpHost
import java.net.InetAddress
import java.net.URI
import java.time.Duration

// Only trusted process-level JVM settings select a proxy. Business input supplies the destination, never a proxy.
internal class HttpCollectionRouting(
    private val resolution: BoundedHostResolver,
    private val addressPolicy: DestinationAddressPolicy,
) {
    fun prepare(
        uri: URI,
        deadline: Long,
    ): CheckedRoute {
        val host = checkNotNull(uri.host)
        val addresses = resolve(host, deadline)
        if (!addresses.all(addressPolicy::permits)) throw CollectionConnectorException("destination address is not allowed")
        val route = select(uri)
        val proxy = route.proxyHost
        if (proxy == null) return CheckedRoute(route, PinnedAddressResolver(host, addresses.first()))
        val proxyAddress = resolve(proxy.hostName, deadline).first()
        // Strip any address supplied by ProxySelector: the connection must use our bounded, single-address lookup.
        val pinnedRoute = HttpRoute(route.targetHost, null, HttpHost("http", proxy.hostName, proxy.port), true)
        return CheckedRoute(pinnedRoute, PinnedAddressResolver(proxy.hostName, proxyAddress))
    }

    private fun resolve(
        host: String,
        deadline: Long,
    ): List<InetAddress> {
        val addresses = resolution.resolve(host, Duration.ofNanos(deadline - System.nanoTime()))
        if (addresses.isEmpty()) throw CollectionConnectorException("destination address is not allowed")
        return addresses
    }

    private fun select(uri: URI): HttpRoute =
        try {
            // The platform supports CONNECT for HTTPS only. Preserve existing direct plain HTTP behavior.
            val planner = if (uri.scheme.equals("https", ignoreCase = true)) SystemDefaultRoutePlanner(null) else DefaultRoutePlanner(null)
            planner.determineRoute(HttpHost.create(uri), HttpClientContext.create())
        } catch (exception: HttpException) {
            throw CollectionConnectorException("HTTP route could not be planned", exception)
        }

    class CheckedRoute(
        val route: HttpRoute,
        val resolver: PinnedAddressResolver,
    )
}
