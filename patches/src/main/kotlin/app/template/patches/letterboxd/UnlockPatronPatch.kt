package app.template.patches.letterboxd

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_LETTERBOXD
import com.android.tools.smali.dexlib2.AccessFlags

private const val MEMBER = "Lcom/letterboxd/api/model/Member;"
private const val MEMBER_STATUS = "Lcom/letterboxd/api/model/MemberStatus;"

/**
 * `Member.setMemberStatus(MemberStatus)` — the Kotlin-generated setter for the `memberStatus`
 * property. Forcing the stored field (rather than the getter) keeps JSON serialization
 * consistent: whatever the server sent gets overwritten with `Patron` at the moment it's
 * decoded, so every subsequent getter call returns the real stored value.
 */
internal object MemberSetMemberStatusFingerprint : Fingerprint(
    definingClass = MEMBER,
    name = "setMemberStatus",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(MEMBER_STATUS),
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
        MemberSetMemberStatusFingerprint.method.apply {
            // Replace the setter body so the field is always written as Patron, regardless of
            // what the server's JSON decoded to.
            replaceInstruction(
                0,
                "sget-object p1, $MEMBER_STATUS->Patron:$MEMBER_STATUS",
            )
            replaceInstruction(
                1,
                "iput-object p1, p0, $MEMBER->memberStatus:$MEMBER_STATUS",
            )
            replaceInstruction(
                2,
                "return-void",
            )
        }
    }
}
