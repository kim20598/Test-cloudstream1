package app.template.patches.letterboxd

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_LETTERBOXD
import com.android.tools.smali.dexlib2.AccessFlags

private const val MEMBER = "Lcom/letterboxd/api/model/Member;"

/**
 * `Member.getMemberStatus()` — the getter used for the logged-in user's own profile.
 *
 * Scoped to `Member` (not `MemberSummary`) on purpose: `MemberSummary` is used for *other* users
 * in lists and feeds, and forcing those to `Patron` breaks view-model code that expects otherwise.
 */
internal object MemberGetMemberStatusFingerprint : Fingerprint(
    definingClass = MEMBER,
    name = "getMemberStatus",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Lcom/letterboxd/api/model/MemberStatus;",
    parameters = listOf(),
)

/**
 * Forces the current user's `MemberStatus` to `Patron` locally, so Patron-only UI (poster and
 * backdrop pickers, etc.) appears without the account actually being a Patron.
 *
 * Local-only. Server-validated actions (saving a poster, stats data, push notifications) still
 * see a non-Patron account.
 */
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
        MemberGetMemberStatusFingerprint.method.apply {
            // Replace the first instruction (iget-object, reading the memberStatus field) with
            // sget-object fetching MemberStatus.Patron. The trailing `return-object v0` is left
            // in place, so the method now unconditionally returns Patron.
            replaceInstruction(
                0,
                "sget-object v0, Lcom/letterboxd/api/model/MemberStatus;->Patron:Lcom/letterboxd/api/model/MemberStatus;",
            )
        }
    }
}
