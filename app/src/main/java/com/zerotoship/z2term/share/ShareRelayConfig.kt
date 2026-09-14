package com.zerotoship.z2term.share

/** The user supplies both the SSH profile and an existing HTTPS reverse proxy. */
internal class ShareRelayConfig private constructor(
    val profileId: String, val origin: String, val remotePort: Int, val minutes: Int,
) {
    companion object {
        fun parse(profileId: String, origin: String, remotePort: Int, minutes: Int): ShareRelayConfig {
            require(profileId.isNotBlank() && profileId.length <= 256)
            val parsed = DirectShareConfig.parse(origin, remotePort, minutes)
            require(parsed.tls)
            return ShareRelayConfig(profileId, parsed.origin, remotePort, minutes)
        }
    }
}
