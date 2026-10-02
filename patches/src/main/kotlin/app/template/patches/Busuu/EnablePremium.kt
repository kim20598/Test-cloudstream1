/**
 * Busuu "Enable Premium" patch.
 *
 * Adapted from hoo-dles/morphe-patches (GPLv3):
 * https://github.com/hoo-dles/morphe-patches
 */

package app.template.patches.busuu

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.returnEarly
import app.template.patches.shared.Constants.COMPATIBILITY_BUSUU

// ApiUser
private object ApiUserToStringFingerprint : Fingerprint(
    strings = listOf("ApiUser(legacyUid=")
)

// ApiUserAccess
private object ApiUserAccessToStringFingerprint : Fingerprint(
    strings = listOf("ApiUserAccess(tier=")
)

private object GetTierFingerprint : Fingerprint(
    name = "getTier"
)

// PremiumUser
private object PremiumUserCtorFingerprint : Fingerprint(
    strings = listOf("legacyId", "name", "avatar", "accessTier")
)

private object GetHasActiveSubscriptionFingerprint : Fingerprint(
    name = "getHasActiveSubscription"
)

private object IsPremiumFingerprint : Fingerprint(
    name = "isPremium"
)

@Suppress("unused")
val enablePremiumPatch = bytecodePatch(
    name = "Enable Premium",
    description = "Enables app features locked behind the subscription paywall.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_BUSUU)

    execute {
        IsPremiumFingerprint.match(ApiUserToStringFingerprint.classDef)
            .method.returnEarly(true)

        GetTierFingerprint.match(ApiUserAccessToStringFingerprint.classDef)
            .method.returnEarly("standard")

        GetHasActiveSubscriptionFingerprint.method.returnEarly(true)
        IsPremiumFingerprint.match(PremiumUserCtorFingerprint.classDef).method.returnEarly(true)
    }
}
