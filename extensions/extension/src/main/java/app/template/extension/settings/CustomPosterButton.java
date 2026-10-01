package app.template.extension.settings;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import java.lang.reflect.Method;

/**
 * Runtime for the "Custom poster" entry point.
 *
 * <p>The Kotlin patch hooks {@code ActionSheetsKt.showFilmActionSheet(fragment, filmSummary)}.
 * When that method runs — i.e. the user long-pressed a poster, or tapped the … menu — we get
 * first crack at it. We read the film's slug and IMDb id (via reflection on FilmSummary), then
 * show our own {@link CustomPosterDialog} instead of the sheet. Users who want the original
 * sheet (e.g. real Patrons using Change poster / backdrop) can still reach it — see below.
 *
 * <p>If the user's preference is "show both", we also fall through and let the original sheet
 * open after ours has closed. Default is our dialog only, because the sheet is mostly useful
 * only to Patrons.
 */
public final class CustomPosterButton {

    private CustomPosterButton() {}

    /**
     * Called from the hooked action-sheet method. Returns true if we handled the click and the
     * caller should return early, false if the caller should proceed to the original sheet.
     */
    public static boolean maybeIntercept(Fragment fragment, Object filmSummary) {
        try {
            if (fragment == null || filmSummary == null) return false;

            Prefs.load(fragment.requireContext());
            if (!Prefs.has(Prefs.KEY_CUSTOM_POSTERS) && !TmdbClient.isConfigured()
                    && !Prefs.getBoolean(Prefs.KEY_OPEN_IN_PLAYER, false)) {
                // The patch is enabled (otherwise we wouldn't be here), but the user has
                // neither a TMDB key nor a custom poster yet. Still show our dialog — that's
                // how they'll set their first override.
            }

            String slug = reflectString(filmSummary, "getId");
            String imdbId = reflectImdbId(filmSummary);
            if (slug == null || slug.isEmpty()) {
                // No id — let the app show its own sheet, since we can't key the override.
                return false;
            }

            final Context ctx = fragment.requireContext();
            final String filmSlug = slug;
            final String filmImdb = imdbId;

            CustomPosterDialog dialog = new CustomPosterDialog(ctx, filmSlug, filmImdb,
                    new CustomPosterDialog.OnChange() {
                        @Override public void onChange(String newUrl) {
                            // Nothing to do — PosterView will pick up the change on next render.
                            // If the user is currently viewing the film, they can pull-to-refresh
                            // or navigate away and back to see it.
                        }
                    });
            dialog.show();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // --- reflection -----------------------------------------------------

    private static String reflectString(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            Object result = m.invoke(target);
            return result == null ? null : result.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Extracts the IMDb id from a FilmSummary's {@code getLinks()} list, if present. */
    private static String reflectImdbId(Object filmSummary) {
        try {
            Method getLinks = filmSummary.getClass().getMethod("getLinks");
            Object linksObj = getLinks.invoke(filmSummary);
            if (!(linksObj instanceof java.util.List)) return null;
            for (Object link : (java.util.List<?>) linksObj) {
                Method getType = link.getClass().getMethod("getType");
                Object type = getType.invoke(link);
                if (type == null) continue;
                if (!"Imdb".equals(type.getClass().getSimpleName())) continue;
                Method getUrl = link.getClass().getMethod("getUrl");
                Object url = getUrl.invoke(link);
                if (url == null) continue;
                java.util.regex.Matcher m =
                        java.util.regex.Pattern.compile("(tt\\d+)").matcher(url.toString());
                if (m.find()) return m.group(1);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
