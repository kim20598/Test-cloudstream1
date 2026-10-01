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
private const val FILM_HEADER_FRAGMENT =
    "Lcom/letterboxd/letterboxd/ui/fragments/film/FilmHeaderFragment;"
private const val FILM =
    "Lcom/letterboxd/api/model/Film;"
private const val FRAGMENT_FILM_HEADER_BINDING =
    "Lcom/letterboxd/letterboxd/databinding/FragmentFilmHeaderBinding;"
private const val SETTINGS_APP_ICON_FRAGMENT =
    "Lcom/letterboxd/letterboxd/ui/fragments/user/SettingsAppIconFragment;"

internal object PosterViewSetImageFingerprint : Fingerprint(
    definingClass = POSTER_VIEW,
    name = "setImage",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(IMAGE, "I", "Lkotlin/jvm/functions/Function0;"),
)

internal object FilmActionsOnViewCreatedFingerprint : Fingerprint(
    definingClass = FILM_ACTIONS_FRAGMENT,
    name = "onViewCreated",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = listOf("Landroid/view/View;", "Landroid/os/Bundle;"),
)

/**
 * `FilmHeaderFragment.configureBackdrop(FragmentFilmHeaderBinding, Film)` — private, called
 * when the film page renders its header image. We prepend a call that hands the binding + film
 * to our helper, which swaps in a stored custom URL if one exists.
 */
internal object FilmHeaderConfigureBackdropFingerprint : Fingerprint(
    definingClass = FILM_HEADER_FRAGMENT,
    name = "configureBackdrop",
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(FRAGMENT_FILM_HEADER_BINDING, FILM),
)

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
        "film's action sheet, swaps in a custom film backdrop if you've set one, plus unlocks " +
        "all Pro app icons (which are a local toggle). Pick a poster or backdrop from TMDB or " +
        "paste any image URL — saved locally and included in Mod settings export/import.",
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

        // 3. Film backdrop override — FilmHeaderFragment.configureBackdrop(binding, film).
        //    .registers 9, 3 params → p0 = v6, p1 = v7, p2 = v8.
        FilmHeaderConfigureBackdropFingerprint.method.apply {
            addInstruction(
                0,
                "invoke-static {v7, v8}, Lapp/template/extension/settings/CustomPosterButton;->maybeOverrideFilmBackdrop(Ljava/lang/Object;Ljava/lang/Object;)V",
            )
        }

        // 4. App icon unlock — SettingsAppIconFragment.getCanChangeAppIcon()Z.
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
