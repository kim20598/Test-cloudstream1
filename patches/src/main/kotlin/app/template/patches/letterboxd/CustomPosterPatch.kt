package app.template.patches.letterboxd

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_LETTERBOXD
import com.android.tools.smali.dexlib2.AccessFlags

private const val POSTER_VIEW =
    "Lcom/letterboxd/letterboxd/ui/views/PosterView;"
private const val IMAGE =
    "Lcom/letterboxd/api/model/Image;"
private const val FILM_ACTIONS_FRAGMENT =
    "Lcom/letterboxd/letterboxd/ui/fragments/film/FilmActionsFragment;"
private const val SETTINGS_APP_ICON_FRAGMENT =
    "Lcom/letterboxd/letterboxd/ui/fragments/user/SettingsAppIconFragment;"

/**
 * `PosterView.setImage(Image, int, Function0)` — poster rendering. Prepends a check for a
 * stored custom poster URL, loads it via setImageURL if present.
 */
internal object PosterViewSetImageFingerprint : Fingerprint(
    definingClass = POSTER_VIEW,
    name = "setImage",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(IMAGE, "I", "Lkotlin/jvm/functions/Function0;"),
)

/**
 * `FilmActionsFragment.onViewCreated(View, Bundle)` — runs once when the sheet is created.
 * We prepend a call that injects our "Custom poster" row into the action list, right under
 * "Change poster / backdrop".
 */
internal object FilmActionsOnViewCreatedFingerprint : Fingerprint(
    definingClass = FILM_ACTIONS_FRAGMENT,
    name = "onViewCreated",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = listOf(
        "Landroid/view/View;",
        "Landroid/os/Bundle;",
    ),
)

/**
 * `SettingsAppIconFragment.getCanChangeAppIcon()` — private, returns whether the user is
 * allowed to pick a Pro-exclusive app icon. The click handler in the same class checks this
 * and, when false, routes Pro icons to the Upgrade screen instead of the confirmation dialog.
 *
 * Forcing it to `true` makes every icon behave like a free one: tap → confirmation dialog →
 * change. The icon swap is entirely local (a manifest activity-alias toggle) so it truly works.
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
    description = "Adds a \"Custom poster\" row under the existing Change poster button on a " +
        "film's action sheet, plus unlocks all Pro app icons (which are a local toggle). Pick " +
        "a poster from TMDB or paste any image URL — saved locally and included in Mod " +
        "settings export/import. Works on every film regardless of Patron tier.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_LETTERBOXD)

    execute {
        // 1. Poster override — PosterView.setImage(Image, int, Function0). p0 = v3.
        PosterViewSetImageFingerprint.method.apply {
            addInstruction(
                0,
                "invoke-static {v3}, Lapp/template/extension/settings/CustomPosterButton;->maybeOverridePoster(Ljava/lang/Object;)V",
            )
        }

        // 2. Row injection — FilmActionsFragment.onViewCreated(View, Bundle). p0 = fragment.
        FilmActionsOnViewCreatedFingerprint.method.apply {
            addInstruction(
                0,
                "invoke-static {p0}, Lapp/template/extension/settings/CustomPosterButton;->injectRow(Ljava/lang/Object;)V",
            )
        }

        // 3. App icon unlock — SettingsAppIconFragment.getCanChangeAppIcon()Z. Force return
        //    true. The original body reads a Lazy boolean; we prepend an unconditional
        //    `return true` so the rest never runs.
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
