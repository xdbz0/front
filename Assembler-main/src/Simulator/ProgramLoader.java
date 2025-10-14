package Simulator;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

public class ProgramLoader {

    private boolean octalListingDetected = false;

    public void load(File file, int[] memory) throws Exception {
        if (memory.length < 2048)
            throw new IllegalArgumentException("Memory must be at least 2048 words");

        Arrays.fill(memory, 0);
        int nextAddr = 0;

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {

            String line;
            while ((line = br.readLine()) != null) {
                String raw = stripInlineComments(line).trim();
                if (raw.isEmpty()) continue;

                // ORG / LOC / @
                String upper = raw.toUpperCase(Locale.ROOT);
                if (upper.startsWith("ORG ") || upper.startsWith("LOC ") || raw.startsWith("@")) {
                    String addrStr = raw.replaceFirst("(?i)^(ORG|LOC)\\s*|^@", "").trim();
                    nextAddr = parseIntAuto(addrStr);
                    checkAddr(nextAddr);
                    continue;
                }

                // address:value
                int colon = raw.indexOf(':');
                if (colon >= 0) {
                    String aStr = raw.substring(0, colon).trim();
                    String vStr = raw.substring(colon + 1).trim();
                    Integer aNum = parseNumericSafe(aStr);
                    Integer vNum = parseNumericSafe(vStr);
                    if (aNum != null && vNum != null) {
                        int addr = aNum;
                        int val = vNum & 0xFFFF;
                        checkAddr(addr);
                        memory[addr] = val;
                        continue;
                    }
                }

                // token-based tolerant parsing
                List<String> tokens = splitTokens(raw);
                if (tokens.isEmpty()) continue;


                //  "... data N" 行
                int dataIdx = indexOfIgnoreCase(tokens, "data");
                if (dataIdx >= 0) {
                    if (dataIdx >= 2) {
                        String t0 = tokens.get(0);
                        String t1 = tokens.get(1);
                        boolean looksOctPair =
                                DIGITS_0_7.matcher(t0).matches() && DIGITS_0_7.matcher(t1).matches();

                        Integer aTry = parseNumericSafe(t0);
                        Integer wTry = parseNumericSafe(t1);
                        if (aTry != null && wTry != null) {
                            int addr = parseWithHint(t0, looksOctPair);
                            int val  = parseWithHint(t1, looksOctPair) & 0xFFFF;
                            checkAddr(addr);
                            memory[addr] = val;
                            continue;
                        }
                    }

                    if (dataIdx + 1 < tokens.size()) {
                        Integer v = parseNumericSafe(tokens.get(dataIdx + 1));
                        if (v != null) {
                            int val = v & 0xFFFF;
                            checkAddr(nextAddr);
                            memory[nextAddr++] = val;
                        }
                    }
                    continue;
                }

                List<Integer> nums = allNumeric(tokens);
                if (nums.size() >= 2) {
                    String t0 = tokens.get(0);
                    String t1 = tokens.get(1);
                    boolean looksOctPair = DIGITS_0_7.matcher(t0).matches() && DIGITS_0_7.matcher(t1).matches();

                    Integer addrTry = parseNumericSafe(t0);
                    Integer wordTry = parseNumericSafe(t1);
                    if (addrTry != null && wordTry != null) {
                        int addr = parseWithHint(t0, looksOctPair);
                        int val  = parseWithHint(t1, looksOctPair) & 0xFFFF;
                        checkAddr(addr);
                        memory[addr] = val;
                        continue;
                    }
                }

                if (nums.size() == 1) {
                    int val = nums.get(0) & 0xFFFF;
                    checkAddr(nextAddr);
                    memory[nextAddr++] = val;
                    continue;
                }
            }
        }
    }

    // -------- helpers --------

    private static String stripInlineComments(String s) {
        int cut = s.length();
        int i1 = s.indexOf(';');  if (i1 >= 0) cut = Math.min(cut, i1);
        int i2 = s.indexOf('#');  if (i2 >= 0) cut = Math.min(cut, i2);
        int i3 = s.indexOf("//"); if (i3 >= 0) cut = Math.min(cut, i3);
        return s.substring(0, cut);
    }

    private static void checkAddr(int addr) throws Exception {
        if (addr < 0 || addr >= 2048) throw new Exception("Address out of range: " + addr);
    }

    private static final Pattern BIN = Pattern.compile("[01]{1,16}");
    private static final Pattern OCT = Pattern.compile("[0-7]+");

    private static final Pattern DIGITS_0_7 = Pattern.compile("^[0-7]+$");
    private int parseWithHint(String s, boolean preferOctal) throws Exception {
        String t = s.replace("_", "").trim().toLowerCase(Locale.ROOT);
        if (preferOctal && DIGITS_0_7.matcher(t).matches() && !t.startsWith("0x")) {
            int v = Integer.parseInt(t, 8);
            octalListingDetected = true;
            return v;
        }
        return parseIntAuto(s);
    }

    private Integer parseNumericSafe(String token) {
        try { return parseIntAuto(token); }
        catch (Exception ignore) { return null; }
    }

    private List<Integer> allNumeric(List<String> tokens) {
        List<Integer> out = new ArrayList<>();
        for (String t : tokens) {
            Integer v = parseNumericSafe(t);
            if (v != null) out.add(v);
        }
        return out;
    }

    private static int indexOfIgnoreCase(List<String> arr, String key) {
        for (int i = 0; i < arr.size(); i++)
            if (arr.get(i).equalsIgnoreCase(key)) return i;
        return -1;
    }

    private static List<String> splitTokens(String raw) {
        String[] rough = raw.split("\\s+");
        List<String> out = new ArrayList<>();
        for (String r : rough) {
            if (r.isEmpty()) continue;
            for (String p : r.split(",")) {
                String t = trimPunct(p);
                if (!t.isEmpty()) out.add(t);
            }
        }
        return out;
    }

    private static String trimPunct(String s) {
        int i = 0, j = s.length() - 1;
        while (i <= j && (s.charAt(i) == ',' || s.charAt(i) == ':' || s.charAt(i) == ';')) i++;
        while (j >= i && (s.charAt(j) == ',' || s.charAt(j) == ':' || s.charAt(j) == ';')) j--;
        return (i <= j) ? s.substring(i, j + 1) : "";
    }
    
    private int parseIntAuto(String s) throws Exception {
        String t = s.replace("_", "").trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) throw new NumberFormatException("empty");

        if (t.startsWith("0x")) return Integer.parseInt(t.substring(2), 16);        // hex
        if (t.endsWith("o") || t.endsWith("q")) return Integer.parseInt(t.substring(0, t.length()-1), 8); // explicit octal
        if (BIN.matcher(t).matches()) return Integer.parseInt(t, 2);                // binary

        if (octalListingDetected && OCT.matcher(t).matches())                        // prefer octal if detected
            return Integer.parseInt(t, 8);

        try {
            int dec = Integer.parseInt(t, 10);
            if (dec > 0xFFFF && OCT.matcher(t).matches()) {                          // too big → try octal
                int oct = Integer.parseInt(t, 8);
                if (oct <= 0xFFFF) { octalListingDetected = true; return oct; }
            }
            return dec;
        } catch (NumberFormatException e) {
            if (OCT.matcher(t).matches()) {
                int oct = Integer.parseInt(t, 8);
                if (oct <= 0xFFFF) octalListingDetected = true;
                return oct;
            }
            throw e;
        }
    }

    private int parseWord(String s) throws Exception {
        int v = parseIntAuto(s);
        if (v < 0 || v > 0xFFFF) throw new Exception("Word out of 16-bit range: " + s);
        return v & 0xFFFF;
    }
}
