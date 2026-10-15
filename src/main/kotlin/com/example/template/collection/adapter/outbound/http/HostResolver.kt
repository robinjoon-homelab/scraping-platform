package com.example.template.collection.adapter.outbound.http

import java.net.InetAddress

// Resolves every address of a host name once per collection. Production uses the JVM resolver; the connector connects
// only to the addresses this lookup returned, so HttpClient never resolves the host itself.
fun interface HostResolver {
    fun resolve(host: String): List<InetAddress>

    companion object {
        val system: HostResolver = HostResolver { host -> InetAddress.getAllByName(host).toList() }
    }
}
