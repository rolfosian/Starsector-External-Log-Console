package data.scripts;

import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

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
import javax.swing.text.Document;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Set;
import org.apache.log4j.spi.LoggingEvent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CustomConsoleWindow extends JFrame {
    public static final Logger log = Logger.getLogger(TextMateGrammar.class);
    
    private static TextMateGrammar grammar;
    
    static {
        try {
            grammar = new TextMateGrammar();
        } catch (Exception e) {
            log.error("Failed to initialize TextMateGrammar: " + e.getMessage(), e);
        }
    }

    private JTextPane textPane;
    protected JTextField inputField = null;
    private StyledDocument doc;
    private JDialog searchDialog;
    private JTextField searchField;
    private int currentMatchIndex = 0;
    private java.util.List<Integer> matchPositions = new java.util.ArrayList<>();
    private JLabel matchCounterLabel;
    private Style highlightStyle;
    private JCheckBox caseSensitiveCheckBox;

    private Style infoStyle, warnStyle, errorStyle, defaultStyle;

    private ExecutorService syntaxHighlightExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService statusExecutor = Executors.newSingleThreadExecutor();

    public static class TextSegment {
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

    private final int MAX_LOG_ENTRIES = 33000;
    private final int MAX_LINES = 33000;
    private List<LoggingEvent> logEvents = new ArrayList<>();

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
        textPane.setFont(new Font("Consolas", Font.PLAIN, 14));

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

    public CustomConsoleWindow preInit() {
        setExtendedState(getExtendedState() | java.awt.Frame.MAXIMIZED_BOTH);
        setVisible(true);
        setupRightClickMenus();
        return this;
    }

    public void init(CustomConsoleAppender appender) {
        this.appender = appender;
        setPatternLayout();
        Logger.getRootLogger().addAppender(appender);
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

    public StyledDocument getDoc() {
        return doc;
    }

    public void appendText(String text) {
        try {
            Document doc = textPane.getDocument();
            JScrollBar verticalScrollBar = ((JScrollPane) textPane.getParent().getParent()).getVerticalScrollBar();
            boolean atBottom = verticalScrollBar.getValue() + verticalScrollBar.getVisibleAmount() >= verticalScrollBar.getMaximum() - 20;

            int lineCount = getLineCount();
            if (lineCount >= MAX_LINES) {
                removeExcessLines(lineCount - MAX_LINES + 1);
            }
    
            int start = doc.getLength();
            doc.insertString(start, text, defaultStyle);
            
            TextSegment segment = new TextSegment(start, text.length(), defaultStyle);
            segments.add(segment);

            if (atBottom) {
                textPane.setCaretPosition(doc.getLength());
            }
            applySyntaxHighlightingAsync(text, start, segment);

        } catch (BadLocationException e) {
            log.error(e);
        }
    }

    public void appendText(LoggingEvent event) {
        if (logEvents.size() >= MAX_LOG_ENTRIES) {
            logEvents.remove(0);
        }
        logEvents.add(event);

        String message = this.appender.getLayout().format(event);

        appendText(message);

        if (event.getThrowableInformation() != null) {
            String stackTrace = "";
            for (String str : event.getThrowableInformation().getThrowableStrRep()) {
                stackTrace += str + "\n";
            }
            appendText(stackTrace);
        }
    }

    private void applySyntaxHighlightingAsync(String text, int startOffset, TextSegment segment) {
        syntaxHighlightExecutor.submit(() -> {
            grammar.parseLine(text, this, segment, startOffset);
        });
    }
    
    protected Style createStyleFromScope(String scope) {
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

        searchDialog.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        searchDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                clearHighlights();
                searchDialog.setVisible(false);
            }
        });
        searchDialog.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                int keyCode = e.getKeyCode();

                switch(keyCode) {
                    case KeyEvent.VK_ESCAPE:
                        clearHighlights();
                        searchDialog.setVisible(false);
                        e.consume();
                        return;
                    
                    default:
                        return;
                }
            }
        });
        
        
        searchField.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                int keyCode = e.getKeyCode();

                switch(keyCode) {
                    case KeyEvent.VK_ENTER:
                        findNext();
                        return;

                    case KeyEvent.VK_ESCAPE:
                        clearHighlights();
                        searchDialog.setVisible(false);
                        e.consume();
                        return;

                    default:
                        return;
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

    private CustomConsoleAppender appender;
    private String currentPatternLayoutString = "%r [%t] %-5p %c - %m%n";

    private String[] layoutParamOrder = new String[] {
        "%r",
        "[%t]",
        "%-5p",
        "%c"
    };

    private Set<String> activeLayoutParams = new HashSet<>(Arrays.asList(layoutParamOrder));

    private void rebuildPatternLayoutString() {
        StringBuilder sb = new StringBuilder();
        for (String param : layoutParamOrder) {
            if (activeLayoutParams.contains(param)) {
                sb.append(param).append(" ");
            }
        }
        sb.append("- %m%n%");
        currentPatternLayoutString = sb.toString();
    }

    private void insertLayoutParam(String param) {
        activeLayoutParams.add(param);
        rebuildPatternLayoutString();
    }

    private void removeLayoutParam(String param) {
        activeLayoutParams.remove(param);
        rebuildPatternLayoutString();
    }

    public void setPatternLayout() {
        this.appender.setLayout(new PatternLayout(currentPatternLayoutString));
    }

    private void rerenderLogMessages() {
        syntaxHighlightExecutor.submit(() -> {
            textPane.setText("");
            segments.clear();

            for (int i = logEvents.size() - 1; i >= 0; i--) {
                LoggingEvent event = logEvents.get(i);
                String message = this.appender.getLayout().format(event);
                appendText(message);
            }
        });
    }

    private void setupRightClickMenus() {
        JPopupMenu textPanePopupMenu = new JPopupMenu();
        textPanePopupMenu.setBackground(new Color(30, 30, 30));
        textPanePopupMenu.setForeground(Color.WHITE);
        textPanePopupMenu.setFont(new Font("Consolas", Font.PLAIN, 14));

        JMenuItem copyItem = new JMenuItem("Copy");
        copyItem.addActionListener(e -> textPane.copy());
        copyItem.setBackground(new Color(30, 30, 30));
        copyItem.setForeground(Color.WHITE);
        copyItem.setFont(new Font("Consolas", Font.PLAIN, 14));
        textPanePopupMenu.add(copyItem);

        if (inputField != null) {
            JMenuItem pasteItem = new JMenuItem("Paste");
            pasteItem.addActionListener(e -> {
                inputField.paste();
                inputField.requestFocus();
            });

            pasteItem.setBackground(new Color(30, 30, 30));
            pasteItem.setForeground(Color.WHITE);
            pasteItem.setFont(new Font("Consolas", Font.PLAIN, 14));
            textPanePopupMenu.add(pasteItem);

            JPopupMenu inputFieldPopupMenu = new JPopupMenu();
            inputFieldPopupMenu.setBackground(new Color(30, 30, 30));
            inputFieldPopupMenu.setForeground(Color.WHITE);
            inputFieldPopupMenu.setFont(new Font("Consolas", Font.PLAIN, 14));

            AbstractButton[] items = new AbstractButton[2];

            JMenuItem inputCopyItem = new JMenuItem("Copy");
            inputCopyItem.addActionListener(e -> textPane.copy());
            items[0] = inputCopyItem;

            JMenuItem inputPasteItem = new JMenuItem("Paste");
            inputPasteItem.addActionListener(e -> {
                inputField.paste();
                inputField.requestFocus();
            });
            items[1] = inputPasteItem;

            for (AbstractButton item : items) {
                item.setBackground(new Color(30, 30, 30));
                item.setForeground(Color.WHITE);
                item.setFont(new Font("Consolas", Font.PLAIN, 14));
                inputFieldPopupMenu.add(item);
            }

            inputField.addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    if (e.isPopupTrigger())
                    inputFieldPopupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
    
                public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger())
                    inputFieldPopupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            });
        }

        JMenuItem clearItem = new JMenuItem("Clear");
        clearItem.addActionListener(e -> this.clearConsole());
        clearItem.setBackground(new Color(30, 30, 30));
        clearItem.setForeground(Color.WHITE);
        clearItem.setFont(new Font("Consolas", Font.PLAIN, 14));
        textPanePopupMenu.add(clearItem);

        textPanePopupMenu.addSeparator();

        AbstractButton[] items = new AbstractButton[4];
        
        JCheckBoxMenuItem showlogTimeToggle = new JCheckBoxMenuItem("Show log time");
        showlogTimeToggle.setSelected(true);
        showlogTimeToggle.addActionListener(e -> {
            boolean on = showlogTimeToggle.isSelected();

            if (on) {
                insertLayoutParam("%r");
            } else {
                removeLayoutParam("%r");
            }
            setPatternLayout();
            rerenderLogMessages();
        });
        items[0] = showlogTimeToggle;

        JCheckBoxMenuItem showlogThreadToggle = new JCheckBoxMenuItem("Show log thread");
        showlogThreadToggle.setSelected(true);
        showlogThreadToggle.addActionListener(e -> {
            boolean on = showlogThreadToggle.isSelected();

            if (on) {
                insertLayoutParam("[%t]");
            } else {
                removeLayoutParam("[%t]");
            }
            setPatternLayout();
            rerenderLogMessages();
        });
        items[1] = showlogThreadToggle;

        JCheckBoxMenuItem showLogLevelToggle = new JCheckBoxMenuItem("Show log level");
        showLogLevelToggle.setSelected(true);
        showLogLevelToggle.addActionListener(e -> {
            boolean on = showLogLevelToggle.isSelected();

            if (on) {
                insertLayoutParam("%-5p");
            } else {
                removeLayoutParam("%-5p");
            }
            setPatternLayout();
            rerenderLogMessages();
        });
        items[2] = showLogLevelToggle;

        JCheckBoxMenuItem showCategoryToggle = new JCheckBoxMenuItem("Show log category (class)");
        showCategoryToggle.setSelected(true);
        showCategoryToggle.addActionListener(e -> {
            boolean on = showCategoryToggle.isSelected();

            if (on) {
                insertLayoutParam("%c");
            } else {
                removeLayoutParam("%c");
            }
            setPatternLayout();
            rerenderLogMessages();
        });
        items[3] = showCategoryToggle;

        for (AbstractButton item : items) {
            item.setBackground(new Color(30, 30, 30));
            item.setForeground(Color.WHITE);
            item.setFont(new Font("Consolas", Font.PLAIN, 14));
            textPanePopupMenu.add(item);
        }

        textPane.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger())
                textPanePopupMenu.show(e.getComponent(), e.getX(), e.getY());
            }

            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger())
                textPanePopupMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        });
    }

    private void setupKeyBindings() {
        KeyStroke ctrlF = KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK);
        textPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ctrlF, "search");
        textPane.getActionMap().put("search", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchDialog.setVisible(true);
                searchField.requestFocus();
                if (!searchField.getText().isEmpty()) {
                    highlightAllMatches(searchField.getText());
                }
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
        boolean isFocused = false;
        if (!textPane.isFocusOwner()) {
            isFocused = true;
            textPane.requestFocus(true);
        }
        
        currentMatchIndex = (currentMatchIndex + 1) % matchPositions.size();
        int position = matchPositions.get(currentMatchIndex);
        textPane.setCaretPosition(position);
        textPane.setSelectionStart(position);
        textPane.setSelectionEnd(position + searchField.getText().length());
        updateMatchCounter();
        if (isFocused) {
            searchField.requestFocus();
        }
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

        return;
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
                searchField.requestFocus();
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

    public void clearConsole() {
        SwingUtilities.invokeLater(() -> {
            try {
                doc.remove(0, doc.getLength());
                segments.clear();
                searchHighlights.clear();
                matchPositions.clear();
                currentMatchIndex = 0;
                logEvents.clear();
                updateMatchCounter();
            } catch (BadLocationException e) {
                log.error(e);
            }
        });
    }

    public void dispose() {
        if (syntaxHighlightExecutor != null && !syntaxHighlightExecutor.isShutdown()) {
            syntaxHighlightExecutor.shutdown();
        }
        if (statusExecutor != null && !statusExecutor.isShutdown()) {
            statusExecutor.shutdown();
        }
        super.dispose();
    }

    private int getLineCount() {
        try {
            String text = doc.getText(0, doc.getLength());
            return text.split("\n", -1).length;
        } catch (BadLocationException e) {
            return 0;
        }
    }
    
    private void removeExcessLines(int linesToRemove) {
        try {
            String text = doc.getText(0, doc.getLength());
            String[] lines = text.split("\n", -1);
            
            if (lines.length <= linesToRemove) {
                doc.remove(0, doc.getLength());
                segments.clear();
                return;
            }
            
            final int startPosition = calculateStartPosition(lines, linesToRemove);
            
            doc.remove(0, startPosition);
            
            segments.removeIf(segment -> segment.start + segment.length <= startPosition);
            
            for (TextSegment segment : segments) {
                segment.start -= startPosition;
            }
            
            Map<Integer, Style> newSearchHighlights = new HashMap<>();
            for (Map.Entry<Integer, Style> entry : searchHighlights.entrySet()) {
                int position = entry.getKey();
                if (position >= startPosition) {
                    newSearchHighlights.put(position - startPosition, entry.getValue());
                }
            }
            searchHighlights = newSearchHighlights;
            
            for (int i = 0; i < matchPositions.size(); i++) {
                int position = matchPositions.get(i);
                if (position >= startPosition) {
                    matchPositions.set(i, position - startPosition);
                } else {
                    matchPositions.remove(i);
                    i--;
                }
            }
            
        } catch (Exception e) {
            log.error("Error removing excess lines: " + e.getMessage(), e);
        }
    }
    
    private int calculateStartPosition(String[] lines, int linesToRemove) {
        int startPosition = 0;
        int lineCount = 0;
        for (int i = 0; i < lines.length && lineCount < linesToRemove; i++) {
            startPosition += lines[i].length() + 1; // +1 for the newline
            lineCount++;
        }
        return startPosition;
    }
}