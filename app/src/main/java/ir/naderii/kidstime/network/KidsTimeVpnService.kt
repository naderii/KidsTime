package ir.naderii.kidstime.network

import android.net.VpnService

/**
 * Stage 1 placeholder.
 *
 * This service intentionally does not establish a VPN interface or intercept traffic.
 * Do not start it expecting website filtering. A later milestone must implement:
 * - user consent via VpnService.prepare()
 * - a TUN interface and packet processing
 * - DNS query parsing and allowlist enforcement
 * - IPv4/IPv6 handling, lifecycle/reconnect behavior and fail-safe policy
 * - protection against DNS bypass, while clearly documenting remaining limits
 */
class KidsTimeVpnService : VpnService()
