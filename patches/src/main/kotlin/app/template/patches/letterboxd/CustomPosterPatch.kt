package app.template.patches.letterboxd

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_LETTERBOXD
import com.android.tools.smali.dexlib2.AccessFlags

private const val FILM_ACTIONS_FRAGMENT =
    "Lcom/letterboxd/letterboxd/ui/fragments/film/FilmActionsFragment;"
private const val FILM_HEADER_FRAGMENT =
    "Lcom/letterboxd/letterboxd/ui/fragments/film/FilmHeaderFragment;"
private const val FILM =
    "Lcom/letterboxd/api/model/Film;"
private const val FRAGMENT_FILM_HEADER_BINDING =
    "Lcom/letterboxd/letterboxd/databinding/FragmentFilmHeaderBinding;"
private const val MEMBER_HEADER_FRAGMENT =
    "Lcom/letterboxd/letterboxd/ui/fragments/member/MemberHeaderFragment;"
private const val MEMBER =
    "Lcom/letterboxd/api/model/Member;"
private const val SETTINGS_APP_ICON_FRAGMENT =
    "Lcom/letterboxd/letterboxd/ui/fragments/user/SettingsAppIconFragment;"

/**
 * `FilmActionsFragment.onViewCreated` — injects the three rows (poster / backdrop / profile
 * backdrop) into the film action sheet.
 */
internal object FilmActionsOnViewCreatedFingerprint : Fingerprint(
    definingClass = FILM_ACTIONS_FRAGMENT,
    name = "onViewCreated",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = listOf("Landroid/view/View;", "Landroid/os/Bundle;"),
)

/**
 * `FilmHeaderFragment.configureBackdrop(binding, film)` — runs when the film page renders
 * its header image. We prepend a hook that, if a custom backdrop exists, posts a Coil load
 * to the header ImageView after the original Glide load — instant replacement, no flicker.
 */
internal object FilmHeaderConfigureBackdropFingerprint : Fingerprint(
    definingClass = FILM_HEADER_FRAGMENT,
    name = "configureBackdrop",
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(FRAGMENT_FILM_HEADER_BINDING, FILM),
)

/**
 * `MemberHeaderFragment.applyMember(Member)` — runs on profile data load. Same pattern as
 * film backdrop: post a Coil load to the userBackdrop ImageView after the original Glide
 * load completes.
 */
internal object MemberHeaderApplyMemberFingerprint : Fingerprint(
    definingClass = MEMBER_HEADER_FRAGMENT,
    name = "applyMember",
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(MEMBER),
)

/**
 * `SettingsAppIconFragment.getCanChangeAppIcon()` — force to true so Pro app icons are
 * pickable. The icon swap is a purely local manifest toggle.
 */
internal object SettingsAppIconCanChangeFingerprint : Fingerprint(
    definingClass = SETTINGS_APP_ICON_FRAGMENT,
    name = "getCanChangeAppIcon",
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
)

@Suppress("unused")
val customPosterPatch = bytecodePatch(
    name = "Custom poster (local)",
    description = "Adds \"Custom poster\", \"Custom backdrop\", and \"Use as profile backdrop\" " +
        "rows to a film's action sheet. Custom images are stored locally, applied instantly, " +
        "and included in Mod settings export/import. Also unlocks all Pro app icons (a purely " +
        "local toggle).",
    default = false,
) {
    compatibleWith(COMPATIBILITY_LETTERBOXD)

    execute {
        // 1. Inject the three rows into the film action sheet.
        FilmActionsOnViewCreatedFingerprint.method.apply {
            addInstruction(
                0,
                "invoke-static {p0}, Lapp/template/extension/settings/CustomPosterButton;->injectRow(Ljava/lang/Object;)V",
            )
        }

        // 2. Film backdrop override — FilmHeaderFragment.configureBackdrop(binding, film).
        //    .registers 9, 3 params → p0 = v6, p1 = v7, p2 = v8.
        FilmHeaderConfigureBackdropFingerprint.method.apply {
            addInstruction(
                0,
                "invoke-static {v7, v8}, Lapp/template/extension/settings/CustomPosterButton;->maybeOverrideFilmBackdrop(Ljava/lang/Object;Ljava/lang/Object;)V",
            )
        }

        // 3. Profile backdrop override — MemberHeaderFragment.applyMember(Member).
        //    Prepend with p0 = the fragment.
        MemberHeaderApplyMemberFingerprint.method.apply {
            addInstruction(
                0,
                "invoke-static {p0}, Lapp/template/extension/settings/CustomPosterButton;->maybeOverrideProfileBackdrop(Ljava/lang/Object;)V",
            )
        }

        // 4. App icon unlock — force getCanChangeAppIcon() to return true.
        SettingsAppIconCanChangeFingerprint.method.apply {
            addInstructions(
                0,
                """
                    const/4 v0, 0x1
                    return v0
                """.trimIndent(),
            )
        }
    }
}
