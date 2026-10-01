package app.template.patches.letterboxd

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
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
 * `ActionSheetsKt.showFilmActionSheet(Fragment, FilmSummary)` — the popup that appears when
 * the user long-presses a poster (in any list) or taps the 3-dot menu on a film page.
 *
 * We prepend a fire-and-forget call to our runtime helper. The helper shows the custom-poster
 * dialog if the user has one configured. The original method then continues normally and
 * displays the app's own sheet underneath — no early return, no label jumps, nothing that can
 * invalidate the DEX. If our helper throws, the try/catch inside it swallows the error and
 * the sheet still renders.
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
    description = "Adds a \"Custom poster\" option on a film's action sheet. Pick a poster from " +
        "TMDB or paste any image URL, and it will be used on your device for that film — saved " +
        "locally and included in Mod settings export/import. Works on every film regardless of " +
        "Patron tier.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_LETTERBOXD)

    execute {
        // 1. Poster override hook.
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

        // 2. Action-sheet hook — fire-and-forget. No labels, no early return, no move-result.
        //    Our helper runs first; the original method body then executes as usual.
        ShowFilmActionSheetFingerprint.method.apply {
            addInstructions(
                0,
                """
                    invoke-static {p0, p1}, Lapp/template/extension/settings/CustomPosterButton;->offerDialog(Landroidx/fragment/app/Fragment;Lcom/letterboxd/api/model/FilmSummary;)V
                """.trimIndent(),
            )
        }
    }
}
