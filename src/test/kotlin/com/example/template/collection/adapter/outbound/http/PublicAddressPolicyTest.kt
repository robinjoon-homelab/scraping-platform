package com.example.template.collection.adapter.outbound.http

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.Inet6Address
import java.net.InetAddress

class PublicAddressPolicyTest {
    @ParameterizedTest
    @ValueSource(
        strings = [
            "0.0.0.0", "0.255.255.255", "10.0.0.0", "10.42.0.15", "10.43.0.1", "10.255.255.255",
            "100.64.0.0", "100.127.255.255", "127.0.0.1", "127.255.255.254", "169.254.0.0", "169.254.169.254",
            "169.254.255.255", "172.16.0.0", "172.31.255.255", "192.168.0.0", "192.168.255.255", "224.0.0.1",
            "239.255.255.255", "240.0.0.1", "255.255.255.255",
        ],
    )
    fun `IPv4 loopback, private, link-local, shared, multicast and reserved addresses are blocked`(literal: String) {
        assertThat(PublicAddressPolicy.permits(InetAddress.getByName(literal))).isFalse()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "192.0.0.0", "192.0.0.8", "192.0.0.9", "192.0.0.10", "192.0.0.170", "192.0.0.255", "192.0.2.0", "192.0.2.255",
            "192.88.99.0", "192.88.99.255", "198.18.0.0", "198.19.255.255", "198.51.100.0", "198.51.100.255",
            "203.0.113.0", "203.0.113.255",
        ],
    )
    fun `IPv4 special-purpose blocks that are not globally reachable are blocked whole`(literal: String) {
        assertThat(PublicAddressPolicy.permits(InetAddress.getByName(literal))).isFalse()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "::", "::1", "::8.8.8.8", "::127.0.0.1", "100::1", "100::ffff:ffff:ffff:ffff", "1fff:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            "4000::1", "5f00::1", "fbff:ffff::1", "fc00::1", "fdff:ffff::1", "fe7f::1", "fe80::1", "febf:ffff::1", "fec0::1",
            "ff02::1",
        ],
    )
    fun `IPv6 addresses outside global unicast are blocked`(literal: String) {
        assertThat(PublicAddressPolicy.permits(InetAddress.getByName(literal))).isFalse()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "2001::", "2001:0:4136:e378:8000:63bf:3fff:fdd2", "2001:1::1", "2001:2::1", "2001:1ff:ffff:ffff:ffff:ffff:ffff:ffff",
            "2001:db8::", "2001:db8:ffff:ffff:ffff:ffff:ffff:ffff", "2002::", "2002:7f00:1::1", "2002:808:808::1",
            "2002:ffff:ffff:ffff:ffff:ffff:ffff:ffff", "3fff::", "3fff:fff:ffff:ffff:ffff:ffff:ffff:ffff",
        ],
    )
    fun `IPv6 special-purpose, documentation, Teredo and 6to4 blocks inside global unicast are blocked`(literal: String) {
        assertThat(PublicAddressPolicy.permits(InetAddress.getByName(literal))).isFalse()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "::ffff:127.0.0.1", "::ffff:10.0.0.1", "::ffff:169.254.169.254", "64:ff9b::a00:1", "64:ff9b::7f00:1",
            "64:ff9b::c612:1", "64:ff9b:1::a00:1", "64:ff9b:1::808:808", "64:ff9b:1:ffff:ffff:ffff:ffff:ffff",
            "64:ff9b::1:0:808:808",
        ],
    )
    fun `translated IPv6 forms are blocked unless they are well-known NAT64 or mapped forms of a public address`(literal: String) {
        assertThat(PublicAddressPolicy.permits(InetAddress.getByName(literal))).isFalse()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "1.1.1.1", "8.8.8.8", "9.255.255.255", "11.0.0.0", "100.63.255.255", "100.128.0.0", "126.255.255.255",
            "128.0.0.0", "169.253.255.255", "169.255.0.0", "172.15.255.255", "172.32.0.0", "191.255.255.255",
            "192.0.1.0", "192.0.3.0", "192.31.196.1", "192.52.193.1", "192.88.98.255", "192.88.100.0", "192.167.255.255",
            "192.169.0.0", "198.17.255.255", "198.20.0.0", "198.51.99.255", "198.51.101.0", "203.0.112.255", "203.0.114.0",
            "223.255.255.255",
        ],
    )
    fun `public IPv4 addresses, including the neighbours of blocked ranges, are permitted`(literal: String) {
        assertThat(PublicAddressPolicy.permits(InetAddress.getByName(literal))).isTrue()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "2001:200::1", "2001:db7:ffff:ffff:ffff:ffff:ffff:ffff", "2001:db9::", "2001:4860:4860::8888", "2003::1",
            "2606:4700::1111", "3fff:1000::1", "::ffff:8.8.8.8", "64:ff9b::808:808",
        ],
    )
    fun `global unicast IPv6 addresses and translated public IPv4 addresses are permitted`(literal: String) {
        assertThat(PublicAddressPolicy.permits(InetAddress.getByName(literal))).isTrue()
    }

    @Test
    fun `an IPv4-mapped address kept in IPv6 form is judged by its IPv4 address`() {
        fun mapped(vararg ipv4: Int): InetAddress =
            Inet6Address.getByAddress(
                null,
                ByteArray(10) + byteArrayOf(0xff.toByte(), 0xff.toByte()) + ipv4.map(Int::toByte).toByteArray(),
                -1,
            )

        assertThat(mapped(10, 43, 0, 1)).isInstanceOf(Inet6Address::class.java)
        assertThat(PublicAddressPolicy.permits(mapped(10, 43, 0, 1))).isFalse()
        assertThat(PublicAddressPolicy.permits(mapped(127, 0, 0, 1))).isFalse()
        assertThat(PublicAddressPolicy.permits(mapped(8, 8, 8, 8))).isTrue()
    }
}
