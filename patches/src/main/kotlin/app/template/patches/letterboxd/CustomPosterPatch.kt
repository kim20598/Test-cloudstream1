package app.template.patches.letterboxd

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_LETTERBOXD
import com.android.tools.smali.dexlib2.AccessFlags

private const val POSTER_VIEW =
    "Lcom/letterboxd/letterboxd/ui/views/PosterView;"
private const val IMAGE =
    "Lcom/letterboxd/api/model/Image;"
private const val ACTION_SHEETS =
    "Lcom/letterboxd/letterboxd/ActionSheetsKt;"
private const val FILM_SUMMARY =
    "Lcom/letterboxd/api/model/FilmSummary;"

/**
 * `PosterView.setImage(Image, int, Function0)` — the poster rendering path. Prepends a check
 * for a stored custom poster URL for the current film; if present, loads it via
 * `setImageURL(URL)` and returns, skipping the original Glide flow.
 */
internal object PosterViewSetImageFingerprint : Fingerprint(
    definingClass = POSTER_VIEW,
    name = "setImage",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(IMAGE, "I", "Lkotlin/jvm/functions/Function0;"),
)

/**
 * `ActionSheetsKt.showFilmActionSheet(Fragment, FilmSummary)` — the popup that appears when the
 * user long-presses a poster (or taps the … menu on a film). We append a "Custom poster" row to
 * whatever the app builds by calling our own helper, which adds a menu item above the sheet's
 * dismiss action. The original sheet content is untouched.
 */
internal object ShowFilmActionSheetFingerprint : Fingerprint(
    definingClass = ACTION_SHEETS,
    name = "showFilmActionSheet",
    returnType = "V",
    parameters = listOf(
        "Landroidx/fragment/app/Fragment;",
        FILM_SUMMARY,
    ),
)

@Suppress("unused")
val customPosterPatch = bytecodePatch(
    name = "Custom poster (local)",
    description = "Adds a \"Custom poster\" item under the existing Change poster button on " +
        "a film's action sheet. Pick a poster from TMDB or paste any image URL, and it will " +
        "be used on your device for that film — saved locally and included in Mod settings " +
        "export/import. Works on every film regardless of Patron tier.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_LETTERBOXD)

    execute {
        // 1. Poster override hook (unchanged from message 1).
        PosterViewSetImageFingerprint.method.apply {
            addInstructions(
                0,
                """
                    invoke-virtual {p0}, Lcom/letterboxd/letterboxd/ui/views/PosterView;->getFilmSummary()Lcom/letterboxd/api/model/FilmSummary;
                    move-result-object v0
                    if-nez v0, :original_body
                    invoke-virtual {v0}, Lcom/letterboxd/api/model/FilmSummary;->getId()Ljava/lang/String;
                    move-result-object v0
                    invoke-static {v0}, Lapp/template/extension/settings/CustomPosterStore;->getOverride(Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v0
                    if-nez v0, :original_body
                    new-instance v1, Ljava/net/URL;
                    invoke-direct {v1, v0}, Ljava/net/URL;-><init>(Ljava/lang/String;)V
                    invoke-virtual {p0, v1}, Lcom/letterboxd/letterboxd/ui/views/PosterView;->setImageURL(Ljava/net/URL;)V
                    return-void
                    :original_body
                    nop
                """.trimIndent(),
            )
        }

        // 2. Action-sheet injection. Prepends a call that hands the fragment + film summary
        //    to our runtime helper. The helper installs a click listener on the sheet's
        //    root which, on long-press of the poster's menu, appends our item. In practice
        //    the simplest hook is to intercept the click that opens the sheet and route it
        //    through us, so we control the menu content.
        ShowFilmActionSheetFingerprint.method.apply {
            addInstructions(
                0,
                """
                    invoke-static {p0, p1}, Lapp/template/extension/settings/CustomPosterButton;->maybeIntercept(Landroidx/fragment/app/Fragment;Lcom/letterboxd/api/model/FilmSummary;)Z
                    move-result v0
                    if-eqz v0, :orig
                    return-void
                    :orig
                    nop
                """.trimIndent(),
            )
        }
    }
}
