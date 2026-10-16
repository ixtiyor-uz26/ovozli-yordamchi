package uz.ovozli;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Ovozdan kelgan matnni buyruqqa aylantiradi.
 * Android'ga bog'liq emas, shuning uchun alohida sinab ko'rish mumkin.
 */
public class CommandParser {

    public enum Type { CALL, YOUTUBE, OPEN, UNKNOWN }

    public static class Command {
        public final Type type;
        public final String arg;   // kimga qo'ng'iroq / nima qidirish / qaysi ilova
        public final String text;  // tozalangan asl matn

        Command(Type type, String arg, String text) {
            this.type = type;
            this.arg = arg;
            this.text = text;
        }

        @Override
        public String toString() { return type + "(" + arg + ")"; }
    }

    // ---------- Matnni tozalash ----------

    private static final String[][] CYR = {
        {"шч", "shch"}, {"ш", "sh"}, {"ч", "ch"}, {"ё", "yo"}, {"ю", "yu"}, {"я", "ya"},
        {"ў", "o'"}, {"ғ", "g'"}, {"қ", "q"}, {"ҳ", "h"}, {"ц", "ts"}, {"щ", "sh"},
        {"а", "a"}, {"б", "b"}, {"в", "v"}, {"г", "g"}, {"д", "d"}, {"е", "e"}, {"ж", "j"},
        {"з", "z"}, {"и", "i"}, {"й", "y"}, {"к", "k"}, {"л", "l"}, {"м", "m"}, {"н", "n"},
        {"о", "o"}, {"п", "p"}, {"р", "r"}, {"с", "s"}, {"т", "t"}, {"у", "u"}, {"ф", "f"},
        {"х", "x"}, {"ъ", "'"}, {"ы", "i"}, {"ь", ""}, {"э", "e"}
    };

    /** Kichik harf, kirill → lotin, apostroflarni bir xil qilish, tinish belgilarini olib tashlash. */
    public static String normalize(String s) {
        if (s == null) return "";
        String t = s.toLowerCase(Locale.ROOT);
        for (String[] p : CYR) t = t.replace(p[0], p[1]);
        t = t.replaceAll("[‘’ʻʼ`´]", "'");
        t = t.replaceAll("[^a-z0-9' +]", " ");
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }

    /** Apostroflarsiz variant (solishtirish uchun): "qo'ng'iroq" → "qongiroq". */
    public static String flat(String s) {
        return normalize(s).replace("'", "");
    }

    // ---------- Kalit so'zlar ----------

    private static final String[] CALL_WORDS = {
        "qongiroq qil", "qongiroq qilib ber", "qongiroq", "qongiroqlash",
        "telefon qil", "telefon qilib ber", "tel qil", "tel qilib ber", "telefon",
        "zvonit", "zvon qil", "pozvoni", "chaqir"
    };

    private static final String[] YOUTUBE_WORDS = {
        "youtubedan", "youtubeda", "youtubega", "youtube", "you tube",
        "yutubdan", "yutubda", "yutubga", "yutub", "yutubi", "utubdan", "utub"
    };

    private static final String[] MUSIC_WORDS = {"musiqa", "qoshiq", "qoshigini", "qoshiqni", "video", "klip"};

    private static final String[] OPEN_WORDS = {
        "ochib ber", "ishga tushir", "ichiga kir", "kirib ber", "kir", "och", "ochgin", "kirgin", "ot"
    };

    private static final String[] FILLER = {
        "iltimos", "menga", "hozir", "tezda", "tez", "ber", "qil", "qoy", "qoyib", "qoyib ber",
        "qoygin", "yoq", "top", "topib", "ijro et", "ijro", "eshittir", "ko'rsat", "korsat"
    };

    // ---------- Asosiy funksiya ----------

    public static Command parse(String raw) {
        String text = normalize(raw);
        String f = " " + flat(raw) + " ";

        // 1) Qo'ng'iroq
        for (String w : CALL_WORDS) {
            if (f.contains(" " + w + " ") || f.endsWith(" " + w + " ")) {
                String who = f.replace(" " + w + " ", " ");
                for (String x : CALL_WORDS) who = (" " + who + " ").replace(" " + x + " ", " ");
                who = removeWords(who, FILLER);
                who = stripSuffix(who.trim());
                return new Command(Type.CALL, who, text);
            }
        }

        // 2) YouTube / musiqa
        boolean yt = containsAny(f, YOUTUBE_WORDS);
        boolean music = containsAny(f, MUSIC_WORDS);
        if (yt || music) {
            String q = removeWords(f, YOUTUBE_WORDS);
            q = removeWords(q, FILLER);
            q = removeWords(q, OPEN_WORDS);
            q = q.trim();
            if (q.isEmpty()) q = "musiqa";
            return new Command(Type.YOUTUBE, q, text);
        }

        // 3) Ilovani ochish
        for (String w : OPEN_WORDS) {
            if (f.contains(" " + w + " ")) {
                String app = removeWords(f, OPEN_WORDS);
                app = removeWords(app, FILLER);
                app = stripSuffix(app.trim());
                if (!app.isEmpty()) return new Command(Type.OPEN, app, text);
            }
        }

        return new Command(Type.UNKNOWN, text, text);
    }

    // ---------- Yordamchi funksiyalar ----------

    private static boolean containsAny(String padded, String[] words) {
        for (String w : words) if (padded.contains(" " + w + " ")) return true;
        return false;
    }

    private static String removeWords(String s, String[] words) {
        String r = " " + s.trim() + " ";
        // uzunroq iboralarni avval olib tashlash
        List<String> list = new ArrayList<>(Arrays.asList(words));
        list.sort((a, b) -> b.length() - a.length());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String w : list) {
                String n = r.replace(" " + w + " ", " ");
                if (!n.equals(r)) { r = n; changed = true; }
            }
        }
        return r.replaceAll("\\s+", " ").trim();
    }

    /** "onamga" → "onam", "telegramga" → "telegram", "dadamni" → "dadam". */
    public static String stripSuffix(String w) {
        String s = w.trim();
        String[] suf = {"larga", "lariga", "imga", "iga", "ga", "ka", "qa", "ni", "dan", "da", "ning"};
        // faqat oxirgi so'zga qo'llanadi
        int sp = s.lastIndexOf(' ');
        String head = sp >= 0 ? s.substring(0, sp + 1) : "";
        String last = sp >= 0 ? s.substring(sp + 1) : s;
        for (String x : suf) {
            if (last.length() > x.length() + 2 && last.endsWith(x)) {
                last = last.substring(0, last.length() - x.length());
                break;
            }
        }
        return (head + last).trim();
    }

    /** Qarindoshlik so'zlarining sinonimlari: "oyim", "onajon" → "onam" va h.k. */
    public static String canonicalKin(String name) {
        String n = flat(name);
        if (n.matches("(onam|onajon|oyim|oyijon|oyi|ona|mama|mamam|ayam|ayajon|apam|onamla)")) return "onam";
        if (n.matches("(dadam|dadajon|dada|otam|otajon|ota|papa|papam|adam)")) return "dadam";
        return n;
    }
}
