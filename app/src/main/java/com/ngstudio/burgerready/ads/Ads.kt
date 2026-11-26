package com.ngstudio.burgerready.ads

/**
 * Single seam for rewarded ads. Real ads can be added later by implementing this interface
 * (e.g. AdMob/AppLovin + UMP consent) and selecting it in App.onCreate.
 *
 *  - DebugMockAds: debug builds only. The UI shows a mock "ad" for a few seconds, then rewards.
 *  - NoAds: release builds (no ad UI anywhere until a real provider is integrated).
 */
interface AdsProvider {
    /** True when the UI may offer a rewarded action ("Watch Ad"). */
    val rewardedAvailable: Boolean
    /** True when the UI must render the mock ad overlay itself (debug only). */
    val isMock: Boolean
}

object DebugMockAds : AdsProvider {
    override val rewardedAvailable = true
    override val isMock = true
}

object NoAds : AdsProvider {
    override val rewardedAvailable = false
    override val isMock = false
}
