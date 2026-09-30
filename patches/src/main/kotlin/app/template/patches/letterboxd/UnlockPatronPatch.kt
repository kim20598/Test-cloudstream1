package app.template.patches.letterboxd

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.methodFingerprint
import app.template.patches.shared.Constants.COMPATIBILITY_LETTERBOXD
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Forces the current user's `MemberStatus` to `Patron` locally, so Patron-only UI (poster and
 * backdrop pickers, custom lists UI, etc.) appears without the account actually being a Patron.
 *
 * <p>This is a local-only change. Anything the server validates — saving a poster, listing stats,
 * push notifications — still goes through Letterboxd's servers, sees a non-Patron account, and
 * behaves accordingly. The unlock is cosmetic: it makes the app *try* to show the feature.
 *
 * <p>Hooks {@code Member.getMemberStatus()} — the getter used for the logged-in user's own
 * profile — and rewrites its body to return the `Patron` enum constant unconditionally.
 *
 * <p>Note: {@code MemberSummary.getMemberStatus()} (the sibling class used in lists and feeds for
 * *other* users) is intentionally left alone, so this patch does not make every other account on
 * Letterboxd appear as a Patron.
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
        val getMemberStatus = methodFingerprint(
            returnType = "Lcom/letterboxd/api/model/MemberStatus;",
            accessFlags = AccessFlags.PUBLIC or AccessFlags.FINAL,
            strings = listOf("getMemberStatus"),
            customFingerprint = { method, classDef ->
                classDef.type == "Lcom/letterboxd/api/model/Member;"
            },
        )

        getMemberStatus.method.apply {
            // Replace the body: return MemberStatus.Patron unconditionally.
            // `MemberStatus.Patron` is a static enum constant, so `sget-object` fetches it.
            instructions().clear()
            addInstructions(
                0,
                """
                    sget-object v0, Lcom/letterboxd/api/model/MemberStatus;->Patron:Lcom/letterboxd/api/model/MemberStatus;
                    return-object v0
                """.trimIndent(),
            )
        }
    }
}
