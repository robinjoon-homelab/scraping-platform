package com.example.template.collection.adapter.outbound.http

import java.net.InetAddress

// Resolves every address of a host name. Production uses the JVM resolver, whose cache is shared with HttpClient.
fun interface HostResolver {
    fun resolve(host: String): List<InetAddress>

    companion object {
        val system: HostResolver = HostResolver { host -> InetAddress.getAllByName(host).toList() }
    }
}
