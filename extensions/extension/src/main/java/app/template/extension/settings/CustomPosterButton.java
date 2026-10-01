package app.template.extension.settings;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runtime for the "Custom poster" feature. Three entry points:
 *
 * <ul>
 *   <li>{@link #maybeOverridePoster} — called from the {@code PosterView.setImage} hook every
 *       time a poster is about to render. If the user has a stored custom URL for this film,
 *       substitutes it via {@code PosterView.setImageURL}.</li>
 *   <li>{@link #injectRow} — called from {@code FilmActionsFragment.onViewCreated}. Inserts a
 *       "Custom poster" row into the action sheet, right below "Change poster / backdrop".</li>
 *   <li>{@link #openPickerForFragment} — internal, opens the picker when the row is tapped.</li>
 * </ul>
 *
 * <p>All reflection is wrapped in try/catch — an exception here would take down the host view.
 */
public final class CustomPosterButton {

    private static final String TAG_ROW = "morphe_custom_poster_row";
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

    // --- entry point 2: row injection -----------------------------------

    /**
     * Called from the injected hook at the top of {@code FilmActionsFragment.onViewCreated}.
     *
     * <p>Reads the fragment's {@code binding} field, walks to {@code binding.userButtonsView}
     * (the LinearLayout holding the action rows), finds {@code buttonChangePoster}, and inserts
     * a new row directly below it. The new row is styled to match its neighbours and opens the
     * picker on tap.
     */
    public static void injectRow(Object fragment) {
        try {
            if (fragment == null) return;

            Object binding = readField(fragment, "binding");
            if (binding == null) return;

            Object userButtonsViewObj = readField(binding, "userButtonsView");
            if (!(userButtonsViewObj instanceof ViewGroup)) return;
            ViewGroup container = (ViewGroup) userButtonsViewObj;

            Object changePosterObj = readField(binding, "buttonChangePoster");
            if (!(changePosterObj instanceof View)) return;
            View reference = (View) changePosterObj;

            if (container.findViewWithTag(TAG_ROW) != null) return; // already injected

            Context ctx = reference.getContext();
            if (ctx == null) return;

            Button row = new Button(ctx);
            row.setTag(TAG_ROW);
            row.setText("Custom poster");
            row.setAllCaps(false);
            row.setBackground(null);
            row.setGravity(Gravity.CENTER_VERTICAL);

            if (reference instanceof TextView) {
                TextView ref = (TextView) reference;
                row.setTextSize(TypedValue.COMPLEX_UNIT_PX, ref.getTextSize());
                row.setTextColor(ref.getCurrentTextColor());
                row.setTypeface(ref.getTypeface());
                row.setPadding(ref.getPaddingLeft(), ref.getPaddingTop(),
                        ref.getPaddingRight(), ref.getPaddingBottom());
                if (ref.getMinHeight() > 0) row.setMinHeight(ref.getMinHeight());
            }

            final Object fragRef = fragment;
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    openPickerForFragment(fragRef);
                }
            });

            int idx = container.indexOfChild(reference);
            if (idx < 0) idx = container.getChildCount() - 1;
            ViewGroup.LayoutParams lp = reference.getLayoutParams();
            container.addView(row, idx + 1, lp);
        } catch (Throwable ignored) {
        }
    }

    // --- internal: open picker ------------------------------------------

    private static void openPickerForFragment(Object fragment) {
        try {
            Method getFilmSummary = fragment.getClass().getMethod("getFilmSummary");
            Object filmSummary = getFilmSummary.invoke(fragment);
            if (filmSummary == null) return;

            Method requireContext = fragment.getClass().getMethod("requireContext");
            Context ctx = (Context) requireContext.invoke(fragment);
            if (ctx == null) return;

            Prefs.load(ctx);

            String slug = reflectString(filmSummary, "getId");
            if (slug == null || slug.isEmpty()) return;
            String imdbId = reflectImdbId(filmSummary);

            CustomPosterDialog dialog = new CustomPosterDialog(ctx, slug, imdbId,
                    new CustomPosterDialog.OnChange() {
                        @Override public void onChange(String newUrl) {
                            // Poster redraws on next render.
                        }
                    });
            dialog.show();
        } catch (Throwable ignored) {
        }
    }

    // --- reflection helpers ---------------------------------------------

    private static Object readField(Object target, String name) {
        try {
            Field f = target.getClass().getField(name);
            return f.get(target);
        } catch (Throwable t) {
            try {
                Field f = target.getClass().getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (Throwable t2) {
                return null;
            }
        }
    }

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
    public static void __cacheBustV3() {
        // intentionally empty
    }
}
