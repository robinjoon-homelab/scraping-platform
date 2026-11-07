package com.example.template.collection.adapter.outbound.http

import org.apache.hc.client5.http.DnsResolver
import java.net.InetAddress
import java.net.UnknownHostException

// Answers HttpClient's TCP endpoint lookup with exactly one address. Direct connections use the first destination
// address after all destination addresses passed the policy, so DNS rebinding cannot move the connection after checking.
// CONNECT connections pin the trusted proxy instead; this does not pin the proxy's upstream destination. Only that one
// endpoint name can be resolved, and other names are refused rather than resolved.
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
