package app.template.extension.settings;

import android.content.Context;

import androidx.fragment.app.Fragment;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runtime for the "Custom poster" feature. Two entry points:
 *
 * <ul>
 *   <li>{@link #offerDialog} — called from the action-sheet hook when the user long-presses a
 *       poster or opens the film's 3-dot menu. Shows the picker.</li>
 *   <li>{@link #maybeOverridePoster} — called from the {@code PosterView.setImage} hook every
 *       time a poster is about to render. If the user has a stored custom URL for this film,
 *       substitutes it via {@code PosterView.setImageURL}.</li>
 * </ul>
 *
 * <p>Everything is reflection-based and wrapped in try/catch, because we're reaching into
 * Letterboxd's runtime from inside a bytecode hook — an exception here would take down the
 * whole view.
 */
public final class CustomPosterButton {

    private static final Pattern IMDB = Pattern.compile("(tt\\d+)");

    private CustomPosterButton() {}

    // --- entry point 1: poster override ---------------------------------

    /**
     * Called from the injected hook at the top of {@code PosterView.setImage}.
     *
     * <p>If the film shown by this view has a custom poster stored, we call
     * {@code PosterView.setImageURL} to load it. The original {@code setImage} still runs, but
     * our Coil load immediately supersedes the Glide load it starts. In practice the custom
     * poster wins.
     *
     * <p>The parameter is declared as {@code Object} so the injected Dalvik call site doesn't
     * need the concrete {@code PosterView} class type — reflection handles the rest.
     */
    public static void maybeOverridePoster(Object posterView) {
        try {
            if (posterView == null) return;

            Method getFilmSummary = posterView.getClass().getMethod("getFilmSummary");
            Object filmSummary = getFilmSummary.invoke(posterView);
            if (filmSummary == null) return;

            Method getId = filmSummary.getClass().getMethod("getId");
            Object id = getId.invoke(filmSummary);
            if (id == null) return;
            String slug = id.toString();
            if (slug.isEmpty()) return;

            String customUrl = CustomPosterStore.getOverride(slug);
            if (customUrl == null || customUrl.isEmpty()) return;

            URL url = new URL(customUrl);
            Method setImageURL = posterView.getClass().getMethod("setImageURL", URL.class);
            setImageURL.invoke(posterView, url);
        } catch (Throwable ignored) {
        }
    }

    // --- entry point 2: action sheet ------------------------------------

    /**
     * Fire-and-forget entry point from the action-sheet hook. Shows the custom-poster dialog
     * if we can resolve a film slug. Wrapped so a failure never breaks the app's own sheet.
     */
    public static void offerDialog(Fragment fragment, Object filmSummary) {
        try {
            if (fragment == null || filmSummary == null) return;

            final Context ctx;
            try {
                ctx = fragment.requireContext();
            } catch (Throwable t) {
                return;
            }
            if (ctx == null) return;

            Prefs.load(ctx);

            String slug = reflectString(filmSummary, "getId");
            if (slug == null || slug.isEmpty()) return;

            final String filmSlug = slug;
            final String imdbId = reflectImdbId(filmSummary);

            CustomPosterDialog dialog = new CustomPosterDialog(ctx, filmSlug, imdbId,
                    new CustomPosterDialog.OnChange() {
                        @Override public void onChange(String newUrl) {
                            // Poster redraws on next render.
                        }
                    });
            dialog.show();
        } catch (Throwable ignored) {
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
            if (!(linksObj instanceof List)) return null;
            for (Object link : (List<?>) linksObj) {
                Method getType = link.getClass().getMethod("getType");
                Object type = getType.invoke(link);
                if (type == null) continue;
                if (!"Imdb".equals(type.getClass().getSimpleName())) continue;
                Method getUrl = link.getClass().getMethod("getUrl");
                Object url = getUrl.invoke(link);
                if (url == null) continue;
                Matcher m = IMDB.matcher(url.toString());
                if (m.find()) return m.group(1);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // --- cache buster ---------------------------------------------------

    /**
     * Marks the class as changed so Gradle's incremental compiler cannot reuse a previously
     * built {@code extension.mpe}. Called from nowhere; presence is the point.
     */
    public static void __cacheBustV2() {
        // intentionally empty
    }
}
