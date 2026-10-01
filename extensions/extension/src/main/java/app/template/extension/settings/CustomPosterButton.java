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

/**
 * Runtime for the "Custom poster" feature. Every override uses the same reliable pattern:
 *
 *   1. Read the stored URL for the film / profile
 *   2. Cancel any in-flight Glide request bound to the target ImageView
 *   3. Load our URL via Coil
 *
 * The Glide cancel is what stops the original server URL from overwriting our image a moment
 * later. Because we run this on every render via the injected hooks, overrides persist across
 * navigation instead of only appearing on the same screen where the dialog was opened.
 */
public final class CustomPosterButton {

    private static final String TAG_ROW_POSTER = "morphe_custom_poster_row";
    private static final String TAG_ROW_BACKDROP = "morphe_custom_backdrop_row";
    private static final String TAG_ROW_PROFILE = "morphe_profile_backdrop_row";
    private static final Pattern IMDB = Pattern.compile("(tt\\d+)");
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private CustomPosterButton() {}

    // --- 1: poster override (runs on every PosterView render) -----------

    public static void maybeOverridePoster(Object posterView) {
        try {
            if (posterView == null) return;

            Object filmSummary = posterView.getClass().getMethod("getFilmSummary").invoke(posterView);
            if (filmSummary == null) return;

            Object id = filmSummary.getClass().getMethod("getId").invoke(filmSummary);
            if (id == null) return;
            String slug = id.toString();
            if (slug.isEmpty()) return;

            String customUrl = CustomPosterStore.getOverride(slug);
            if (customUrl == null || customUrl.isEmpty()) return;

            // Reach the underlying ImageView of the PosterView and load directly.
            // The PosterView has a public setImageURL(URL) that manages its internal ImageView,
            // but that path uses Coil internally and might race with the app's Glide call.
            // We call it on the main thread after the host method returns.
            MAIN.post(new Runnable() {
                @Override public void run() {
                    try {
                        URL url = new URL(customUrl);
                        posterView.getClass().getMethod("setImageURL", URL.class)
                                .invoke(posterView, url);
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}
    }

    // --- 2: film action sheet: three rows -------------------------------

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

            // Always start clean.
            removeByTag(container, TAG_ROW_POSTER);
            removeByTag(container, TAG_ROW_BACKDROP);
            removeByTag(container, TAG_ROW_PROFILE);

            int insertAt = container.indexOfChild(reference);
            if (insertAt < 0) insertAt = container.getChildCount() - 1;
            insertAt += 1;

            View posterRow = buildRow(container, reference, "Custom poster",
                    new View.OnClickListener() {
                        @Override public void onClick(View v) { openPicker(fragment, "poster"); }
                    });
            if (posterRow != null) {
                posterRow.setTag(TAG_ROW_POSTER);
                container.addView(posterRow, insertAt++, reference.getLayoutParams());
            }

            View backdropRow = buildRow(container, reference, "Custom backdrop",
                    new View.OnClickListener() {
                        @Override public void onClick(View v) { openPicker(fragment, "backdrop"); }
                    });
            if (backdropRow != null) {
                backdropRow.setTag(TAG_ROW_BACKDROP);
                container.addView(backdropRow, insertAt++, reference.getLayoutParams());
            }

            View profileRow = buildRow(container, reference, "Use as profile backdrop",
                    new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            useCurrentFilmBackdropAsProfile(fragment);
                        }
                    });
            if (profileRow != null) {
                profileRow.setTag(TAG_ROW_PROFILE);
                container.addView(profileRow, insertAt, reference.getLayoutParams());
            }
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

    // --- 3: film backdrop override (runs on every FilmHeader render) ----

    public static void maybeOverrideFilmBackdrop(Object binding, Object film) {
        try {
            if (binding == null || film == null) return;
            String slug = extractFilmSlug(film);
            if (slug == null || slug.isEmpty()) return;
            String customUrl = CustomPosterStore.getBackdropOverride(slug);
            if (customUrl == null || customUrl.isEmpty()) return;

            Object headerImageView = readField(binding, "headerImageView");
            if (!(headerImageView instanceof ImageView)) return;
            final ImageView iv = (ImageView) headerImageView;

            applyImageOverride(iv, customUrl);
        } catch (Throwable ignored) {}
    }

    // --- 4: profile backdrop override -----------------------------------

    public static void maybeOverrideProfileBackdrop(Object fragment) {
        try {
            if (fragment == null) return;
            Object binding = readField(fragment, "binding");
            if (binding == null) return;
            Object backdropObj = readField(binding, "userBackdrop");
            if (!(backdropObj instanceof ImageView)) return;
            final ImageView iv = (ImageView) backdropObj;

            String url = Prefs.getString(Prefs.KEY_PROFILE_BACKDROP, "");
            if (url == null || url.isEmpty()) return;

            applyImageOverride(iv, url);
        } catch (Throwable ignored) {}
    }

    /**
     * The one-shot replacement mechanism. Cancels any pending Glide request on the view,
     * then Coil-loads the override URL.
     */
    private static void applyImageOverride(final ImageView iv, final String url) {
        if (iv == null || url == null || url.isEmpty()) return;
        MAIN.post(new Runnable() {
            @Override public void run() {
                try {
                    cancelGlide(iv);
                    CoilLoader.load(iv.getContext(), url, iv);
                } catch (Throwable ignored) {}
            }
        });
    }

    private static void cancelGlide(View view) {
        try {
            Class<?> glide = Class.forName("com.bumptech.glide.Glide");
            Object requestManager = glide.getMethod("with", View.class).invoke(null, view);
            requestManager.getClass().getMethod("clear", View.class)
                    .invoke(requestManager, view);
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

    // --- instant refresh from the picker dialog -------------------------

    /**
     * Called by CustomPosterDialog after a poster is picked. Uses FragmentManager to find
     * the currently-visible FilmHeaderFragment and reloads the poster immediately.
     */
    public static void refreshVisiblePoster(Context ctx, String filmSlug, String newUrl) {
        try {
            if (ctx == null || filmSlug == null) return;
            Activity activity = findActivity(ctx);
            if (activity == null) return;

            // Update any visible PosterView directly.
            View root = activity.getWindow().getDecorView();
            refreshPosterInTree(root, filmSlug, newUrl);

            // Also reach through FragmentManager in case the tree walk missed one.
            if (activity instanceof FragmentActivity) {
                FragmentManager fm = ((FragmentActivity) activity).getSupportFragmentManager();
                applyToFilmHeaderFragments(fm, newUrl);
            }
        } catch (Throwable ignored) {}
    }

    private static void applyToFilmHeaderFragments(FragmentManager fm, String newUrl) {
        try {
            List<Fragment> frags = fm.getFragments();
            for (Fragment f : frags) {
                try {
                    Object binding = readField(f, "binding");
                    if (binding == null) continue;
                    Object iv = readField(binding, "headerImageView");
                    if (iv instanceof ImageView) {
                        applyImageOverride((ImageView) iv, newUrl);
                    }
                } catch (Throwable ignored) {}
            }
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
            if (activity == null) return;

            View root = activity.getWindow().getDecorView();
            refreshFilmBackdropInTree(root, newUrl);

            if (activity instanceof FragmentActivity) {
                FragmentManager fm = ((FragmentActivity) activity).getSupportFragmentManager();
                applyToFilmHeaderFragments(fm, newUrl);
            }
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

    public static void __cacheBustV10() {}
}
