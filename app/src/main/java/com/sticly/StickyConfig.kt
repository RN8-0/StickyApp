package com.sticly

object StickyConfig {
    const val LEGAL_BASE_URL = "https://sticky-privacy.46.225.95.201.sslip.io/"

    fun legalUrl(anchor: String): String = LEGAL_BASE_URL + anchor.removePrefix("/")
}