package app.template.extension.settings;

import android.content.Context;

import androidx.fragment.app.Fragment;

import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runtime for the "Custom poster" entry point.
 *
 * <p>The Kotlin patch hooks {@code ActionSheetsKt.showFilmActionSheet(fragment, filmSummary)}.
 * That method runs every time the user long-presses a poster in a list or taps the 3-dot menu
 * on a film page. We get called first — fire-and-forget — and if the setup makes sense, we
 * show our {@link CustomPosterDialog}. The original method then continues and paints the app's
 * own sheet underneath.
 *
 * <p>Everything is wrapped in try/catch. A failure inside this class never breaks the app's
 * normal action sheet — that's why the Kotlin hook uses no labels and no early return.
 */
public final class CustomPosterButton {

    private static final Pattern IMDB = Pattern.compile("(tt\\d+)");

    private CustomPosterButton() {}

    /**
     * Fire-and-forget entry point. Called from the injected hook before the app's own action
     * sheet renders. Shows the custom-poster dialog if we can resolve a film slug; otherwise
     * returns silently and the app's sheet appears unchanged.
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
            // Never let our code crash the action sheet.
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
}
