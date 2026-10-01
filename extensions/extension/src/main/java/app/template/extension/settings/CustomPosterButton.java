package app.template.extension.settings;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CustomPosterButton {

    private static final String TAG_ROW_POSTER = "morphe_custom_poster_row";
    private static final String TAG_ROW_BACKDROP = "morphe_custom_backdrop_row";
    private static final String TAG_ROW_PROFILE = "morphe_profile_backdrop_row";
    private static final Pattern IMDB = Pattern.compile("(tt\\d+)");
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private CustomPosterButton() {}

    // --- film action sheet: three rows ----------------------------------

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

            // Remove any stale copies from a previous injection pass, so we always start clean.
            removeByTag(container, TAG_ROW_POSTER);
            removeByTag(container, TAG_ROW_BACKDROP);
            removeByTag(container, TAG_ROW_PROFILE);

            // Insert all three under "Change poster / backdrop" in order.
            int insertAt = container.indexOfChild(reference);
            if (insertAt < 0) insertAt = container.getChildCount() - 1;
            insertAt += 1;

            View posterRow = buildRow(container, reference, "Custom poster",
                    new View.OnClickListener() {
                        @Override public void onClick(View v) { openPicker(fragment, "poster"); }
                    });
            if (posterRow != null) {
                container.addView(posterRow, insertAt++, reference.getLayoutParams());
            }

            View backdropRow = buildRow(container, reference, "Custom backdrop",
                    new View.OnClickListener() {
                        @Override public void onClick(View v) { openPicker(fragment, "backdrop"); }
                    });
            if (backdropRow != null) {
                container.addView(backdropRow, insertAt++, reference.getLayoutParams());
            }

            View profileRow = buildRow(container, reference, "Use as profile backdrop",
                    new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            useCurrentFilmBackdropAsProfile(fragment);
                        }
                    });
            if (profileRow != null) {
                container.addView(profileRow, insertAt, reference.getLayoutParams());
            }

            // Tag the rows so a re-run doesn't duplicate them.
            if (posterRow != null) posterRow.setTag(TAG_ROW_POSTER);
            if (backdropRow != null) backdropRow.setTag(TAG_ROW_BACKDROP);
            if (profileRow != null) profileRow.setTag(TAG_ROW_PROFILE);
        } catch (Throwable ignored) {}
    }

    private static void removeByTag(ViewGroup container, String tag) {
        try {
            View existing = container.findViewWithTag(tag);
            while (existing != null) {
                container.removeView(existing);
                existing = container.findViewWithTag(tag);
            }
        } catch (Throwable ignored) {}
    }

    private static View buildRow(ViewGroup parent, View styledLike, String label,
                                 View.OnClickListener click) {
        try {
            Context ctx = parent.getContext();
            if (ctx == null) return null;
            Button row = new Button(ctx);
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

            applyImageOverride(iv, customUrl);
        } catch (Throwable ignored) {}
    }

    // --- profile backdrop override --------------------------------------

    public static void maybeOverrideProfileBackdrop(Object fragment) {
        try {
            if (fragment == null) return;
            Object binding = readField(fragment, "binding");
            if (binding == null) return;
            Object backdropObj = readField(binding, "userBackdrop");
            if (!(backdropObj instanceof ImageView)) return;
            final ImageView iv = (ImageView) backdropObj;

            final String url = Prefs.getString(Prefs.KEY_PROFILE_BACKDROP, "");
            if (url == null || url.isEmpty()) return;

            applyImageOverride(iv, url);
        } catch (Throwable ignored) {}
    }

    /**
     * Cancels any in-flight Glide request bound to [iv], then loads our custom URL via Coil.
     * The Glide cancel is what stops the original server URL from overwriting ours a moment
     * later. Runs on the UI thread via post() so we're definitively after the host method's
     * synchronous portion.
     */
    private static void applyImageOverride(final ImageView iv, final String url) {
        if (iv == null || url == null || url.isEmpty()) return;
        MAIN.post(new Runnable() {
            @Override public void run() {
                try {
                    // Cancel Glide's pending request on this view, if any.
                    try {
                        Class<?> glide = Class.forName("com.bumptech.glide.Glide");
                        Object requestManager = glide.getMethod("with", View.class).invoke(null, iv);
                        requestManager.getClass().getMethod("clear", View.class)
                                .invoke(requestManager, iv);
                    } catch (Throwable ignored) {}
                    CoilLoader.load(iv.getContext(), url, iv);
                } catch (Throwable ignored) {}
            }
        });
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

    // --- "Use as profile backdrop" --------------------------------------

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

            // Refresh any visible profile screen now.
            Activity activity = findActivity(ctx);
            if (activity != null) {
                View root = activity.getWindow().getDecorView();
                refreshProfileBackdropInTree(root, url);
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
                            applyImageOverride((ImageView) view, url);
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

    // --- instant refresh from the dialog --------------------------------

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
            if (ctx == null || newUrl == null) return;
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
                            applyImageOverride((ImageView) view, newUrl);
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

    // --- picker entry ---------------------------------------------------

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

    public static void __cacheBustV9() {}
}
