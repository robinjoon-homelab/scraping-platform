package com.example.template.collection.adapter.outbound.http

import org.apache.hc.client5.http.DnsResolver
import java.net.InetAddress
import java.net.UnknownHostException

// Answers HttpClient's lookup with the one address the connector chose after every address of the host passed the
// address policy, so the connection can only go to a checked address. Asking DNS again here would let a rebinding DNS
// server move the connection to an internal address after the check. The client that uses it sends one request without a
// proxy or redirects, so the request's own host is the only name it can be asked for; any other name is refused rather
// than resolved.
internal class PinnedAddressResolver(
    host: String,
    private val address: InetAddress,
) : DnsResolver {
    // URI keeps the brackets of an IPv6 literal while HttpClient's host name drops them.
    private val host = host.removeSurrounding("[", "]")

    override fun resolve(host: String): Array<InetAddress> {
        if (!host.equals(this.host, ignoreCase = true)) throw UnknownHostException("host is not the checked destination")
        return arrayOf(address)
    }

    override fun resolveCanonicalHostname(host: String): String = host
}
