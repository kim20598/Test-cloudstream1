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
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runtime for the "Custom poster" feature.
 *
 * <p>Every override uses the same pattern: the hook prepends a call which checks for a stored
 * URL, and if found schedules a Coil load on the target ImageView's own message queue via
 * {@link View#post(Runnable)}. Because {@code post()} runs after the current frame's original
 * Glide load, Coil wins the race — the image is replaced instantly, no flicker, no re-entry.
 */
public final class CustomPosterButton {

    private static final String TAG_ROW_POSTER = "morphe_custom_poster_row";
    private static final String TAG_ROW_BACKDROP = "morphe_custom_backdrop_row";
    private static final String TAG_ROW_PROFILE = "morphe_profile_backdrop_row";
    private static final Pattern IMDB = Pattern.compile("(tt\\d+)");

    private CustomPosterButton() {}

    // --- film action sheet: inject the three rows -----------------------

    public static void injectRow(Object fragment) {
        try {
            if (fragment == null) return;
            Object binding = readField(fragment, "binding");
            if (binding == null) return;
            Object containerObj = readField(binding, "userButtonsView");
            if (!(containerObj instanceof ViewGroup)) return;
            ViewGroup container = (ViewGroup) containerObj;
            Object refObj = readField(binding, "buttonChangePoster");
            if (!(refObj instanceof View)) return;
            View reference = (View) refObj;

            if (container.findViewWithTag(TAG_ROW_POSTER) == null) {
                View row = buildRow(container, reference, "Custom poster", TAG_ROW_POSTER,
                        new View.OnClickListener() {
                            @Override public void onClick(View v) { openPicker(fragment, "poster"); }
                        });
                if (row != null) {
                    int idx = container.indexOfChild(reference);
                    if (idx < 0) idx = container.getChildCount() - 1;
                    container.addView(row, idx + 1, reference.getLayoutParams());
                }
            }

            if (container.findViewWithTag(TAG_ROW_BACKDROP) == null) {
                View anchorPoster = container.findViewWithTag(TAG_ROW_POSTER);
                View anchor = anchorPoster != null ? anchorPoster : reference;
                View row = buildRow(container, anchor, "Custom backdrop", TAG_ROW_BACKDROP,
                        new View.OnClickListener() {
                            @Override public void onClick(View v) { openPicker(fragment, "backdrop"); }
                        });
                if (row != null) {
                    int idx = container.indexOfChild(anchor);
                    if (idx < 0) idx = container.getChildCount() - 1;
                    container.addView(row, idx + 1, anchor.getLayoutParams());
                }
            }

            if (container.findViewWithTag(TAG_ROW_PROFILE) == null) {
                View anchorBackdrop = container.findViewWithTag(TAG_ROW_BACKDROP);
                View anchor = anchorBackdrop != null ? anchorBackdrop : reference;
                View row = buildRow(container, anchor, "Use as profile backdrop", TAG_ROW_PROFILE,
                        new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                useCurrentFilmBackdropAsProfile(fragment);
                            }
                        });
                if (row != null) {
                    int idx = container.indexOfChild(anchor);
                    if (idx < 0) idx = container.getChildCount() - 1;
                    container.addView(row, idx + 1, anchor.getLayoutParams());
                }
            }
        } catch (Throwable ignored) {}
    }

    private static View buildRow(ViewGroup parent, View styledLike, String label, String tag,
                                 View.OnClickListener click) {
        try {
            Context ctx = parent.getContext();
            if (ctx == null) return null;
            Button row = new Button(ctx);
            row.setTag(tag);
            row.setText(label);
            row.setAllCaps(false);
            row.setBackground(null);
            row.setGravity(Gravity.CENTER_VERTICAL);
            if (styledLike instanceof TextView) {
                TextView ref = (TextView) styledLike;
                row.setTextSize(TypedValue.COMPLEX_UNIT_PX, ref.getTextSize());
                row.setTextColor(ref.getCurrentTextColor());
                row.setTypeface(ref.getTypeface());
                row.setPadding(ref.getPaddingLeft(), ref.getPaddingTop(),
                        ref.getPaddingRight(), ref.getPaddingBottom());
                if (ref.getMinHeight() > 0) row.setMinHeight(ref.getMinHeight());
            }
            row.setOnClickListener(click);
            return row;
        } catch (Throwable t) { return null; }
    }

    // --- film backdrop override -----------------------------------------

    public static void maybeOverrideFilmBackdrop(Object binding, Object film) {
        try {
            if (binding == null || film == null) return;
            final String slug = extractFilmSlug(film);
            if (slug == null || slug.isEmpty()) return;
            final String customUrl = CustomPosterStore.getBackdropOverride(slug);
            if (customUrl == null || customUrl.isEmpty()) return;

            Object headerImageView = readField(binding, "headerImageView");
            if (!(headerImageView instanceof ImageView)) return;
            final ImageView iv = (ImageView) headerImageView;

            // Post — runs after the original Glide load has been queued.
            iv.post(new Runnable() {
                @Override public void run() {
                    try { CoilLoader.load(iv.getContext(), customUrl, iv); }
                    catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}
    }

    private static String extractFilmSlug(Object film) {
        try {
            Object summary = film.getClass().getMethod("getSummary").invoke(film);
            if (summary != null) {
                Object id = summary.getClass().getMethod("getId").invoke(summary);
                if (id != null) return id.toString();
            }
        } catch (Throwable ignored) {}
        try {
            Object id = film.getClass().getMethod("getId").invoke(film);
            if (id != null) return id.toString();
        } catch (Throwable ignored) {}
        return null;
    }

    // --- profile backdrop override --------------------------------------

    public static void maybeOverrideProfileBackdrop(Object fragment) {
        try {
            if (fragment == null) return;
            Object binding = readField(fragment, "binding");
            if (binding == null) return;
            final Object backdropObj = readField(binding, "userBackdrop");
            if (!(backdropObj instanceof ImageView)) return;
            final ImageView iv = (ImageView) backdropObj;

            final String url = Prefs.getString(Prefs.KEY_PROFILE_BACKDROP, "");
            if (url == null || url.isEmpty()) return;

            // Post — runs after the original Glide load has been queued.
            iv.post(new Runnable() {
                @Override public void run() {
                    try { CoilLoader.load(iv.getContext(), url, iv); }
                    catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}
    }

    // --- "Use as profile backdrop" row ----------------------------------

    private static void useCurrentFilmBackdropAsProfile(Object fragment) {
        try {
            Object filmSummary = fragment.getClass().getMethod("getFilmSummary").invoke(fragment);
            if (filmSummary == null) return;
            Context ctx = (Context) fragment.getClass().getMethod("requireContext").invoke(fragment);
            if (ctx == null) return;
            Prefs.load(ctx);

            Object id = filmSummary.getClass().getMethod("getId").invoke(filmSummary);
            if (id == null) return;
            String slug = id.toString();

            // Prefer the custom backdrop set by the user; fall back to the server's.
            String url = CustomPosterStore.getBackdropOverride(slug);
            if (url == null || url.isEmpty()) {
                try {
                    Object image = filmSummary.getClass().getMethod("getBackdrop").invoke(filmSummary);
                    if (image != null) {
                        Object u = image.getClass().getMethod("getUrl").invoke(image);
                        if (u != null) url = u.toString();
                    }
                } catch (Throwable ignored) {}
            }

            if (url == null || url.isEmpty()) {
                toast(ctx, "No backdrop available for this film");
                return;
            }

            Prefs.putString(Prefs.KEY_PROFILE_BACKDROP, url);
            toast(ctx, "Profile backdrop set");

            // If a profile screen is currently visible, refresh it now.
            Activity activity = findActivity(ctx);
            if (activity != null && activity.getWindow() != null) {
                refreshProfileBackdropInTree(activity.getWindow().getDecorView(), url);
            }
        } catch (Throwable ignored) {}
    }

    private static void refreshProfileBackdropInTree(View view, String url) {
        try {
            if (view == null || url == null) return;
            if (view instanceof ImageView) {
                int id = view.getId();
                if (id != 0) {
                    try {
                        String name = view.getResources().getResourceEntryName(id);
                        if ("userBackdrop".equals(name)) {
                            CoilLoader.load(view.getContext(), url, (ImageView) view);
                            return;
                        }
                    } catch (Throwable ignored) {}
                }
            }
            if (view instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) view;
                for (int i = 0; i < g.getChildCount(); i++) {
                    refreshProfileBackdropInTree(g.getChildAt(i), url);
                }
            }
        } catch (Throwable ignored) {}
    }

    // --- instant refresh (poster / backdrop) ----------------------------

    public static void refreshVisiblePoster(Context ctx, String filmSlug, String newUrl) {
        try {
            if (ctx == null || filmSlug == null) return;
            Activity activity = findActivity(ctx);
            if (activity == null || activity.getWindow() == null) return;
            refreshPosterInTree(activity.getWindow().getDecorView(), filmSlug, newUrl);
        } catch (Throwable ignored) {}
    }

    private static void refreshPosterInTree(View view, String filmSlug, String newUrl) {
        try {
            if (view == null) return;
            if (view.getClass().getName().equals("com.letterboxd.letterboxd.ui.views.PosterView")) {
                Object filmSummary = view.getClass().getMethod("getFilmSummary").invoke(view);
                if (filmSummary != null) {
                    Object id = filmSummary.getClass().getMethod("getId").invoke(filmSummary);
                    if (id != null && filmSlug.equals(id.toString())) {
                        Method setImageURL = view.getClass().getMethod("setImageURL", URL.class);
                        if (newUrl == null || newUrl.isEmpty()) {
                            setImageURL.invoke(view, new Object[]{null});
                        } else {
                            setImageURL.invoke(view, new URL(newUrl));
                        }
                    }
                }
            }
            if (view instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) view;
                for (int i = 0; i < g.getChildCount(); i++) {
                    refreshPosterInTree(g.getChildAt(i), filmSlug, newUrl);
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void refreshVisibleBackdrop(Context ctx, String filmSlug, String newUrl) {
        try {
            if (ctx == null) return;
            Activity activity = findActivity(ctx);
            if (activity == null || activity.getWindow() == null) return;
            refreshFilmBackdropInTree(activity.getWindow().getDecorView(), newUrl);
        } catch (Throwable ignored) {}
    }

    private static void refreshFilmBackdropInTree(View view, String newUrl) {
        try {
            if (view == null) return;
            if (view instanceof ImageView) {
                int id = view.getId();
                if (id != 0) {
                    try {
                        String name = view.getResources().getResourceEntryName(id);
                        if ("headerImageView".equals(name)) {
                            if (newUrl == null || newUrl.isEmpty()) {
                                ((ImageView) view).setImageDrawable(null);
                            } else {
                                CoilLoader.load(view.getContext(), newUrl, (ImageView) view);
                            }
                            return;
                        }
                    } catch (Throwable ignored) {}
                }
            }
            if (view instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) view;
                for (int i = 0; i < g.getChildCount(); i++) {
                    refreshFilmBackdropInTree(g.getChildAt(i), newUrl);
                }
            }
        } catch (Throwable ignored) {}
    }

    // --- helpers --------------------------------------------------------

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
        } catch (Throwable ignored) {}
        return null;
    }

    private static void openPicker(Object fragment, String mode) {
        try {
            Object filmSummary = fragment.getClass().getMethod("getFilmSummary").invoke(fragment);
            if (filmSummary == null) return;
            Context ctx = (Context) fragment.getClass().getMethod("requireContext").invoke(fragment);
            if (ctx == null) return;
            Prefs.load(ctx);
            String slug = reflectString(filmSummary, "getId");
            if (slug == null || slug.isEmpty()) return;
            String imdbId = reflectImdbId(filmSummary);
            new CustomPosterDialog(ctx, slug, imdbId, mode,
                    new CustomPosterDialog.OnChange() {
                        @Override public void onChange(String newUrl) { }
                    }).show();
        } catch (Throwable ignored) {}
    }

    private static Object readField(Object target, String name) {
        try { return target.getClass().getField(name).get(target); }
        catch (Throwable t) {
            try {
                Field f = target.getClass().getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (Throwable t2) { return null; }
        }
    }

    private static String reflectString(Object target, String method) {
        try {
            Object r = target.getClass().getMethod(method).invoke(target);
            return r == null ? null : r.toString();
        } catch (Throwable t) { return null; }
    }

    private static String reflectImdbId(Object filmSummary) {
        try {
            Object linksObj = filmSummary.getClass().getMethod("getLinks").invoke(filmSummary);
            if (!(linksObj instanceof List)) return null;
            for (Object link : (List<?>) linksObj) {
                Object type = link.getClass().getMethod("getType").invoke(link);
                if (type == null || !"Imdb".equals(type.getClass().getSimpleName())) continue;
                Object url = link.getClass().getMethod("getUrl").invoke(link);
                if (url == null) continue;
                Matcher m = IMDB.matcher(url.toString());
                if (m.find()) return m.group(1);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void toast(Context ctx, String msg) {
        try { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show(); }
        catch (Throwable ignored) {}
    }

    public static void __cacheBustV8() {}
}
