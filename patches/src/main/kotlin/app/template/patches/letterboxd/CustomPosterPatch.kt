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
private const val ACTION_SHEETS =
    "Lcom/letterboxd/letterboxd/ActionSheetsKt;"
private const val FILM_SUMMARY =
    "Lcom/letterboxd/api/model/FilmSummary;"

internal object PosterViewSetImageFingerprint : Fingerprint(
    definingClass = POSTER_VIEW,
    name = "setImage",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(IMAGE, "I", "Lkotlin/jvm/functions/Function0;"),
)

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
        // PosterView.setImage(Image, int, Function0) — .registers 6, 3 params, so p0 = v3.
        PosterViewSetImageFingerprint.method.apply {
            addInstruction(
                0,
                "invoke-static {v3}, Lapp/template/extension/settings/CustomPosterButton;->maybeOverridePoster(Ljava/lang/Object;)V",
            )
        }

        // ActionSheetsKt.showFilmActionSheet(Fragment, FilmSummary) — .registers 9,
        // 2 params, so p0 = v7 and p1 = v8.
        ShowFilmActionSheetFingerprint.method.apply {
            addInstruction(
                0,
                "invoke-static {v7, v8}, Lapp/template/extension/settings/CustomPosterButton;->offerDialog(Landroidx/fragment/app/Fragment;Lcom/letterboxd/api/model/FilmSummary;)V",
            )
        }
    }
}
