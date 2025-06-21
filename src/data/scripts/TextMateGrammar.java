package data.scripts;

import org.json.*;
import org.apache.log4j.Logger;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

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
                
                if (pattern.has("match")) {
                    String regex = pattern.getString("match");
                    String cleanedRegex = cleanRegexPattern(regex);
                    if (!patternCache.containsKey(cleanedRegex)) {
                        try {
                            Pattern p = Pattern.compile(cleanedRegex);
                            patternCache.put(cleanedRegex, p);
                        } catch (PatternSyntaxException e) {
                            log.warn("Invalid regex pattern at index " + i + ": " + regex + " - " + e.getMessage());
                        }
                    }
                }
                
                if (pattern.has("begin") && pattern.has("end")) {
                    String beginRegex = pattern.getString("begin");
                    String endRegex = pattern.getString("end");
                    
                    String cleanedBeginRegex = cleanRegexPattern(beginRegex);
                    String cleanedEndRegex = cleanRegexPattern(endRegex);
                    
                    if (!patternCache.containsKey(cleanedBeginRegex)) {
                        try {
                            Pattern beginPattern = Pattern.compile(cleanedBeginRegex);
                            patternCache.put(cleanedBeginRegex, beginPattern);
                        } catch (PatternSyntaxException e) {
                            log.warn("Invalid begin regex pattern at index " + i + ": " + beginRegex + " - " + e.getMessage());
                        }
                    }
                    
                    if (!patternCache.containsKey(cleanedEndRegex)) {
                        try {
                            Pattern endPattern = Pattern.compile(cleanedEndRegex);
                            patternCache.put(cleanedEndRegex, endPattern);
                        } catch (PatternSyntaxException e) {
                            log.warn("Invalid end regex pattern at index " + i + ": " + endRegex + " - " + e.getMessage());
                        }
                    }
                }
                
                if (pattern.has("patterns")) {
                    cachePatternsRecursive(pattern.getJSONArray("patterns"));
                }
                
            } catch (JSONException e) {
                log.warn("Error parsing pattern at index " + i + ": " + e.getMessage());
            }
        }
    }
    
    public List<MatchResult> parseLine(String line) {
        List<MatchResult> allResults = new ArrayList<>();
        if (grammar == null) return allResults;
        
        try {
            JSONArray patterns = grammar.getJSONArray("patterns");
            parsePatterns(patterns, line, 0, allResults);

            return allResults;            
        } catch (Exception e) {
            log.error("Error parsing line: " + e.getMessage(), e);
        }
        
        return allResults;
    }
    
    private void parsePatterns(JSONArray patterns, String line, int startPos, List<MatchResult> results) {
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
                            results.add(new MatchResult(start, end - start, name, m.group()));
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
                                
                                results.add(new MatchResult(beginStart, endEnd - beginStart, name, 
                                    line.substring(beginStart, endEnd)));
                            }
                        }
                    }
                }
                
                if (pattern.has("patterns")) {
                    parsePatterns(pattern.getJSONArray("patterns"), line, startPos, results);
                }
                
            } catch (JSONException e) {
                log.warn("Error parsing pattern at index " + i + ": " + e.getMessage());
            }
        }
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
    
    public void testSyntaxHighlighting() {
        TextMateGrammar grammar = new TextMateGrammar();
        
        String[] testLines = {
            "2024-01-15 10:30:45 INFO - Application started successfully",
            "2024-01-15 10:30:46 DEBUG - Loading configuration from config.json",
            "2024-01-15 10:30:47 WARN - Deprecated API call detected",
            "2024-01-15 10:30:48 ERROR - Failed to connect to database",
            "2024-01-15 10:30:49 FATAL - Critical system failure",
            "Exception in thread \"main\" java.lang.NullPointerException",
            "at com.example.Main.main(Main.java:25)",
            "V - Verbose message",
            "D - Debug message", 
            "I - Info message",
            "W - Warning message",
            "E - Error message",
            "Trace: Detailed trace information",
            "[DEBUG] Debug message in brackets",
            "[INFO] Info message in brackets",
            "[WARN] Warning message in brackets",
            "[ERROR] Error message in brackets",
            "URL: https://example.com/path",
            "File path: /usr/local/bin/script.sh",
            "UUID: 123e4567-e89b-12d3-a456-426614174000",
            "Hash: a1b2c3d4e5f6789012345678901234567890abcd",
            "Number: 42",
            "Boolean: true",
            "String: \"Hello World\"",
            "Exception: java.lang.RuntimeException",
        };
        
        log.info("Testing syntax highlighting with " + testLines.length + " lines");
        
        for (String line : testLines) {
            List<MatchResult> matches = grammar.parseLine(line);
            log.info("Line: " + line);
            log.info("Matches: " + matches.size());
            for (MatchResult match : matches) {
                log.info("  - Scope: " + match.scope + ", Text: '" + match.text + "'");
                StyleInfo style = grammar.getStyleForScope(match.scope);
                if (style != null) {
                    log.info("    Style: fg=" + style.foreground + ", bg=" + style.background + ", font=" + style.fontStyle);
                }
            }
        }
        
        log.info("Syntax highlighting test completed successfully!");
    }
}

