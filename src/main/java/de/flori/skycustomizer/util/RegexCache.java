package de.flori.skycustomizer.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class RegexCache {
    private static final Map<String, Optional<Pattern>> PATTERNS = new LinkedHashMap<>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Optional<Pattern>> e) { return size() > 128; }
    };
    private RegexCache() {}

    public static synchronized Pattern compile(String expression) {
        if (expression == null || expression.isEmpty()) return null;
        return PATTERNS.computeIfAbsent(expression, key -> {
            try { return Optional.of(Pattern.compile(key)); }
            catch (PatternSyntaxException e) { return Optional.empty(); }
        }).orElse(null);
    }
}
