package com.example.template.collection.adapter.outbound.http

import java.net.InetAddress

// Decides whether the HTTP connector may send a request to a resolved address. Production always uses
// PublicAddressPolicy; a wider policy exists only so tests can reach a loopback server.
fun interface DestinationAddressPolicy {
    fun permits(address: InetAddress): Boolean
}
