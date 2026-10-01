package app.template.patches.letterboxd

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_LETTERBOXD

/**
 * Forces the current user's `MemberStatus` to `Patron` locally, so Patron-only UI (poster and
 * backdrop pickers, etc.) appears without the account actually being a Patron.
 *
 * Local-only. Server-validated actions (saving a poster, stats data, push notifications) still
 * see a non-Patron account.
 */
private val memberGetMemberStatusFingerprint = Fingerprint(
    name = "getMemberStatus",
    returnType = "Lcom/letterboxd/api/model/MemberStatus;",
)

@Suppress("unused")
val unlockPatronPatch = bytecodePatch(
    name = "Force Patron (local)",
    description = "Makes the app treat your own account as Patron locally, so Patron-only " +
        "screens and pickers appear. Purely cosmetic — the server still knows the real tier, so " +
        "anything that saves (posters, backdrops) or fetches Patron-only data will not actually " +
        "work. Off by default.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_LETTERBOXD)

    execute {
        memberGetMemberStatusFingerprint.method.apply {
            replaceInstruction(
                0,
                "sget-object v0, Lcom/letterboxd/api/model/MemberStatus;->Patron:Lcom/letterboxd/api/model/MemberStatus;",
            )
        }
    }
}
