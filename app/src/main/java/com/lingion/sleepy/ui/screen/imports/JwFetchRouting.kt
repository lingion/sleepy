package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.data.jw.JwProtocol

private val WEBVPN_HTTP_HEX = Regex("/http/[0-9a-f]{4,8}/")

/** Final ZF fetch fallback, after the dedicated school collectors have been checked. */
internal fun shouldUseZfNewFetch(schoolType: String?, currentUrl: String): Boolean {
    val isIeas = schoolType == JwProtocol.TYPE_QZ_IEAS ||
        currentUrl.contains("/ieas2.1/", ignoreCase = true)
    return !isIeas && (schoolType == JwProtocol.TYPE_ZF_NEW ||
        currentUrl.contains("/jwglxt/", ignoreCase = true) ||
        currentUrl.contains("/kbcx/", ignoreCase = true) ||
        WEBVPN_HTTP_HEX.containsMatchIn(currentUrl))
}
