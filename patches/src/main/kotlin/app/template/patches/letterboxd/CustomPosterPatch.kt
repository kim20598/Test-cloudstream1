package app.template.patches.letterboxd

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_LETTERBOXD
import com.android.tools.smali.dexlib2.AccessFlags

private const val POSTER_VIEW =
    "Lcom/letterboxd/letterboxd/ui/views/PosterView;"
private const val IMAGE =
    "Lcom/letterboxd/api/model/Image;"
private const val FILM_ACTIONS_FRAGMENT =
    "Lcom/letterboxd/letterboxd/ui/fragments/film/FilmActionsFragment;"

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
 * We append a call that injects our "Custom poster" row into the action list, right under
 * "Change poster / backdrop". Native-looking, no dialog overlay on top of the sheet.
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

@Suppress("unused")
val customPosterPatch = bytecodePatch(
    name = "Custom poster (local)",
    description = "Adds a \"Custom poster\" row under the existing Change poster button on a " +
        "film's action sheet. Pick a poster from TMDB or paste any image URL, and it will be " +
        "used on your device for that film — saved locally and included in Mod settings " +
        "export/import. Works on every film regardless of Patron tier.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_LETTERBOXD)

    execute {
        // 1. Poster override — PosterView.setImage(Image, int, Function0). .registers 6,
        //    3 params, p0 = v3.
        PosterViewSetImageFingerprint.method.apply {
            addInstruction(
                0,
                "invoke-static {v3}, Lapp/template/extension/settings/CustomPosterButton;->maybeOverridePoster(Ljava/lang/Object;)V",
            )
        }

        // 2. Row injection — FilmActionsFragment.onViewCreated(View, Bundle). 2 params,
        //    p0 = the fragment. Prepends a call that walks the fragment's binding and
        //    inserts our row into userButtonsView, right after buttonChangePoster.
        FilmActionsOnViewCreatedFingerprint.method.apply {
            addInstruction(
                0,
                "invoke-static {p0}, Lapp/template/extension/settings/CustomPosterButton;->injectRow(Ljava/lang/Object;)V",
            )
        }
    }
}
