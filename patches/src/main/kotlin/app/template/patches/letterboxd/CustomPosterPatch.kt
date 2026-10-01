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

/**
 * `PosterView.setImage(Image, int, Function0)` — the method that renders a film's poster.
 *
 * We prepend a check: if the user has set a custom poster URL for the film currently shown by
 * this view, we call `PosterView.setImageURL(URL)` (the public Coil-backed path) and return
 * early, skipping the original Glide pipeline. If no override exists, the original path runs.
 *
 * The override lookup is `CustomPosterStore.getOverride(slug)` — a static method in the
 * extension module. It reads a SharedPreferences-backed map of `filmSlug -> posterUrl`.
 */
internal object PosterViewSetImageFingerprint : Fingerprint(
    definingClass = POSTER_VIEW,
    name = "setImage",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(
        IMAGE,
        "I",
        "Lkotlin/jvm/functions/Function0;",
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
        PosterViewSetImageFingerprint.method.apply {
            // Prepend: check CustomPosterStore for an override for this film's slug.
            // If one exists, load it via setImageURL and return. Otherwise jump to the
            // original body.
            //
            // Call chain in the injected block:
            //   p0.getFilmSummary() -> FilmSummary (or null)
            //   FilmSummary.getId() -> String slug (or null)
            //   CustomPosterStore.getOverride(slug) -> String url (or null)
            //   new URL(url) -> java.net.URL
            //   p0.setImageURL(url) -> returns void
            //
            // The label `:original_body` must be followed by a real instruction — `nop` is
            // the standard placeholder.
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
    }
}
