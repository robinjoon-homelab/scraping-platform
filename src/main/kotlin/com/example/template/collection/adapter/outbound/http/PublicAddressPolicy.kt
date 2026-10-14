package com.example.template.collection.adapter.outbound.http

import java.net.InetAddress

// Permits only globally reachable unicast destinations so that a source cannot point the collector at the cluster it
// runs in or at any network that is reachable only locally. The K3s pod and service networks (10.42.0.0/16 and
// 10.43.0.0/16 by default), node addresses on the home network and the link-local metadata address are all blocked.
//
// The blocked ranges follow the IANA special-purpose address registries, blocking every destination whose "Globally
// Reachable" value is not True:
// https://www.iana.org/assignments/iana-ipv4-special-registry/ and
// https://www.iana.org/assignments/iana-ipv6-special-registry/
// Whole special blocks are blocked even where the registry lists a few globally reachable members (192.0.0.9 and
// 192.0.0.10 in 192.0.0.0/24; PCP, TURN, AMT, AS112 and ORCHIDv2 members of 2001::/23). Those are protocol anycast and
// infrastructure services, never HTTP JSON APIs, and one block per registry entry keeps the rule auditable.
//
// IPv6 is allowed only inside the global unicast range 2000::/3, so unassigned, loopback, unique-local, link-local,
// multicast, discard and SRv6 space are rejected without listing them. Two IPv6 forms are judged by the IPv4 address
// they carry: IPv4-mapped addresses (::ffff:0:0/96) and the NAT64 well-known prefix (64:ff9b::/96, RFC 6052 places the
// IPv4 address in the last four bytes for a /96). Every other transition form is rejected outright rather than decoded:
// the local-use NAT64 prefix 64:ff9b:1::/48 (RFC 8215) may translate to private IPv4 networks, and 6to4 (2002::/16),
// Teredo (2001::/32, inside 2001::/23) and IPv4-compatible addresses (::/96) are not needed to reach public HTTP APIs.
object PublicAddressPolicy : DestinationAddressPolicy {
    private const val IPV4_BYTES = 4
    private const val IPV6_BYTES = 16
    private const val EMBEDDED_IPV4_OFFSET = 12

    private val blockedIpv4 =
        listOf(
            block("0.0.0.0", 8), // "this network", including the any-local address
            block("10.0.0.0", 8), // private use
            block("100.64.0.0", 10), // shared address space (carrier-grade NAT)
            block("127.0.0.0", 8), // loopback
            block("169.254.0.0", 16), // link-local, including cloud metadata endpoints
            block("172.16.0.0", 12), // private use
            block("192.0.0.0", 24), // IETF protocol assignments, including its anycast exceptions
            block("192.0.2.0", 24), // documentation (TEST-NET-1)
            block("192.88.99.0", 24), // deprecated 6to4 relay anycast
            block("192.168.0.0", 16), // private use
            block("198.18.0.0", 15), // benchmarking, routable only inside a site
            block("198.51.100.0", 24), // documentation (TEST-NET-2)
            block("203.0.113.0", 24), // documentation (TEST-NET-3)
            block("224.0.0.0", 4), // multicast
            block("240.0.0.0", 4), // reserved, including the limited broadcast 255.255.255.255
        )

    private val globalUnicastIpv6 = block("2000::", 3)

    private val blockedGlobalIpv6 =
        listOf(
            block("2001::", 23), // IETF protocol assignments, including Teredo and benchmarking
            block("2001:db8::", 32), // documentation
            block("2002::", 16), // 6to4
            block("3fff::", 20), // documentation
        )

    private val ipv4Mapped = AddressBlock(ByteArray(10) + byteArrayOf(-1, -1) + ByteArray(IPV4_BYTES), 96)
    private val nat64WellKnown = block("64:ff9b::", 96)

    override fun permits(address: InetAddress): Boolean {
        val bytes = address.address
        return when {
            bytes.size == IPV4_BYTES -> {
                blockedIpv4.none { it.contains(bytes) }
            }

            ipv4Mapped.contains(bytes) || nat64WellKnown.contains(bytes) -> {
                blockedIpv4.none { it.contains(bytes.copyOfRange(EMBEDDED_IPV4_OFFSET, IPV6_BYTES)) }
            }

            else -> {
                globalUnicastIpv6.contains(bytes) && blockedGlobalIpv6.none { it.contains(bytes) }
            }
        }
    }

    // Only numeric literals are given here, so no name lookup happens.
    private fun block(
        literal: String,
        prefixLength: Int,
    ): AddressBlock = AddressBlock(InetAddress.getByName(literal).address, prefixLength)

    private class AddressBlock(
        private val network: ByteArray,
        private val prefixLength: Int,
    ) {
        fun contains(address: ByteArray): Boolean {
            if (address.size != network.size) return false
            val fullBytes = prefixLength / Byte.SIZE_BITS
            if ((0 until fullBytes).any { address[it] != network[it] }) return false
            val remainingBits = prefixLength % Byte.SIZE_BITS
            if (remainingBits == 0) return true
            val mask = (0xff shl (Byte.SIZE_BITS - remainingBits)) and 0xff
            return (address[fullBytes].toInt() and mask) == (network[fullBytes].toInt() and mask)
        }
    }
}
