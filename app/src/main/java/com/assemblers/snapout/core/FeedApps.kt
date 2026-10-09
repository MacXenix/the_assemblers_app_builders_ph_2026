package com.assemblers.snapout.core

object FeedApps {
    val names = mapOf(
        "com.zhiliaoapp.musically" to "TikTok",
        "com.ss.android.ugc.trill" to "TikTok",
        "com.instagram.android" to "Instagram",
        "com.google.android.youtube" to "YouTube",
        "com.twitter.android" to "X",
        "com.facebook.katana" to "Facebook",
        "com.reddit.frontpage" to "Reddit",
    )

    /** Windows that may appear on top of a feed without ending the session. */
    val transient = setOf("com.android.systemui", "com.google.android.inputmethod.latin", "com.samsung.android.honeyboard")

    fun isFeed(pkg: String?) = pkg != null && pkg in names
    fun label(pkg: String?) = names[pkg] ?: pkg ?: "a feed"
}
