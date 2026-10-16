package uz.ovozli;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQ_SPEECH = 1;
    private static final int REQ_PERMS = 2;
    private static final String PREFS = "ovozli";
    private static final String KEY_PEOPLE = "people"; // "nom|raqam" qatorlari

    private static final int GREEN = Color.parseColor("#0d6b56");
    private static final int GREEN_LIGHT = Color.parseColor("#e6f2ef");

    private TextView status;
    private TextView heard;
    private LinearLayout peopleBox;
    private String pendingNumber = null;

    // ===================== Ekran =====================

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(GREEN);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(32));
        root.setBackgroundColor(Color.WHITE);
        scroll.addView(root);

        TextView title = text("Ovozli yordamchi", 26, true, GREEN);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        Button mic = new Button(this);
        mic.setText("🎤");
        mic.setTextSize(48);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(GREEN);
        mic.setBackground(circle);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(150), dp(150));
        mp.gravity = Gravity.CENTER_HORIZONTAL;
        mp.topMargin = dp(24);
        root.addView(mic, mp);
        mic.setOnClickListener(v -> listen());

        status = text("Tugmani bosing va gapiring", 17, false, Color.DKGRAY);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(16), 0, 0);
        root.addView(status);

        heard = text("", 15, false, Color.GRAY);
        heard.setGravity(Gravity.CENTER);
        root.addView(heard);

        root.addView(section("Masalan:"));
        root.addView(text("• «Onamga telefon qil»\n• «Dadamga qo'ng'iroq qil»\n• «YouTube'dan Shoxrux qo'shig'ini qo'y»\n• «Musiqa qo'y»\n• «Telegramga kir»\n• «Instagramni och»", 15, false, Color.DKGRAY));

        root.addView(section("Yaqinlarim (tez qo'ng'iroq uchun)"));
        root.addView(text("Nom va raqam qo'shing. «Onam» deb saqlasangiz, «onamga», «oyimga» desangiz ham tushunadi. O'chirish uchun ustiga bosib turing.", 13, false, Color.GRAY));
        peopleBox = new LinearLayout(this);
        peopleBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(peopleBox);

        EditText name = edit("Nomi (masalan: Onam)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText phone = edit("Raqami (+998...)", InputType.TYPE_CLASS_PHONE);
        root.addView(name);
        root.addView(phone);
        Button add = button("Qo'shish");
        root.addView(add);
        add.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            String p = phone.getText().toString().trim();
            if (n.isEmpty() || p.isEmpty()) { toast("Nom va raqamni yozing"); return; }
            List<String[]> list = people();
            list.add(new String[]{n, p});
            savePeople(list);
            name.setText("");
            phone.setText("");
            renderPeople();
        });

        root.addView(section("Ruxsatlar"));
        Button perms = button("Qo'ng'iroq va kontaktlarga ruxsat berish");
        root.addView(perms);
        perms.setOnClickListener(v -> askPerms());

        setContentView(scroll);
        renderPeople();
        askPerms();

        // Ilova ochilishi bilan darhol tinglay boshlaydi
        if (b == null) mic.postDelayed(this::listen, 400);
    }

    // ===================== Ovozni tanish =====================

    private void listen() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uz-UZ");
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "uz-UZ");
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Gapiring...");
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        try {
            startActivityForResult(i, REQ_SPEECH);
            status.setText("Tinglayapman...");
        } catch (ActivityNotFoundException e) {
            status.setText("Telefonda ovozni tanish xizmati yo'q. Play Market'dan «Google» ilovasini o'rnating.");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_SPEECH) return;
        if (res != RESULT_OK || data == null) { status.setText("Eshitmadim. Qaytadan bosing."); return; }
        ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) { status.setText("Eshitmadim. Qaytadan bosing."); return; }

        // Bir nechta variantdan birinchi tushunarlisini olamiz
        CommandParser.Command cmd = null;
        String said = results.get(0);
        for (String r : results) {
            CommandParser.Command c = CommandParser.parse(r);
            if (c.type != CommandParser.Type.UNKNOWN) { cmd = c; said = r; break; }
        }
        heard.setText("«" + said + "»");
        if (cmd == null) {
            status.setText("Tushunmadim. Masalan: «Onamga telefon qil» deb ayting.");
            return;
        }
        run(cmd);
    }

    private void run(CommandParser.Command c) {
        switch (c.type) {
            case CALL: call(c.arg); break;
            case YOUTUBE: youtube(c.arg); break;
            case OPEN: openApp(c.arg); break;
            default: status.setText("Tushunmadim.");
        }
    }

    // ===================== 1) Qo'ng'iroq =====================

    private void call(String who) {
        if (who == null || who.trim().isEmpty()) { status.setText("Kimga qo'ng'iroq qilay?"); return; }
        String number = findInPeople(who);
        String shown = who;
        if (number == null) {
            String[] c = findInContacts(who);
            if (c != null) { shown = c[0]; number = c[1]; }
        }
        if (number == null) {
            status.setText("«" + who + "» topilmadi. Pastdagi «Yaqinlarim»ga qo'shing.");
            return;
        }
        status.setText(shown + "ga qo'ng'iroq qilinyapti...");
        dial(number);
    }

    private void dial(String number) {
        Uri u = Uri.parse("tel:" + number.replace(" ", ""));
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            try { startActivity(new Intent(Intent.ACTION_CALL, u)); return; } catch (Exception ignored) { }
        }
        // Ruxsat bo'lmasa: raqam terilgan oyna ochiladi, faqat yashil tugmani bosasiz
        startActivity(new Intent(Intent.ACTION_DIAL, u));
    }

    private String findInPeople(String who) {
        String want = CommandParser.canonicalKin(who);
        for (String[] p : people()) {
            String have = CommandParser.canonicalKin(p[0]);
            if (have.equals(want)) return p[1];
        }
        for (String[] p : people()) {
            String have = CommandParser.flat(p[0]);
            String w = CommandParser.flat(who);
            if (!w.isEmpty() && (have.startsWith(w) || w.startsWith(have))) return p[1];
        }
        return null;
    }

    /** Telefon kontaktlaridan qidirish (kirill nomlar ham lotinga o'girilib solishtiriladi). */
    private String[] findInContacts(String who) {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null;
        String want = CommandParser.flat(CommandParser.canonicalKin(who));
        String wantRaw = CommandParser.flat(who);
        String[] best = null;
        int bestScore = 0;
        try (Cursor cur = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER},
                null, null, null)) {
            if (cur == null) return null;
            while (cur.moveToNext()) {
                String name = cur.getString(0);
                String num = cur.getString(1);
                if (name == null || num == null) continue;
                String n = CommandParser.flat(name);
                String nk = CommandParser.flat(CommandParser.canonicalKin(name));
                int score = 0;
                if (n.equals(wantRaw) || nk.equals(want)) score = 3;
                else if (n.startsWith(wantRaw) || n.startsWith(want)) score = 2;
                else if (n.contains(wantRaw) || n.contains(want)) score = 1;
                if (score > bestScore) { bestScore = score; best = new String[]{name, num}; }
            }
        } catch (Exception ignored) { }
        return best;
    }

    // ===================== 2) YouTube =====================

    private void youtube(String query) {
        status.setText("YouTube: «" + query + "»");
        try {
            String url = "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8");
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.setPackage("com.google.android.youtube");
            try { startActivity(i); }
            catch (ActivityNotFoundException e) { i.setPackage(null); startActivity(i); }
        } catch (Exception e) {
            status.setText("YouTube ochilmadi.");
        }
    }

    // ===================== 3) Ilovani ochish =====================

    private void openApp(String name) {
        String n = CommandParser.flat(name);

        // Maxsus holatlar
        if (n.startsWith("sozlama") || n.startsWith("nastroy")) { startActivity(new Intent(Settings.ACTION_SETTINGS)); done(name); return; }
        if (n.startsWith("kamera") || n.startsWith("kamer")) {
            try { startActivity(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)); done(name); return; } catch (Exception ignored) { }
        }

        String[][] known = {
            {"telegram", "org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram"},
            {"youtube", "com.google.android.youtube"}, {"yutub", "com.google.android.youtube"},
            {"instagram", "com.instagram.android"}, {"instagra", "com.instagram.android"},
            {"whatsapp", "com.whatsapp"}, {"vatsap", "com.whatsapp"}, {"vatsapp", "com.whatsapp"},
            {"olx", "uz.olx.android", "com.olx.southasia", "pl.tablica"},
            {"click", "air.com.ssdsoftwaresolutions.clickuz"}, {"payme", "uz.dida.payme"},
            {"yandex", "ru.yandex.taxi", "ru.yandex.searchplugin"}, {"taksi", "ru.yandex.taxi"},
            {"chrome", "com.android.chrome"}, {"brauzer", "com.android.chrome"},
            {"gmail", "com.google.android.gm"}, {"xarita", "com.google.android.apps.maps"},
            {"karta", "com.google.android.apps.maps"}, {"tiktok", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill"}
        };
        for (String[] k : known) {
            if (n.startsWith(k[0]) || k[0].startsWith(n)) {
                for (int j = 1; j < k.length; j++) {
                    Intent li = getPackageManager().getLaunchIntentForPackage(k[j]);
                    if (li != null) { startActivity(li); done(name); return; }
                }
            }
        }

        // Telefondagi barcha ilovalar ichidan nomi bo'yicha qidirish
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = getPackageManager().queryIntentActivities(main, 0);
        ResolveInfo best = null;
        int bestScore = 0;
        for (ResolveInfo ri : apps) {
            String label = CommandParser.flat(String.valueOf(ri.loadLabel(getPackageManager())));
            int score = 0;
            if (label.equals(n)) score = 3;
            else if (label.startsWith(n) || n.startsWith(label)) score = 2;
            else if (label.contains(n)) score = 1;
            if (score > bestScore) { bestScore = score; best = ri; }
        }
        if (best != null) {
            Intent li = getPackageManager().getLaunchIntentForPackage(best.activityInfo.packageName);
            if (li != null) { startActivity(li); done(name); return; }
        }
        status.setText("«" + name + "» ilovasi topilmadi.");
    }

    private void done(String name) { status.setText(name + " ochilyapti..."); }

    // ===================== Yaqinlar ro'yxati =====================

    private List<String[]> people() {
        List<String[]> out = new ArrayList<>();
        String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_PEOPLE, "");
        for (String line : raw.split("\n")) {
            int i = line.indexOf('|');
            if (i > 0) out.add(new String[]{line.substring(0, i), line.substring(i + 1)});
        }
        return out;
    }

    private void savePeople(List<String[]> list) {
        StringBuilder sb = new StringBuilder();
        for (String[] p : list) sb.append(p[0].replace("|", " ")).append('|').append(p[1]).append('\n');
        SharedPreferences.Editor e = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        e.putString(KEY_PEOPLE, sb.toString()).apply();
    }

    private void renderPeople() {
        peopleBox.removeAllViews();
        List<String[]> list = people();
        if (list.isEmpty()) {
            peopleBox.addView(text("Hozircha hech kim qo'shilmagan.", 14, false, Color.GRAY));
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            final int idx = i;
            String[] p = list.get(i);
            TextView row = text("📞  " + p[0] + "   " + p[1], 16, false, Color.BLACK);
            row.setPadding(dp(12), dp(12), dp(12), dp(12));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(GREEN_LIGHT);
            bg.setCornerRadius(dp(10));
            row.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(8);
            peopleBox.addView(row, lp);
            row.setOnClickListener(v -> dial(p[1]));
            row.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setMessage(p[0] + " o'chirilsinmi?")
                        .setPositiveButton("O'chirish", (d, w) -> {
                            List<String[]> l = people();
                            if (idx < l.size()) l.remove(idx);
                            savePeople(l);
                            renderPeople();
                        })
                        .setNegativeButton("Yo'q", null)
                        .show();
                return true;
            });
        }
    }

    // ===================== Ruxsatlar =====================

    private void askPerms() {
        List<String> need = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.CALL_PHONE);
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.READ_CONTACTS);
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERMS);
    }

    // ===================== Kichik yordamchilar =====================

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private TextView text(String s, int size, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView section(String s) {
        TextView t = text(s, 18, true, GREEN);
        t.setPadding(0, dp(28), 0, dp(6));
        return t;
    }

    private EditText edit(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setTextSize(16);
        return e;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(GREEN);
        bg.setCornerRadius(dp(12));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
