package data.scripts;

import org.apache.log4j.Logger;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Toolkit;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class CustomConsoleWindow extends JFrame {
    private static final Logger log = Logger.getLogger(TextMateGrammar.class);
    
    private static TextMateGrammar grammar;
    
    static {
        try {
            grammar = new TextMateGrammar();
        } catch (Exception e) {
            log.error("Failed to initialize TextMateGrammar: " + e.getMessage(), e);
        }
    }

    private JTextPane textPane;
    private StyledDocument doc;
    private JDialog searchDialog;
    private JTextField searchField;
    private int currentMatchIndex = 0;
    private java.util.List<Integer> matchPositions = new java.util.ArrayList<>();
    private JLabel matchCounterLabel;
    private Style highlightStyle;
    private JCheckBox caseSensitiveCheckBox;

    private Style infoStyle, warnStyle, errorStyle, defaultStyle;

    private static class TextSegment {
        int start;
        int length;
        Style style;
        Map<Integer, Style> syntaxHighlights;
    
        TextSegment(int start, int length, Style style) {
            this.start = start;
            this.length = length;
            this.style = style;
            this.syntaxHighlights = new HashMap<>();
        }
    }
    
    private final java.util.List<TextSegment> segments = new java.util.ArrayList<>();
    private Map<Integer, Style> searchHighlights = new HashMap<>();

    public CustomConsoleWindow() {
        setTitle("Log Console");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setIconImage(Toolkit.getDefaultToolkit().getImage("graphics/ui/s_icon64.png"));

        Color darkBackground = new Color(43, 43, 43);
        Color scrollBarThumb = new Color(100, 100, 100);
        Color scrollBarTrack = new Color(60, 60, 60);

        getContentPane().setBackground(darkBackground);

        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setBackground(new Color(30, 30, 30));
        textPane.setFont(new Font("Monospaced", Font.PLAIN, 14));

        doc = textPane.getStyledDocument();
        defineStyles(doc);
        defineHighlightStyle();

        JScrollPane scrollPane = new JScrollPane(textPane);
        scrollPane.getViewport().setBackground(Color.BLACK);
        
        scrollPane.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                this.thumbColor = scrollBarThumb;
                this.trackColor = scrollBarTrack;
            }
            
            @Override
            protected JButton createDecreaseButton(int orientation) {
                return createZeroButton();
            }
            
            @Override
            protected JButton createIncreaseButton(int orientation) {
                return createZeroButton();
            }
            
            private JButton createZeroButton() {
                JButton button = new JButton();
                button.setPreferredSize(new java.awt.Dimension(0, 0));
                button.setMinimumSize(new java.awt.Dimension(0, 0));
                button.setMaximumSize(new java.awt.Dimension(0, 0));
                return button;
            }
        });
        
        scrollPane.getHorizontalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                this.thumbColor = scrollBarThumb;
                this.trackColor = scrollBarTrack;
            }
            
            @Override
            protected JButton createDecreaseButton(int orientation) {
                return createZeroButton();
            }
            
            @Override
            protected JButton createIncreaseButton(int orientation) {
                return createZeroButton();
            }
            
            private JButton createZeroButton() {
                JButton button = new JButton();
                button.setPreferredSize(new java.awt.Dimension(0, 0));
                button.setMinimumSize(new java.awt.Dimension(0, 0));
                button.setMaximumSize(new java.awt.Dimension(0, 0));
                return button;
            }
        });

        add(scrollPane);
        setupSearchDialog();
        setupKeyBindings();
    }

    public CustomConsoleWindow postInit() {
        setExtendedState(getExtendedState() | java.awt.Frame.MAXIMIZED_BOTH);
        setVisible(true);
        return this;
    }

    private void defineStyles(StyledDocument doc) {
        defaultStyle = doc.addStyle("Default", null);
        StyleConstants.setForeground(defaultStyle, Color.WHITE);

        infoStyle = doc.addStyle("Info", null);
        StyleConstants.setForeground(infoStyle, Color.LIGHT_GRAY);

        warnStyle = doc.addStyle("Warn", null);
        StyleConstants.setForeground(warnStyle, Color.YELLOW);

        errorStyle = doc.addStyle("Error", null);
        StyleConstants.setForeground(errorStyle, Color.RED);
    }

    private void defineHighlightStyle() {
        highlightStyle = doc.addStyle("Highlight", null);
        StyleConstants.setBackground(highlightStyle, new Color(255, 255, 0, 100));
        StyleConstants.setForeground(highlightStyle, Color.WHITE);
    }

    public CustomConsoleWindow getInstance() {
        return this;
    }

    public JDialog getSearchDialog() {
        return searchDialog;
    }

    public TextMateGrammar getGrammar() {
        return grammar;
    }

    public void appendText(String text) {
        SwingUtilities.invokeLater(() -> {
            try {
                JScrollBar verticalScrollBar = ((JScrollPane) textPane.getParent().getParent()).getVerticalScrollBar();
                boolean atBottom = verticalScrollBar.getValue() + verticalScrollBar.getVisibleAmount() >= verticalScrollBar.getMaximum() - 20;
    
                int start = doc.getLength();
                
                if (grammar != null) {
                    applySyntaxHighlighting(text, start);
                } else {
                    Style styleToUse = defaultStyle;
                    if (text.contains("ERROR")) {
                        styleToUse = errorStyle;
                    } else if (text.contains("WARN")) {
                        styleToUse = warnStyle;
                    } else if (text.contains("INFO")) {
                        styleToUse = infoStyle;
                    }
                    doc.insertString(start, text, styleToUse);
                    segments.add(new TextSegment(start, text.length(), styleToUse));
                }
    
                if (atBottom) {
                    textPane.setCaretPosition(doc.getLength());
                }
            } catch (BadLocationException e) {
                log.error(e);
            }
        });
    }
    
    public void clearConsole() {
        SwingUtilities.invokeLater(() -> {
            try {
                doc.remove(0, doc.getLength());
                segments.clear();
                searchHighlights.clear();
                matchPositions.clear();
                currentMatchIndex = 0;
                updateMatchCounter();
            } catch (BadLocationException e) {
                log.error(e);
            }
        });
    }
    
    private void applySyntaxHighlighting(String text, int startOffset) throws BadLocationException {
        doc.insertString(startOffset, text, defaultStyle);
        
        TextSegment segment = new TextSegment(startOffset, text.length(), defaultStyle);
        
        List<TextMateGrammar.MatchResult> matches = grammar.parseLine(text);
        
        matches.sort((a, b) -> Integer.compare(a.start, b.start));
        
        for (TextMateGrammar.MatchResult match : matches) {
            Style style = createStyleFromScope(match.scope);
            if (style != null) {
                int absoluteStart = startOffset + match.start;
                doc.setCharacterAttributes(absoluteStart, match.length, style, true);
                
                for (int i = 0; i < match.length; i++) {
                    segment.syntaxHighlights.put(absoluteStart + i, style);
                }
            }
        }
        
        segments.add(segment);
    }
    
    private Style createStyleFromScope(String scope) {
        if (grammar == null || scope == null || scope.isEmpty()) {
            return defaultStyle;
        }
        
        TextMateGrammar.StyleInfo styleInfo = grammar.getStyleForScope(scope);
        if (styleInfo == null) {
            String[] scopeParts = scope.split(",\\s*");
            
            for (String scopePart : scopeParts) {
                scopePart = scopePart.trim();
                
                styleInfo = grammar.getStyleForScope(scopePart);
                if (styleInfo != null) {
                    break;
                }
                
                for (Map.Entry<String, TextMateGrammar.StyleInfo> entry : 
                     grammar.getScopeToStyle().entrySet()) {
                    if (scopePart.contains(entry.getKey()) || entry.getKey().contains(scopePart)) {
                        styleInfo = entry.getValue();
                        break;
                    }
                }
                
                if (styleInfo != null) {
                    break;
                }
            }
        }
        
        if (styleInfo == null) {
            return defaultStyle;
        }
        
        Style style = doc.addStyle("Syntax_" + scope.hashCode(), null);
        
        if (styleInfo.foreground != null) {
            java.awt.Color color = TextMateGrammar.hexToColor(styleInfo.foreground);
            if (color != null) {
                StyleConstants.setForeground(style, color);
            }
        }
        
        if (styleInfo.background != null) {
            java.awt.Color color = TextMateGrammar.hexToColor(styleInfo.background);
            if (color != null) {
                StyleConstants.setBackground(style, color);
            }
        }

        if (scope.contains("strong")) {
            StyleConstants.setBold(style, true);
        }
        
        if (scope.contains("emphasis")) {
            StyleConstants.setItalic(style, true);
        }
        
        if (styleInfo.fontStyle != null) {
            if (styleInfo.fontStyle.contains("bold")) {
                StyleConstants.setBold(style, true);
            }
            if (styleInfo.fontStyle.contains("italic")) {
                StyleConstants.setItalic(style, true);
            }
            if (styleInfo.fontStyle.contains("underline")) {
                StyleConstants.setUnderline(style, true);
            }
        }
        
        return style;
    }

    private void setupSearchDialog() {
        searchDialog = new JDialog(this, "Search", false);
        searchDialog.setIconImage(Toolkit.getDefaultToolkit().getImage("graphics/ui/s_icon64.png"));
        searchDialog.setLayout(new BorderLayout());
        
        Color darkBackground = new Color(43, 43, 43);
        Color darkForeground = new Color(200, 200, 200);
        Color buttonBackground = new Color(60, 60, 60);
        Color buttonHover = new Color(80, 80, 80);
        
        searchDialog.getContentPane().setBackground(darkBackground);
        
        searchField = new JTextField(20);
        searchField.setBackground(darkBackground);
        searchField.setForeground(darkForeground);
        searchField.setCaretColor(darkForeground);
        searchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(100, 100, 100)),
            BorderFactory.createEmptyBorder(2, 5, 2, 5)
        ));
        
        JButton findButton = createStyledButton("Find", buttonBackground, darkForeground, buttonHover);
        JButton closeButton = createStyledButton("Close", buttonBackground, darkForeground, buttonHover);
        JButton prevButton = createStyledButton("↑", buttonBackground, darkForeground, buttonHover);
        JButton nextButton = createStyledButton("↓", buttonBackground, darkForeground, buttonHover);
        
        matchCounterLabel = new JLabel("0/0");
        matchCounterLabel.setForeground(darkForeground);

        caseSensitiveCheckBox = new JCheckBox("Case Sensitive");
        caseSensitiveCheckBox.setBackground(darkBackground);
        caseSensitiveCheckBox.setForeground(darkForeground);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(darkBackground);
        topPanel.add(searchField, BorderLayout.CENTER);
        topPanel.add(caseSensitiveCheckBox, BorderLayout.EAST);
        searchDialog.add(topPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(darkBackground);
        buttonPanel.add(prevButton);
        buttonPanel.add(nextButton);
        buttonPanel.add(findButton);
        buttonPanel.add(closeButton);
        buttonPanel.add(matchCounterLabel);
        
        searchDialog.add(buttonPanel, BorderLayout.SOUTH);
        
        findButton.addActionListener(e -> findNext());
        closeButton.addActionListener(e -> {
            clearHighlights();
            searchDialog.setVisible(false);
        });
        prevButton.addActionListener(e -> navigateToPreviousMatch());
        nextButton.addActionListener(e -> navigateToNextMatch());
        
        searchField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
                    findNext();
                }
            }
        });
        
        searchDialog.pack();
        searchDialog.setMinimumSize(new java.awt.Dimension(400, searchDialog.getPreferredSize().height));
        searchDialog.setLocationRelativeTo(this);
    }

    private JButton createStyledButton(String text, Color background, Color foreground, Color hoverColor) {
        JButton button = new JButton(text);
        button.setBackground(background);
        button.setForeground(foreground);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(true);
        
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) {
                button.setBackground(hoverColor);
            }
            public void mouseExited(java.awt.event.MouseEvent e) {
                button.setBackground(background);
            }
        });
        
        return button;
    }

    private void setupKeyBindings() {
        KeyStroke ctrlF = KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK);
        textPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ctrlF, "search");
        textPane.getActionMap().put("search", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchDialog.setVisible(true);
                searchField.requestFocus();
            }
        });
    }

    private void clearHighlights() {
        try {
            for (Map.Entry<Integer, Style> entry : searchHighlights.entrySet()) {
                int position = entry.getKey();

                Style originalStyle = findOriginalStyle(position);
                if (originalStyle != null) {
                    doc.setCharacterAttributes(position, 1, originalStyle, true);
                }
            }
            searchHighlights.clear();
            matchPositions.clear();
            currentMatchIndex = 0;
            updateMatchCounter();
        } catch (Exception e) {
            log.error(e);
        }
    }
    
    private Style findOriginalStyle(int position) {
        for (TextSegment segment : segments) {
            if (position >= segment.start && position < segment.start + segment.length) {

                Style syntaxStyle = segment.syntaxHighlights.get(position);
                if (syntaxStyle != null) {
                    return syntaxStyle;
                }

                return segment.style;
            }
        }
        return defaultStyle;
    }

    private void highlightAllMatches(String searchText) {
        clearHighlights();
        matchPositions.clear();
        currentMatchIndex = 0;
    
        try {
            String content = doc.getText(0, doc.getLength());
            String search = searchText;
    
            if (!caseSensitiveCheckBox.isSelected()) {
                content = content.toLowerCase();
                search = search.toLowerCase();
            }
    
            int index = 0;
            while ((index = content.indexOf(search, index)) != -1) {
                matchPositions.add(index);
                

                for (int i = 0; i < searchText.length(); i++) {
                    int position = index + i;
                    Style originalStyle = findOriginalStyle(position);
                    
                    Style combinedStyle = createCombinedStyle(originalStyle, highlightStyle);
                    doc.setCharacterAttributes(position, 1, combinedStyle, true);
                    searchHighlights.put(position, combinedStyle);
                }
                
                index += searchText.length();
            }
        } catch (BadLocationException e) {
            log.error(e);
        }
    
        updateMatchCounter();
    }
    
    private Style createCombinedStyle(Style baseStyle, Style overlayStyle) {
        Style combinedStyle = doc.addStyle("Combined_" + System.currentTimeMillis(), baseStyle);
        
        StyleConstants.setBackground(combinedStyle, StyleConstants.getBackground(overlayStyle));
        
        if (StyleConstants.getForeground(baseStyle) != null) {
            StyleConstants.setForeground(combinedStyle, StyleConstants.getForeground(baseStyle));
        }
        
        StyleConstants.setBold(combinedStyle, StyleConstants.isBold(baseStyle));
        StyleConstants.setItalic(combinedStyle, StyleConstants.isItalic(baseStyle));
        StyleConstants.setUnderline(combinedStyle, StyleConstants.isUnderline(baseStyle));
        
        return combinedStyle;
    }

    private void updateMatchCounter() {
        if (matchPositions.isEmpty()) {
            matchCounterLabel.setText("0/0");
        } else {
            matchCounterLabel.setText((currentMatchIndex + 1) + "/" + matchPositions.size());
        }
    }

    private void navigateToNextMatch() {
        if (matchPositions.isEmpty()) return;
        
        currentMatchIndex = (currentMatchIndex + 1) % matchPositions.size();
        int position = matchPositions.get(currentMatchIndex);
        textPane.setCaretPosition(position);
        textPane.setSelectionStart(position);
        textPane.setSelectionEnd(position + searchField.getText().length());
        updateMatchCounter();
    }

    private void navigateToPreviousMatch() {
        if (matchPositions.isEmpty()) return;
        
        currentMatchIndex = (currentMatchIndex - 1 + matchPositions.size()) % matchPositions.size();
        int position = matchPositions.get(currentMatchIndex);
        textPane.setCaretPosition(position);
        textPane.setSelectionStart(position);
        textPane.setSelectionEnd(position + searchField.getText().length());
        updateMatchCounter();
    }

    private void showDarkThemedMessage(String message, String title) {
        JPanel panel = new JPanel();
        panel.setBackground(new Color(43, 43, 43));
        JLabel label = new JLabel(message);
        label.setForeground(new Color(200, 200, 200));
        panel.add(label);
    
        JOptionPane optionPane = new JOptionPane(panel, JOptionPane.INFORMATION_MESSAGE, JOptionPane.DEFAULT_OPTION);
        JDialog dialog = optionPane.createDialog(this, title);
        dialog.setIconImage(Toolkit.getDefaultToolkit().getImage("graphics/ui/s_icon64.png"));
    
        applyDarkTheme(dialog.getContentPane());
        applyDarkTheme(dialog.getRootPane());
    
        dialog.setVisible(true);
    }
    
    private void applyDarkTheme(Container container) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof JPanel || comp instanceof JComponent) {
                comp.setBackground(new Color(43, 43, 43));
                comp.setForeground(new Color(200, 200, 200));
                if (comp instanceof Container) {
                    applyDarkTheme((Container) comp);
                }
            }
        }
    }

    private String lastSearchText = "";
    private void findNext() {
        String searchText = searchField.getText();
        if (searchText.isEmpty()) return;
    
        boolean sameSearch = searchText.equals(lastSearchText) && !matchPositions.isEmpty();
    
        if (sameSearch) {
            navigateToNextMatch();
        } else {
            lastSearchText = searchText;
            highlightAllMatches(searchText);
    
            if (matchPositions.isEmpty()) {
                showDarkThemedMessage("Text not found", "Search Result");
                return;
            }
    
            currentMatchIndex = 0;
            int position = matchPositions.get(currentMatchIndex);
            textPane.setCaretPosition(position);
            textPane.setSelectionStart(position);
            textPane.setSelectionEnd(position + searchText.length());
            updateMatchCounter();
        }
    }
}