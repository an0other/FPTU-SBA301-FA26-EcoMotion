package com.fptufa26.ecomotion.review.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Chặn nhận xét có từ ngữ thô tục/xúc phạm trước khi lưu.
 *
 * <p>So khớp GIỮ NGUYÊN DẤU tiếng Việt: bỏ dấu sẽ chặn nhầm từ bình thường ("lớn"→"lon", "các"→"cac",
 * "buổi"→"buoi", "đeo"→"deo", "du lịch"). Chỉ thêm biến thể không dấu khi không thể mang nghĩa khác
 * ("vcl", "dcm", "dit me"). So khớp theo nguyên từ/cụm từ; ký tự lặp từ 3 lần trở lên được rút gọn
 * ("đụuuu" → "đụ") và vài ký tự lách luật được quy đổi (0→o, @→a, $→s).
 */
public final class ReviewContentPolicy {

    /** Từ đơn — khớp nguyên từ (đã chữ thường, NFC). */
    private static final Set<String> BANNED_WORDS = Set.of(
            // tiếng Việt có dấu
            "đụ", "địt", "lồn", "cặc", "buồi", "đéo", "đĩ", "đcm", "đkm", "đm", "đmm", "đếch",
            // viết tắt / không dấu không có nghĩa khác
            "dcm", "dkm", "dmm", "đjt", "djt", "cc", "vcl", "vkl", "vcc", "clgt", "cmn", "ditme", "dume", "duma",
            // tiếng Anh
            "fuck", "fucking", "fucker", "shit", "bitch", "bastard", "asshole", "pussy", "motherfucker");

    /** Cụm từ — khớp theo ranh giới từ. */
    private static final List<String> BANNED_PHRASES = List.of(
            "địt mẹ", "đụ má", "đụ mẹ", "đù má", "vãi lồn", "vãi cứt", "ăn cứt", "óc chó", "thằng chó",
            "con chó này", "đồ chó", "con đĩ", "đồ ngu", "thằng ngu", "con ngu", "mẹ mày", "cha mày",
            "dit me", "du ma", "djt me", "vai lon", "oc cho", "do ngu", "thang ngu");

    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    private ReviewContentPolicy() {
    }

    /** Trả về các từ/cụm vi phạm (rỗng nếu hợp lệ). */
    public static List<String> violations(String text) {
        List<String> found = new ArrayList<>();
        if (text == null || text.isBlank()) return found;
        String normalized = normalize(text);
        if (normalized.isEmpty()) return found;
        for (String word : normalized.split(" ")) {
            if (BANNED_WORDS.contains(word) && !found.contains(word)) found.add(word);
        }
        String padded = " " + normalized + " ";
        for (String phrase : BANNED_PHRASES) {
            if (padded.contains(" " + phrase + " ") && !found.contains(phrase)) found.add(phrase);
        }
        return found;
    }

    public static boolean isClean(String text) {
        return violations(text).isEmpty();
    }

    static String normalize(String text) {
        String nfc = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
                .replace('0', 'o').replace('@', 'a').replace('$', 's');
        String spaced = NON_WORD.matcher(nfc).replaceAll(" ").trim();
        return collapseLongRuns(spaced);
    }

    /**
     * Rút gọn chuỗi từ 3 ký tự trở lên có cùng chữ cái gốc ("đụuuu" → "đụ", "ngggu" → "ngu"),
     * giữ ký tự đầu (có thể mang dấu). Cặp 2 ký tự như "ưu", "cc" được giữ nguyên.
     */
    private static String collapseLongRuns(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char first = text.charAt(i);
            char base = baseLetter(first);
            int j = i + 1;
            while (j < text.length() && baseLetter(text.charAt(j)) == base && Character.isLetter(first)) j++;
            out.append(first);
            if (j - i < 3) out.append(text, i + 1, j);
            i = j;
        }
        return out.toString();
    }

    private static char baseLetter(char c) {
        if (c == 'đ') return 'd';
        String decomposed = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
        return decomposed.isEmpty() ? c : decomposed.charAt(0);
    }
}
