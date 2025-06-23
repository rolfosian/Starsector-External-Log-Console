package data.scripts;

import org.json.*;
import org.apache.log4j.Logger;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

import javax.swing.text.Style;

import data.scripts.CustomConsoleWindow.TextSegment;

public class TextMateGrammar {
    private static final Logger log = Logger.getLogger(TextMateGrammar.class);
    
    private static Map<String, Pattern> patternCache = new HashMap<>();
    private static JSONObject cachedGrammar = null;
    
    private JSONObject grammar;
    private JSONObject theme;
    public Map<String, StyleInfo> scopeToStyle = new HashMap<>();
    
    public static class StyleInfo {
        public String foreground;
        public String background;
        public String fontStyle;
        
        public StyleInfo(String foreground, String background, String fontStyle) {
            this.foreground = foreground;
            this.background = background;
            this.fontStyle = fontStyle;
        }
    }
    
    public TextMateGrammar() {
        loadGrammar();
        loadTheme();
        buildScopeMap();
    }
    
    private void loadGrammar() {
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("data/scripts/log.tmLanguage.json");
            if (is == null) {
                log.error("Could not load log.tmLanguage.json from classpath");
                return;
            }
            
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            grammar = new JSONObject(content);
            
            // Cache patterns when grammar is loaded
            cachePatterns();
            
            log.info("Successfully loaded log grammar");
        } catch (Exception e) {
            log.error("Error loading grammar: " + e.getMessage(), e);
        }
    }
    
    private void loadTheme() {
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("data/scripts/dark_vs.json");
            if (is == null) {
                log.error("Could not load dark_vs.json from classpath");
                return;
            }
            
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            theme = new JSONObject(content);
            log.info("Successfully loaded dark theme");
        } catch (Exception e) {
            log.error("Error loading theme: " + e.getMessage(), e);
        }
    }
    
    private void buildScopeMap() {
        if (theme == null) return;
        
        try {
            JSONArray tokenColors = theme.getJSONArray("tokenColors");
            for (int i = 0; i < tokenColors.length(); i++) {
                JSONObject tokenColor = tokenColors.getJSONObject(i);
                JSONObject settings = tokenColor.getJSONObject("settings");
                
                String foreground = settings.optString("foreground", null);
                String background = settings.optString("background", null);
                String fontStyle = settings.optString("fontStyle", null);
                
                StyleInfo styleInfo = new StyleInfo(foreground, background, fontStyle);
                
                Object scopeObj = tokenColor.get("scope");
                if (scopeObj instanceof String) {
                    scopeToStyle.put((String) scopeObj, styleInfo);

                } else if (scopeObj instanceof JSONArray) {
                    JSONArray scopeArray = (JSONArray) scopeObj;
                    for (int j = 0; j < scopeArray.length(); j++) {
                        scopeToStyle.put(scopeArray.getString(j), styleInfo);
                    }
                }
            }
            log.info("Built scope map with " + scopeToStyle.size() + " entries");
        } catch (Exception e) {
            log.error("Error building scope map: " + e.getMessage(), e);
        }
    }
    
    private static class CachedPattern {
        Pattern pattern;
        String scope;
        boolean isRange;
        Pattern endPattern; // Only used for range patterns
        JSONArray nestedPatterns;
    }
    private static List<CachedPattern> cachedPatternList = new ArrayList<>();

    private void cachePatterns() {
        if (grammar == null) return;

        if (cachedGrammar == grammar) return;
        
        patternCache.clear();
        cachedGrammar = grammar;
        
        try {
            JSONArray patterns = grammar.getJSONArray("patterns");
            cachePatternsRecursive(patterns);
            log.info("Cached " + patternCache.size() + " regex patterns");
        } catch (Exception e) {
            log.error("Error caching patterns: " + e.getMessage(), e);
        }
    }
    
    private void cachePatternsRecursive(JSONArray patterns) {
        for (int i = 0; i < patterns.length(); i++) {
            try {
                JSONObject pattern = patterns.getJSONObject(i);
                CachedPattern cp = new CachedPattern();
    
                if (pattern.has("match")) {
                    String regex = cleanRegexPattern(pattern.getString("match"));
                    cp.pattern = patternCache.computeIfAbsent(regex, r -> {
                        try {
                            return Pattern.compile(r);
                        } catch (PatternSyntaxException e) {
                            log.warn("Invalid regex: " + r, e);
                            return null;
                        }
                    });
                    cp.scope = pattern.optString("name", "");
                    cp.isRange = false;
                    cachedPatternList.add(cp);
                }
    
                if (pattern.has("begin") && pattern.has("end")) {
                    String begin = cleanRegexPattern(pattern.getString("begin"));
                    String end = cleanRegexPattern(pattern.getString("end"));
                    cp.pattern = patternCache.computeIfAbsent(begin, r -> {
                        try {
                            return Pattern.compile(r);
                        } catch (PatternSyntaxException e) {
                            log.warn("Invalid begin pattern: " + r, e);
                            return null;
                        }
                    });
                    cp.endPattern = patternCache.computeIfAbsent(end, r -> {
                        try {
                            return Pattern.compile(r);
                        } catch (PatternSyntaxException e) {
                            log.warn("Invalid end pattern: " + r, e);
                            return null;
                        }
                    });
                    cp.scope = pattern.optString("name", "");
                    cp.isRange = true;
                    cachedPatternList.add(cp);
                }
    
                if (pattern.has("patterns")) {
                    cp.nestedPatterns = pattern.getJSONArray("patterns");
                    cachePatternsRecursive(cp.nestedPatterns);
                }
            } catch (JSONException e) {
                log.warn("Pattern parsing error at index " + i + ": " + e.getMessage());
            }
        }
    }
    
    
    public void parseLine(String line, CustomConsoleWindow console, TextSegment segment, int startOffset) {
        List<int[]> styledRanges = new ArrayList<>();
        for (CachedPattern cp : cachedPatternList) {
            if (cp.pattern == null) continue;

            Matcher m = cp.pattern.matcher(line);
            if (!cp.isRange) {
                while (m.find()) {
                    int start = m.start();
                    int end = m.end();

                    if(isOverlapping(start, end, styledRanges)) continue;
                    styledRanges.add(new int[]{start,end});

                    MatchResult match = new MatchResult(start, end - start, cp.scope, m.group());
                    Style style = console.createStyleFromScope(cp.scope);

                    if (style != null) {
                        int absoluteStart = startOffset + match.start;
                        console.getDoc().setCharacterAttributes(absoluteStart, match.length, style, true);
                        for (int j = 0; j < match.length; j++) {
                            segment.syntaxHighlights.put(absoluteStart + j, style);
                        }
                    }
                }

            } else {
                while (m.find()) {
                    int beginEnd = m.end();
                    String remaining = line.substring(beginEnd);
                    Matcher endM = cp.endPattern.matcher(remaining);

                    if (endM.find()) {
                        int start = m.start();
                        int endEnd = beginEnd + endM.end();
                        if(isOverlapping(start, endEnd, styledRanges)) continue;
                        styledRanges.add(new int[]{start,endEnd});
                        MatchResult match = new MatchResult(m.start(), endEnd - m.start(), cp.scope, line.substring(m.start(), endEnd));
                        Style style = console.createStyleFromScope(cp.scope);

                        if (style != null) {
                            int absoluteStart = startOffset + match.start;
                            console.getDoc().setCharacterAttributes(absoluteStart, match.length, style, true);
                            for (int j = 0; j < match.length; j++) {
                                segment.syntaxHighlights.put(absoluteStart + j, style);
                            }
                        }
                    }
                }
            }
        }    
        return;
    }

    private boolean isOverlapping(int start, int end, List<int[]> styledRanges) {
        for (int[] range : styledRanges) {
            if (start < range[1] && end > range[0]) {
                return true;
            }
        }
        return false;
    }

    public StyleInfo getStyleForScope(String scope) {
        return scopeToStyle.get(scope);
    }
    
    public static class MatchResult {
        public final int start;
        public final int length;
        public final String scope;
        public final String text;
        
        public MatchResult(int start, int length, String scope, String text) {
            this.start = start;
            this.length = length;
            this.scope = scope;
            this.text = text;
        }
    }
    
    public static java.awt.Color hexToColor(String hex) {
        if (hex == null || hex.isEmpty()) return null;
        
        try {
            if (hex.startsWith("#")) {
                hex = hex.substring(1);
            }
            
            if (hex.length() == 6) {
                int r = Integer.parseInt(hex.substring(0, 2), 16);
                int g = Integer.parseInt(hex.substring(2, 4), 16);
                int b = Integer.parseInt(hex.substring(4, 6), 16);
                return new java.awt.Color(r, g, b);
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid hex color: " + hex);
        }
        
        return null;
    }
    
    private String cleanRegexPattern(String pattern) {
        if (pattern == null) return "";
        
        String cleaned = pattern;
        cleaned = cleaned.replaceAll("\\\\p(?![\\w{])", "\\\\p{L}");
        cleaned = cleaned.replaceAll("\\\\p\\{\\}", "\\\\p{L}");
        
        return cleaned;
    }
    
    public Map<String, StyleInfo> getScopeToStyle() {
        return scopeToStyle;
    }

    public class RegexTests {
        public List<MatchResult> parseLine(String line, CustomConsoleWindow console, TextSegment segment, int startOffset) {
            List<MatchResult> allResults = new ArrayList<>();
            if (grammar == null) return allResults;
        
            try {
                JSONArray patterns = grammar.getJSONArray("patterns");
                parsePatternsWithTiming(patterns, line, 0, allResults, console, segment, startOffset);
                return allResults;
            } catch (Exception e) {
                log.error("Error parsing line: " + e.getMessage(), e);
            }
        
            return allResults;
        }
        
        private void parsePatternsWithTiming(JSONArray patterns, String line, int startPos, List<MatchResult> results, CustomConsoleWindow console, TextSegment segment, int startOffset) {
            for (int i = 0; i < patterns.length(); i++) {
                try {
                    JSONObject pattern = patterns.getJSONObject(i);
        
                    if (pattern.has("match")) {
                        String rawRegex = pattern.getString("match");
                        String name = pattern.optString("name", "");
                        String cleanedRegex = cleanRegexPattern(rawRegex);
                        Pattern p = patternCache.get(cleanedRegex);
        
                        if (p != null) {
                            long startTime = System.nanoTime();
                            Matcher m = p.matcher(line);
                            while (m.find()) {
                                int start = m.start();
                                int end = m.end();
                                MatchResult match = new MatchResult(start, end - start, name, m.group());
                                Style style = console.createStyleFromScope(match.scope);
                                if (style != null) {
                                    int absoluteStart = startOffset + match.start;
                                    console.getDoc().setCharacterAttributes(absoluteStart, match.length, style, true);
                                    for (int j = 0; j < match.length; j++) {
                                        segment.syntaxHighlights.put(absoluteStart + j, style);
                                    }
                                }
                            }
                            long duration = System.nanoTime() - startTime;
                            if (duration > 10_000_000) {
                                log.warn("Slow regex: " + rawRegex + " (scope: " + name + ") took " + (duration / 1_000_000) + "ms");
                            }
                        }
                    }
        
                    if (pattern.has("begin") && pattern.has("end")) {
                        String beginRegex = pattern.getString("begin");
                        String endRegex = pattern.getString("end");
                        String name = pattern.optString("name", "");
        
                        String cleanedBegin = cleanRegexPattern(beginRegex);
                        String cleanedEnd = cleanRegexPattern(endRegex);
        
                        Pattern beginPattern = patternCache.get(cleanedBegin);
                        Pattern endPattern = patternCache.get(cleanedEnd);
        
                        if (beginPattern != null && endPattern != null) {
                            long startTime = System.nanoTime();
                            Matcher beginMatcher = beginPattern.matcher(line);
                            while (beginMatcher.find()) {
                                int beginStart = beginMatcher.start();
                                int beginEnd = beginMatcher.end();
        
                                String remaining = line.substring(beginEnd);
                                Matcher endMatcher = endPattern.matcher(remaining);
                                if (endMatcher.find()) {
                                    int endStart = beginEnd + endMatcher.start();
                                    int endEnd = beginEnd + endMatcher.end();
                                    MatchResult match = new MatchResult(beginStart, endEnd - beginStart, name, line.substring(beginStart, endEnd));
        
                                    Style style = console.createStyleFromScope(match.scope);
                                    if (style != null) {
                                        int absoluteStart = startOffset + match.start;
                                        console.getDoc().setCharacterAttributes(absoluteStart, match.length, style, true);
                                        for (int j = 0; j < match.length; j++) {
                                            segment.syntaxHighlights.put(absoluteStart + j, style);
                                        }
                                    }
                                }
                            }
                            long duration = System.nanoTime() - startTime;
                            if (duration > 10_000_000) {
                                log.warn("Slow begin/end regex: " + beginRegex + " / " + endRegex + " (scope: " + name + ") took " + (duration / 1_000_000) + "ms");
                            }
                        }
                    }
        
                    if (pattern.has("patterns")) {
                        parsePatternsWithTiming(pattern.getJSONArray("patterns"), line, startPos, results, console, segment, startOffset);
                    }
        
                } catch (JSONException e) {
                    log.warn("Error parsing pattern at index " + i + ": " + e.getMessage());
                }
            }
        }
    
        private void parsePatterns(JSONArray patterns, String line, int startPos, List<MatchResult> results, CustomConsoleWindow console, TextSegment segment, int startOffset) {
            for (int i = 0; i < patterns.length(); i++) {
                try {
                    JSONObject pattern = patterns.getJSONObject(i);
                    
                    if (pattern.has("match")) {
                        String regex = pattern.getString("match");
                        String name = pattern.optString("name", "");
                        
                        String cleanedRegex = cleanRegexPattern(regex);
                        Pattern p = patternCache.get(cleanedRegex);
                        
                        if (p != null) {
                            Matcher m = p.matcher(line);
                            
                            while (m.find()) {
                                int start = m.start();
                                int end = m.end();
                                MatchResult match = new MatchResult(start, end - start, name, m.group());
                                Style style = console.createStyleFromScope(match.scope);
                                if (style != null) {
                                    int absoluteStart = startOffset + match.start;
                                    console.getDoc().setCharacterAttributes(absoluteStart, match.length, style, true);
                                    
                                    for (int j = 0; j < match.length; j++) {
                                        segment.syntaxHighlights.put(absoluteStart + j, style);
                                    }
                                }
                            }
                        }
                    }
                    
                    if (pattern.has("begin") && pattern.has("end")) {
                        String beginRegex = pattern.getString("begin");
                        String endRegex = pattern.getString("end");
                        String name = pattern.optString("name", "");
                        
                        String cleanedBeginRegex = cleanRegexPattern(beginRegex);
                        String cleanedEndRegex = cleanRegexPattern(endRegex);
                        
                        Pattern beginPattern = patternCache.get(cleanedBeginRegex);
                        Pattern endPattern = patternCache.get(cleanedEndRegex);
                        
                        if (beginPattern != null && endPattern != null) {
                            Matcher beginMatcher = beginPattern.matcher(line);
                            while (beginMatcher.find()) {
                                int beginStart = beginMatcher.start();
                                int beginEnd = beginMatcher.end();
                                
                                String remainingLine = line.substring(beginEnd);
                                Matcher endMatcher = endPattern.matcher(remainingLine);
                                
                                if (endMatcher.find()) {
                                    int endStart = beginEnd + endMatcher.start();
                                    int endEnd = beginEnd + endMatcher.end();
                                    
                                    MatchResult match = new MatchResult(beginStart, endEnd - beginStart, name, line.substring(beginStart, endEnd));
    
                                    try {
                                        Style style = console.createStyleFromScope(match.scope);
                                        if (style != null) {
                                            int absoluteStart = startOffset + match.start;
                                            console.getDoc().setCharacterAttributes(absoluteStart, match.length, style, true);
                                            
                                            for (int j = 0; j < match.length; j++) {
                                                segment.syntaxHighlights.put(absoluteStart + j, style);
                                            }
                                        }
                                    } catch (Exception e) {
                                        log.error("Error applying syntax highlighting: " + e.getMessage(), e);
                                    }
                                }
                            }
                        }
                    }
                    
                    if (pattern.has("patterns")) {
                        parsePatterns(pattern.getJSONArray("patterns"), line, startPos, results, console, segment, startOffset);
                    }
                    
                } catch (JSONException e) {
                    log.warn("Error parsing pattern at index " + i + ": " + e.getMessage());
                }
            }
        }    
    }
}