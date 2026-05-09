package com.sticly

object StickyConfig {
    const val LEGAL_BASE_URL = "https://sticky-privacy-legal.web.app/"

    fun legalUrl(anchor: String): String = LEGAL_BASE_URL + anchor.removePrefix("/")
}