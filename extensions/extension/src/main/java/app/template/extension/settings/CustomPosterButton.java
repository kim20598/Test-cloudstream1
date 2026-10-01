package app.template.extension.settings;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runtime for the "Custom poster" feature. Four entry points:
 *
 * <ul>
 *   <li>{@link #maybeOverridePoster} — from {@code PosterView.setImage}. Swaps in a custom
 *       poster URL if one is stored for the current film.</li>
 *   <li>{@link #injectRow} — from {@code FilmActionsFragment.onViewCreated}. Adds our
 *       "Custom poster" row to the action sheet.</li>
 *   <li>{@link #maybeOverrideFilmBackdrop} — from {@code FilmHeaderFragment.configureBackdrop}.
 *       Swaps in a custom film backdrop URL if one is stored.</li>
 *   <li>{@link #refreshVisiblePoster} — from the picker after saving, refreshes every visible
 *       PosterView for the film so the change is instant.</li>
 * </ul>
 */
public final class CustomPosterButton {

    private static final String TAG_ROW = "morphe_custom_poster_row";
    private static final Pattern IMDB = Pattern.compile("(tt\\d+)");

    private CustomPosterButton() {}

    // --- entry point 1: poster override ---------------------------------

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

            if (container.findViewWithTag(TAG_ROW) != null) return;

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

    // --- entry point 3: film backdrop override --------------------------

    /**
     * Called from the injected hook at the top of {@code FilmHeaderFragment.configureBackdrop}.
     * Reads the film's slug from the Film argument (via getSummary), looks for a stored
     * backdrop override, and — if found — loads it directly into the binding's
     * {@code headerImageView} via Coil. The original method still runs, but our image wins
     * because it loads later and Coil replaces the drawable.
     */
    public static void maybeOverrideFilmBackdrop(Object binding, Object film) {
        try {
            if (binding == null || film == null) return;

            String slug = extractFilmSlug(film);
            if (slug == null || slug.isEmpty()) return;

            String customUrl = CustomPosterStore.getBackdropOverride(slug);
            if (customUrl == null || customUrl.isEmpty()) return;

            Object headerImageView = readField(binding, "headerImageView");
            if (!(headerImageView instanceof ImageView)) return;
            ImageView iv = (ImageView) headerImageView;

            CoilLoader.load(iv.getContext(), customUrl, iv);
        } catch (Throwable ignored) {
        }
    }

    /** Best-effort slug extraction: try Film.getSummary().getId(), fall back to Film.getId(). */
    private static String extractFilmSlug(Object film) {
        try {
            Method getSummary = film.getClass().getMethod("getSummary");
            Object summary = getSummary.invoke(film);
            if (summary != null) {
                Method getId = summary.getClass().getMethod("getId");
                Object id = getId.invoke(summary);
                if (id != null) return id.toString();
            }
        } catch (Throwable ignored) { }

        try {
            Method getId = film.getClass().getMethod("getId");
            Object id = getId.invoke(film);
            if (id != null) return id.toString();
        } catch (Throwable ignored) { }

        return null;
    }

    // --- entry point 4: instant refresh ---------------------------------

    public static void refreshVisiblePoster(Context ctx, String filmSlug, String newUrl) {
        try {
            if (ctx == null || filmSlug == null) return;

            Activity activity = findActivity(ctx);
            if (activity == null || activity.getWindow() == null) return;

            View root = activity.getWindow().getDecorView();
            refreshInViewTree(root, filmSlug, newUrl);
        } catch (Throwable ignored) {
        }
    }

    private static void refreshInViewTree(View view, String filmSlug, String newUrl) {
        try {
            if (view == null) return;

            if (view.getClass().getName()
                    .equals("com.letterboxd.letterboxd.ui.views.PosterView")) {
                Method getFilmSummary = view.getClass().getMethod("getFilmSummary");
                Object filmSummary = getFilmSummary.invoke(view);
                if (filmSummary != null) {
                    Method getId = filmSummary.getClass().getMethod("getId");
                    Object id = getId.invoke(filmSummary);
                    if (id != null && filmSlug.equals(id.toString())) {
                        Method setImageURL = view.getClass()
                                .getMethod("setImageURL", URL.class);
                        if (newUrl == null || newUrl.isEmpty()) {
                            setImageURL.invoke(view, new Object[]{null});
                        } else {
                            setImageURL.invoke(view, new URL(newUrl));
                        }
                    }
                }
            }

            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    refreshInViewTree(group.getChildAt(i), filmSlug, newUrl);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static Activity findActivity(Context ctx) {
        try {
            if (ctx instanceof Activity) return (Activity) ctx;
            if (ctx instanceof ContextWrapper) {
                Context c = ctx;
                while (c instanceof ContextWrapper) {
                    if (c instanceof Activity) return (Activity) c;
                    c = ((ContextWrapper) c).getBaseContext();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
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
                            // Picker already refreshes visible views.
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

    public static void __cacheBustV5() {
        // intentionally empty
    }
}
