package app.template.extension.settings;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "Custom poster" picker — shown from the film action sheet.
 *
 * <p>Layout: header with title + reset button; a grid of poster thumbnails fetched from TMDB
 * (by the film's IMDb id); a paste-URL input at the bottom for anything TMDB doesn't have; a
 * "Use this URL" button next to it.
 *
 * <p>Requires the film's slug (used as the storage key) and its IMDb id (used for the TMDB
 * lookup). If TMDB isn't configured, or the fetch returns nothing, the grid area shows a hint
 * and the paste-URL input becomes the primary path — still functional.
 */
final class CustomPosterDialog extends Dialog {

    interface OnChange { void onChange(String newUrl); }

    private final Context ctx;
    private final String filmSlug;
    private final String imdbId;
    private final OnChange onChange;
    private final float density;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private LinearLayout gridContainer;
    private ProgressBar spinner;
    private TextView gridHint;
    private EditText urlInput;

    CustomPosterDialog(Context context, String filmSlug, String imdbId, OnChange onChange) {
        super(context);
        this.ctx = context;
        this.filmSlug = filmSlug;
        this.imdbId = imdbId;
        this.onChange = onChange;
        this.density = context.getResources().getDisplayMetrics().density;
        Prefs.load(context);
        build();
        loadPosters();
    }

    private void build() {
        Window window = getWindow();
        if (window != null) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(SurfaceColors.elevated(ctx));
            bg.setCornerRadius(dp(20));
            window.setBackgroundDrawable(bg);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(window.getAttributes());
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(lp);
        }

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(12));

        // --- header ---
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(ctx);
        title.setText("Custom poster");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView reset = new TextView(ctx);
        reset.setText("Reset");
        reset.setTextColor(0xFFB0B0B0);
        reset.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        reset.setPadding(dp(12), dp(6), dp(12), dp(6));
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                CustomPosterStore.clearOverride(filmSlug);
                if (onChange != null) onChange.onChange(null);
                toast("Poster reset");
                dismiss();
            }
        });
        header.addView(reset);

        root.addView(header);

        // --- grid area ---
        LinearLayout gridWrap = new LinearLayout(ctx);
        gridWrap.setOrientation(LinearLayout.VERTICAL);
        gridWrap.setPadding(0, dp(12), 0, 0);

        spinner = new ProgressBar(ctx);
        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        spinnerLp.gravity = Gravity.CENTER_HORIZONTAL;
        spinnerLp.topMargin = dp(20);
        spinnerLp.bottomMargin = dp(20);
        gridWrap.addView(spinner, spinnerLp);

        gridHint = new TextView(ctx);
        gridHint.setText("Loading posters…");
        gridHint.setTextColor(0xFF9AA0A6);
        gridHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        gridHint.setGravity(Gravity.CENTER);
        gridHint.setPadding(0, dp(8), 0, dp(8));
        gridWrap.addView(gridHint);

        ScrollView scroll = new ScrollView(ctx);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(320)));

        gridContainer = new LinearLayout(ctx);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(gridContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        gridWrap.addView(scroll);
        root.addView(gridWrap);

        // --- paste URL ---
        TextView pasteLabel = new TextView(ctx);
        pasteLabel.setText("Paste image URL");
        pasteLabel.setTextColor(0xFF9AA0A6);
        pasteLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        pasteLabel.setPadding(0, dp(12), 0, dp(4));
        root.addView(pasteLabel);

        LinearLayout urlRow = new LinearLayout(ctx);
        urlRow.setOrientation(LinearLayout.HORIZONTAL);
        urlRow.setGravity(Gravity.CENTER_VERTICAL);

        urlInput = new EditText(ctx);
        urlInput.setHint("https://…");
        urlInput.setHintTextColor(0xFF6B6B6B);
        urlInput.setTextColor(0xFFEDEDED);
        urlInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        urlInput.setSingleLine(true);
        urlInput.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(SurfaceColors.elevated(ctx));
        inputBg.setCornerRadius(dp(8));
        inputBg.setStroke(dp(1), 0x33FFFFFF);
        urlInput.setBackground(inputBg);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        inputLp.rightMargin = dp(8);
        urlRow.addView(urlInput, inputLp);

        TextView useUrl = new TextView(ctx);
        useUrl.setText("Use");
        useUrl.setTextColor(0xFFFFFFFF);
        useUrl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        useUrl.setTypeface(useUrl.getTypeface(), Typeface.BOLD);
        useUrl.setPadding(dp(16), dp(10), dp(16), dp(10));
        GradientDrawable useBg = new GradientDrawable();
        useBg.setColor(0xFF2A6FDB);
        useBg.setCornerRadius(dp(8));
        useUrl.setBackground(useBg);
        useUrl.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String url = urlInput.getText() != null ? urlInput.getText().toString().trim() : "";
                if (TextUtils.isEmpty(url) || !isProbablyUrl(url)) {
                    toast("Enter a full image URL");
                    return;
                }
                applyUrl(url);
            }
        });
        urlRow.addView(useUrl);
        root.addView(urlRow);

        setContentView(root);
    }

    // --- TMDB fetch ------------------------------------------------------

    private void loadPosters() {
        if (!TmdbClient.isConfigured()) {
            spinner.setVisibility(View.GONE);
            gridHint.setText("Add a TMDB API key in Mod settings to see poster options.\n" +
                    "You can still paste a URL below.");
            return;
        }
        if (imdbId == null || imdbId.isEmpty()) {
            spinner.setVisibility(View.GONE);
            gridHint.setText("No IMDb id for this film — paste a URL below.");
            return;
        }

        exec.execute(new Runnable() {
            @Override public void run() {
                final List<String> posters = TmdbClient.fetchPosters(imdbId);
                main.post(new Runnable() {
                    @Override public void run() {
                        spinner.setVisibility(View.GONE);
                        if (posters == null || posters.isEmpty()) {
                            gridHint.setText("No posters found on TMDB for this film.\n" +
                                    "Paste a URL below.");
                        } else {
                            gridHint.setVisibility(View.GONE);
                            populateGrid(posters);
                        }
                    }
                });
            }
        });
    }

    private void populateGrid(List<String> urls) {
        gridContainer.removeAllViews();
        // Three per row. Manual rows because GridLayout is fussy on older Android.
        int perRow = 3;
        int gap = dp(8);
        int available = ctx.getResources().getDisplayMetrics().widthPixels - dp(64);
        int cellSize = (available - gap * (perRow - 1)) / perRow;

        LinearLayout row = null;
        for (int i = 0; i < urls.size(); i++) {
            if (i % perRow == 0) {
                row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowLp.bottomMargin = gap;
                gridContainer.addView(row, rowLp);
            }

            final String url = urls.get(i);
            ImageView iv = new ImageView(ctx);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cellSize, (int)(cellSize * 1.5));
            lp.rightMargin = (i % perRow == perRow - 1) ? 0 : gap;
            iv.setLayoutParams(lp);
            iv.setBackgroundColor(0xFF1C1C1C);

            // Load thumbnail with Coil if available (Letterboxd uses Coil). Fall back to
            // Glide, then to a plain background if neither is reachable at runtime.
            CoilLoader.load(ctx, url, iv);

            iv.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    applyUrl(url);
                }
            });

            row.addView(iv);
        }
    }

    // --- apply -----------------------------------------------------------

    private void applyUrl(String url) {
        CustomPosterStore.setOverride(filmSlug, url);
        if (onChange != null) onChange.onChange(url);
        toast("Poster saved");
        dismiss();
    }

    private static boolean isProbablyUrl(String s) {
        String lower = s.toLowerCase();
        return (lower.startsWith("http://") || lower.startsWith("https://")
                || lower.startsWith("file://") || lower.startsWith("content://"));
    }

    private void toast(String message) {
        try {
            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
