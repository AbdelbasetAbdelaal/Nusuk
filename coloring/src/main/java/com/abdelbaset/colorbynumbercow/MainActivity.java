package com.abdelbaset.colorbynumbercow;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REGION_COUNT = 81;
    private static final int[] PALETTE = {
        0xFF35B5D2, 0xFF15945C, 0xFF56BD3B, 0xFF76513D,
        0xFFD89B79, 0xFFE33F86, 0xFFF0E9D4, 0xFF383840
    };
    private static final String[] COLOR_NAMES = {
        "أزرق سماوي", "أخضر", "أخضر فاتح", "بني",
        "خوخي", "وردي", "كريمي", "رمادي داكن"
    };

    private final int[] assigned = new int[REGION_COUNT + 1];
    private final boolean[] filled = new boolean[REGION_COUNT + 1];
    private int[] regionIds;
    private int[] basePixels;
    private int mapWidth;
    private int mapHeight;
    private Bitmap renderedBitmap;
    private BoardView boardView;
    private TextView statusText;
    private TextView progressText;
    private Button modeButton;
    private final Button[] colorButtons = new Button[8];
    private int selectedRegion;
    private int activeColor;
    private boolean playMode;
    private SharedPreferences preferences;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF176B58);
        getWindow().setNavigationBarColor(0xFF176B58);
        preferences = getSharedPreferences("cow_coloring", MODE_PRIVATE);
        loadProgress();
        if (!loadArtwork()) {
            new AlertDialog.Builder(this).setMessage("تعذر تحميل صورة التلوين. أعد تثبيت التطبيق.")
                .setPositiveButton("حسنًا", null).show();
            return;
        }
        buildScreen();
        renderBoard();
        refreshUi();
    }

    private boolean loadArtwork() {
        try (InputStream boardStream = getAssets().open("assets/cow-board.webp");
             InputStream mapStream = getAssets().open("assets/region-map.webp")) {
            Bitmap board = BitmapFactory.decodeStream(boardStream);
            Bitmap map = BitmapFactory.decodeStream(mapStream);
            if (board == null || map == null) return false;
            mapWidth = map.getWidth();
            mapHeight = map.getHeight();
            Bitmap scaledBoard = Bitmap.createScaledBitmap(board, mapWidth, mapHeight, true);
            basePixels = new int[mapWidth * mapHeight];
            int[] mapPixels = new int[mapWidth * mapHeight];
            scaledBoard.getPixels(basePixels, 0, mapWidth, 0, 0, mapWidth, mapHeight);
            map.getPixels(mapPixels, 0, mapWidth, 0, 0, mapWidth, mapHeight);
            regionIds = new int[mapPixels.length];
            for (int i = 0; i < mapPixels.length; i++) {
                regionIds[i] = Color.red(mapPixels[i]);
                int px = basePixels[i];
                int luminance = (Color.red(px) * 299 + Color.green(px) * 587 + Color.blue(px) * 114) / 1000;
                // Remove the grey paper cast and strengthen the printed digits and outlines.
                basePixels[i] = luminance >= 176 ? Color.WHITE : Color.BLACK;
            }
            if (board != scaledBoard) board.recycle();
            map.recycle();
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    private void loadProgress() {
        String a = preferences.getString("assignments", "");
        String f = preferences.getString("filled", "");
        for (int i = 1; i <= REGION_COUNT; i++) {
            if (a.length() >= i && a.charAt(i - 1) >= '1' && a.charAt(i - 1) <= '8')
                assigned[i] = a.charAt(i - 1) - '0';
            if (f.length() >= i) filled[i] = f.charAt(i - 1) == '1';
        }
        playMode = countAssigned() == REGION_COUNT;
        activeColor = preferences.getInt("activeColor", 0);
        if (activeColor < 0 || activeColor > 8) activeColor = 0;
    }

    private void saveProgress() {
        StringBuilder a = new StringBuilder(REGION_COUNT);
        StringBuilder f = new StringBuilder(REGION_COUNT);
        for (int i = 1; i <= REGION_COUNT; i++) {
            a.append(assigned[i] == 0 ? '0' : (char) ('0' + assigned[i]));
            f.append(filled[i] ? '1' : '0');
        }
        preferences.edit().putString("assignments", a.toString())
            .putString("filled", f.toString())
            .putInt("activeColor", activeColor).commit();
    }

    private int countAssigned() {
        int n = 0;
        for (int i = 1; i <= REGION_COUNT; i++) if (assigned[i] != 0) n++;
        return n;
    }

    private int countFilled() {
        int n = 0;
        for (int i = 1; i <= REGION_COUNT; i++) if (filled[i]) n++;
        return n;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView makeText(String text, int size, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(0xFF23332E);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private GradientDrawable rounded(int color, int strokeColor, int strokeWidth) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(14));
        if (strokeWidth > 0) bg.setStroke(dp(strokeWidth), strokeColor);
        return bg;
    }

    private Button makeButton(String label, int background, int foreground) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setTextColor(foreground);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(background, 0x00000000, 0));
        b.setPadding(dp(8), dp(5), dp(8), dp(5));
        return b;
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(14));
        root.setBackgroundColor(0xFFF4F1E9);
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = makeText("🐮 البقرة الملوّنة بالأرقام", 21, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        modeButton = makeButton(playMode ? "إعداد الورقة" : "ابدأ اللعب", 0xFF176B58, Color.WHITE);
        header.addView(modeButton, new LinearLayout.LayoutParams(dp(115), dp(44)));
        root.addView(header);
        modeButton.setOnClickListener(v -> toggleMode());

        statusText = makeText("", 14, true);
        statusText.setPadding(dp(8), dp(5), dp(8), dp(5));
        statusText.setBackground(rounded(Color.WHITE, 0xFFE3DED4, 1));
        root.addView(statusText, new LinearLayout.LayoutParams(-1, dp(52)));

        progressText = makeText("", 12, true);
        progressText.setGravity(Gravity.CENTER);
        progressText.setPadding(0, dp(5), 0, dp(5));
        root.addView(progressText);

        boardView = new BoardView();
        float density = getResources().getDisplayMetrics().density;
        int screenWidthDp = (int) (getResources().getDisplayMetrics().widthPixels / density);
        int boardHeightDp = Math.round((screenWidthDp - 24) * (float) mapHeight / mapWidth);
        LinearLayout.LayoutParams boardParams = new LinearLayout.LayoutParams(-1, dp(boardHeightDp));
        boardParams.topMargin = dp(4);
        boardParams.bottomMargin = dp(7);
        boardView.setBackground(rounded(Color.WHITE, 0xFFE4DED2, 1));
        root.addView(boardView, boardParams);

        LinearLayout zoomRow = new LinearLayout(this);
        zoomRow.setGravity(Gravity.CENTER);
        Button zoomOut = makeButton("−", 0xFFE5ECE8, 0xFF244C40);
        Button zoomReset = makeButton("ملاءمة الصورة", 0xFFE5ECE8, 0xFF244C40);
        Button zoomIn = makeButton("+", 0xFFE5ECE8, 0xFF244C40);
        LinearLayout.LayoutParams zoomSmall = new LinearLayout.LayoutParams(dp(58), dp(40));
        LinearLayout.LayoutParams zoomWide = new LinearLayout.LayoutParams(0, dp(40), 1);
        zoomSmall.setMargins(dp(4), 0, dp(4), dp(4));
        zoomWide.setMargins(dp(4), 0, dp(4), dp(4));
        zoomRow.addView(zoomOut, zoomSmall);
        zoomRow.addView(zoomReset, zoomWide);
        zoomRow.addView(zoomIn, zoomSmall);
        root.addView(zoomRow);
        zoomOut.setOnClickListener(v -> boardView.zoomBy(0.8f));
        zoomIn.setOnClickListener(v -> boardView.zoomBy(1.25f));
        zoomReset.setOnClickListener(v -> boardView.resetZoom());

        root.addView(makeText("اختر رقم اللون", 14, true));
        for (int row = 0; row < 2; row++) {
            LinearLayout paletteRow = new LinearLayout(this);
            paletteRow.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 4; col++) {
                final int number = row * 4 + col + 1;
                Button b = makeButton(String.valueOf(number), PALETTE[number - 1],
                    (number == 4 || number == 6 || number == 8) ? Color.WHITE : 0xFF20252A);
                colorButtons[number - 1] = b;
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1);
                p.setMargins(dp(3), dp(3), dp(3), dp(3));
                paletteRow.addView(b, p);
                b.setOnClickListener(v -> chooseColor(number));
            }
            root.addView(paletteRow);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button reset = makeButton("إعادة التلوين", Color.WHITE, 0xFF46515B);
        Button help = makeButton("طريقة اللعب", 0xFFE8EEE9, 0xFF244C40);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(46), 1);
        half.setMargins(dp(3), dp(5), dp(3), 0);
        actions.addView(reset, half);
        actions.addView(help, half);
        root.addView(actions);
        reset.setOnClickListener(v -> resetCurrentMode());
        help.setOnClickListener(v -> showHelp());
        setContentView(scroll);
    }

    private void chooseColor(int number) {
        activeColor = number;
        if (!playMode && selectedRegion > 0) {
            assigned[selectedRegion] = number;
            filled[selectedRegion] = false;
            int chosen = selectedRegion;
            selectedRegion = 0;
            saveProgress();
            renderBoard();
            refreshUi();
            toast("تم تسجيل المساحة " + chosen + " بالرقم " + number + ". اختر الرقم نفسه لمساحات أخرى.");
        } else {
            saveProgress();
            refreshUi();
            if (playMode) {
                toast("اللون المختار: " + number + " — " + COLOR_NAMES[number - 1] + ". المس المساحات المطابقة.");
            } else {
                toast("تم اختيار الرقم " + number + ". المس الآن المساحات التي تحمل هذا الرقم.");
            }
        }
    }

    private void toggleMode() {
        if (!playMode) {
            if (countAssigned() < REGION_COUNT) {
                toast("تبقّى إعداد " + (REGION_COUNT - countAssigned()) + " مساحة.");
                return;
            }
            playMode = true;
            activeColor = 0;
            selectedRegion = 0;
            toast("بدأ اللعب! اختر اللون ثم المس المساحات المطابقة.");
        } else {
            playMode = false;
            activeColor = 0;
            selectedRegion = 0;
            toast("وضع إعداد الورقة: المساحة ثم رقمها المطبوع.");
        }
        saveProgress();
        refreshUi();
        renderBoard();
    }

    private void onBoardTap(float x, float y) {
        int id = findRegion(x, y);
        if (id == 0) {
            toast("المس داخل مساحة بيضاء بعيدًا عن الخط الأسود.");
            return;
        }
        if (!playMode) {
            if (activeColor > 0) {
                assigned[id] = activeColor;
                filled[id] = false;
                selectedRegion = 0;
                saveProgress();
                renderBoard();
                refreshUi();
                toast("تم تسجيل المساحة بالرقم " + activeColor + ". يمكنك متابعة المساحات التي تحمل الرقم نفسه.");
            } else {
                selectedRegion = id;
                refreshUi();
                renderBoard();
                toast("المساحة " + id + " محددة. اختر رقمها، أو اختر الرقم أولًا ثم المس المساحة.");
            }
            return;
        }
        if (assigned[id] == 0) {
            toast("هذه المساحة لم يتم إعداد رقمها بعد.");
        } else if (activeColor == 0) {
            toast("اختر رقم اللون أولًا.");
        } else if (filled[id]) {
            toast("هذه المساحة ملوّنة بالفعل ✓");
        } else if (activeColor != assigned[id]) {
            toast("لون غير صحيح! هذه المساحة رقم " + assigned[id] + ".");
        } else {
            filled[id] = true;
            saveProgress();
            renderBoard();
            refreshUi();
            toast(countFilled() == REGION_COUNT ? "رائع! أكملت الصورة كلها! 🏆" : "ممتاز! هذا هو اللون الصحيح 👏");
        }
    }

    private int findRegion(float viewX, float viewY) {
        if (boardView == null || regionIds == null) return 0;
        RectF rect = boardView.getImageRect();
        if (!rect.contains(viewX, viewY)) return 0;
        int x = Math.max(0, Math.min(mapWidth - 1, (int) ((viewX - rect.left) * mapWidth / rect.width())));
        int y = Math.max(0, Math.min(mapHeight - 1, (int) ((viewY - rect.top) * mapHeight / rect.height())));
        for (int radius = 0; radius <= 6; radius++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
                    int xx = x + dx, yy = y + dy;
                    if (xx >= 0 && yy >= 0 && xx < mapWidth && yy < mapHeight) {
                        int id = regionIds[yy * mapWidth + xx];
                        if (id > 0 && id <= REGION_COUNT) return id;
                    }
                }
            }
        }
        return 0;
    }

    private void renderBoard() {
        if (basePixels == null) return;
        int[] out = new int[basePixels.length];
        for (int i = 0; i < out.length; i++) {
            int base = basePixels[i];
            int id = regionIds[i];
            int color = base;
            if (id > 0 && id <= REGION_COUNT && filled[id] && assigned[id] > 0) {
                // Full-strength colour on white paper; preserve all black digits and outlines.
                if (Color.red(base) > 160) color = PALETTE[assigned[id] - 1];
            }
            // The selection is shown in the palette, not as a yellow tint on the drawing.
            out[i] = color;
        }
        if (renderedBitmap != null && !renderedBitmap.isRecycled()) renderedBitmap.recycle();
        renderedBitmap = Bitmap.createBitmap(out, mapWidth, mapHeight, Bitmap.Config.ARGB_8888);
        if (boardView != null) boardView.invalidate();
    }

    private int blend(int a, int b, float amount) {
        int r = Math.round(Color.red(a) * (1f - amount) + Color.red(b) * amount);
        int g = Math.round(Color.green(a) * (1f - amount) + Color.green(b) * amount);
        int bl = Math.round(Color.blue(a) * (1f - amount) + Color.blue(b) * amount);
        return Color.rgb(r, g, bl);
    }

    private void refreshUi() {
        if (statusText == null) return;
        if (playMode) {
            statusText.setText("وضع اللعب: اختر رقم اللون، ثم المس الأجزاء التي تحمل الرقم نفسه.");
            modeButton.setText("إعداد الورقة");
            progressText.setText(String.format(Locale.getDefault(), "التلوين: %d من %d", countFilled(), REGION_COUNT));
        } else {
            if (selectedRegion > 0) {
                statusText.setText("المساحة " + selectedRegion + " محددة — اختر رقمها، أو اختر الرقم أولًا ثم المس المساحة.");
            } else if (activeColor > 0) {
                statusText.setText("الرقم المختار: " + activeColor + " — المس كل المساحات التي تحمل هذا الرقم لتسجيلها.");
            } else {
                statusText.setText("إعداد أول مرة: اختر رقمًا ثم المس المساحات المطابقة، أو المس المساحة أولًا.");
            }
            modeButton.setText("ابدأ اللعب");
            progressText.setText(String.format(Locale.getDefault(), "تم إعداد %d من %d مساحة", countAssigned(), REGION_COUNT));
        }
        for (int i = 0; i < 8; i++) {
            Button b = colorButtons[i];
            if (b != null) {
                b.setText(activeColor == i + 1 ? "✓ " + (i + 1) : String.valueOf(i + 1));
                b.setBackground(rounded(PALETTE[i],
                    activeColor == i + 1 ? 0xFF101820 : 0xFFB4B8B3,
                    activeColor == i + 1 ? 5 : 1));
                b.setElevation(activeColor == i + 1 ? dp(5) : dp(1));
            }
        }
    }

    private void resetCurrentMode() {
        if (playMode) {
            new AlertDialog.Builder(this).setMessage("هل تريد إزالة جميع الألوان والبدء من جديد؟")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("إعادة", (d, w) -> {
                    for (int i = 1; i <= REGION_COUNT; i++) filled[i] = false;
                    saveProgress(); renderBoard(); refreshUi(); toast("تمت إعادة التلوين.");
                }).show();
        } else {
            new AlertDialog.Builder(this).setMessage("هل تريد مسح أرقام المساحات وإعداد الورقة من جديد؟")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("مسح", (d, w) -> {
                    for (int i = 1; i <= REGION_COUNT; i++) { assigned[i] = 0; filled[i] = false; }
                    selectedRegion = 0; activeColor = 0; saveProgress(); renderBoard(); refreshUi();
                    toast("تم مسح الإعداد.");
                }).show();
        }
    }

    private void showHelp() {
        String message = "إعداد الورقة أول مرة: المس كل مساحة بيضاء ثم اختر الرقم المطبوع بداخلها.\n\n" +
            "اللعب: بعد إكمال الإعداد، اختر رقم اللون من لوحة الألوان ثم المس المساحات التي تحمل الرقم نفسه.\n\n" +
            "لن تُلوّن المساحة بلون خاطئ، والخطوط السوداء تفصل بين المساحات. يُحفظ تقدم الطفل على الهاتف.";
        new AlertDialog.Builder(this).setTitle("طريقة اللعب").setMessage(message)
            .setPositiveButton("حسنًا", null).show();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private class BoardView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF imageRect = new RectF();
        private final ScaleGestureDetector scaleDetector;
        private float zoom = 1f;
        private float offsetX = 0f, offsetY = 0f;
        private float lastX, lastY, downX, downY;
        private boolean dragging = false;

        BoardView() {
            super(MainActivity.this);
            scaleDetector = new ScaleGestureDetector(MainActivity.this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScale(ScaleGestureDetector detector) {
                        float oldZoom = zoom;
                        zoom = Math.max(1f, Math.min(4f, zoom * detector.getScaleFactor()));
                        float cx = getWidth() / 2f, cy = getHeight() / 2f;
                        float fx = detector.getFocusX() - cx;
                        float fy = detector.getFocusY() - cy;
                        offsetX = (offsetX - fx) * (zoom / oldZoom) + fx;
                        offsetY = (offsetY - fy) * (zoom / oldZoom) + fy;
                        clampOffsets();
                        invalidate();
                        return true;
                    }
                });
        }

        void zoomBy(float factor) {
            float oldZoom = zoom;
            zoom = Math.max(1f, Math.min(4f, zoom * factor));
            float ratio = zoom / oldZoom;
            offsetX *= ratio;
            offsetY *= ratio;
            clampOffsets();
            invalidate();
        }

        void resetZoom() {
            zoom = 1f;
            offsetX = 0f;
            offsetY = 0f;
            invalidate();
        }

        private RectF baseRect() {
            float w = getWidth() - dp(10);
            float h = getHeight() - dp(10);
            float factor = Math.min(w / mapWidth, h / mapHeight);
            float dw = mapWidth * factor, dh = mapHeight * factor;
            float left = (getWidth() - dw) / 2f, top = (getHeight() - dh) / 2f;
            return new RectF(left, top, left + dw, top + dh);
        }

        private void clampOffsets() {
            RectF base = baseRect();
            float maxX = Math.max(0f, (base.width() * zoom - getWidth()) / 2f);
            float maxY = Math.max(0f, (base.height() * zoom - getHeight()) / 2f);
            offsetX = Math.max(-maxX, Math.min(maxX, offsetX));
            offsetY = Math.max(-maxY, Math.min(maxY, offsetY));
            if (zoom <= 1f) { offsetX = 0f; offsetY = 0f; }
        }

        RectF getImageRect() {
            RectF base = baseRect();
            float cx = getWidth() / 2f + offsetX;
            float cy = getHeight() / 2f + offsetY;
            float dw = base.width() * zoom, dh = base.height() * zoom;
            imageRect.set(cx - dw / 2f, cy - dh / 2f, cx + dw / 2f, cy + dh / 2f);
            return imageRect;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.WHITE);
            if (renderedBitmap != null) canvas.drawBitmap(renderedBitmap, null, getImageRect(), paint);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = lastX = event.getX();
                    downY = lastY = event.getY();
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    dragging = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (zoom > 1.02f && event.getPointerCount() == 1 && !scaleDetector.isInProgress()) {
                        float dx = event.getX() - lastX;
                        float dy = event.getY() - lastY;
                        if (Math.abs(event.getX() - downX) + Math.abs(event.getY() - downY) > dp(7)) dragging = true;
                        if (dragging) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                            offsetX += dx;
                            offsetY += dy;
                            clampOffsets();
                            invalidate();
                        }
                    }
                    lastX = event.getX();
                    lastY = event.getY();
                    return true;
                case MotionEvent.ACTION_UP:
                    getParent().requestDisallowInterceptTouchEvent(false);
                    if (!dragging && event.getPointerCount() == 1) {
                        performClick();
                        onBoardTap(event.getX(), event.getY());
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return true;
            }
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }
    }

    @Override protected void onDestroy() {
        if (renderedBitmap != null && !renderedBitmap.isRecycled()) renderedBitmap.recycle();
        super.onDestroy();
    }
}
