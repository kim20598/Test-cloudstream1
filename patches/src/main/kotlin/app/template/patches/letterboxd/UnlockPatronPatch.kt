package app.template.patches.letterboxd

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_LETTERBOXD
import com.android.tools.smali.dexlib2.AccessFlags

private const val COMPANION =
    "Lcom/letterboxd/api/model/MemberStatus\$Companion;"
private const val MEMBER_STATUS =
    "Lcom/letterboxd/api/model/MemberStatus;"

/**
 * `MemberStatus.Companion.valueOf(String)` — the string → enum parser Kotlin serialization
 * runs on every server response that carries a `memberStatus` field.
 *
 * Forcing this method to return `Patron` makes the enum stored on the `Member` object genuinely
 * `Patron`, so the getter, the serializer, and everything downstream read the same value. (The
 * earlier getter hook broke the serialization round-trip — the field said `Member` but reads
 * claimed `Patron`. The setter hook didn't work because `memberStatus` is a `val`. This parser
 * is the only point where the value is set exactly once and safely.)
 */
internal object MemberStatusValueOfFingerprint : Fingerprint(
    definingClass = COMPANION,
    name = "valueOf",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = MEMBER_STATUS,
    parameters = listOf("Ljava/lang/String;"),
)

@Suppress("unused")
val unlockPatronPatch = bytecodePatch(
    name = "Force Patron (local)",
    description = "Makes the app treat your own account as Patron locally, so Patron-only " +
        "screens and pickers appear. Purely cosmetic — the server still knows the real tier, " +
        "so anything that saves (posters, backdrops) or fetches Patron-only data will not " +
        "actually work. Off by default.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_LETTERBOXD)

    execute {
        MemberStatusValueOfFingerprint.method.apply {
            // Prepend: immediately return Patron, ignoring the input string. The rest of the
            // original method becomes unreachable dead code — Dalvik tolerates that fine.
            addInstructions(
                0,
                """
                    sget-object p1, $MEMBER_STATUS->Patron:$MEMBER_STATUS
                    return-object p1
                """.trimIndent(),
            )
        }
    }
}
