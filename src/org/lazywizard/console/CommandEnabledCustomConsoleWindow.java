package org.lazywizard.console;

import data.scripts.CustomConsoleWindow;
import data.scripts.TextMateGrammar.StyleInfo;

import org.lazywizard.console.CustomConsoleListeners.ConsoleCampaignListener;
import org.lazywizard.console.CustomConsoleListeners.ConsoleCombatListener;
import org.apache.log4j.Logger;
import org.lazywizard.console.BaseCommand.CommandContext;

import com.fs.starfarer.api.Global;

import org.lwjgl.input.Keyboard;

import javax.swing.BorderFactory;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.*;

public class CommandEnabledCustomConsoleWindow extends CustomConsoleWindow {
    private List<String> inputHistory = new ArrayList<>();
    private int historyIndex = -1;
    private String currentInput = "";

    private static Logger log; 

    public CommandEnabledCustomConsoleWindow() {
        super();

        inputField = new JTextField();
        inputField.setFont(new Font("Consolas", Font.PLAIN, 14));

        inputField.setFocusTraversalKeysEnabled(false);
        inputField.addActionListener(e -> {
            String command = inputField.getText();
            if (!command.trim().isEmpty()) {
                
                addToHistory(command);
                
                switch (command.toLowerCase()) {
                    case "clear":
                        this.clearConsole();
                        break;
                    
                    case"printgrammar":
                        for (Map.Entry<String, StyleInfo> entry : getGrammar().scopeToStyle.entrySet()) {
                            log.info(entry.getKey() + " " + entry.getValue().foreground);
                        }
                        inputField.setText("");
                        return;

                    default:
                        break;
                }

                parseInput(command);
                inputField.setText("");
            }
        });

        // Add key listener for history navigation
        inputField.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
                // Handle tab key in keyTyped as well to ensure complete override
                if (e.getKeyChar() == '\t') {
                    e.consume();
                }
            }

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_UP) {
                    navigateHistoryUp();
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    navigateHistoryDown();
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_TAB) {
                    // Lazywizard's autocomplete logic repurposed for external console
                    
                    String inputText = inputField.getText();
                    
                    // If input is empty, just insert a tab character
                    if (inputText.trim().isEmpty()) {
                        StringBuilder sb = new StringBuilder(inputText);
                        int caretPos = inputField.getCaretPosition();

                        if (e.isShiftDown()) {
                            inputField.setCaretPosition(caretPos - 1);
                        } else {
                            sb.insert(caretPos, '\t');
                            inputField.setText(sb.toString());
                            inputField.setCaretPosition(caretPos + 1);
                        }

                        e.consume();
                        return;
                    }
                    
                    ConsoleSettings settings = Console.getSettings();
                    int currentIndex = inputText.length();

                    // Get just the current command (separator support complicates things)
                    int startIndex = inputText.lastIndexOf(settings.getCommandSeparator(), currentIndex) + 1;
                    int tmp = inputText.indexOf(settings.getCommandSeparator(), startIndex);
                    int endIndex = (tmp < 0) ? inputText.length() : tmp;
                    String toIndex = inputText.substring(startIndex, Math.max(startIndex, currentIndex));
                    String fullCommand = inputText.substring(startIndex, endIndex);

                    // Only auto-complete if arguments haven't been entered
                    if (fullCommand.indexOf(' ') != -1 || fullCommand.indexOf('\n') != -1) {
                        StringBuilder sb = new StringBuilder(inputText);
                        sb.insert(currentIndex, '\t');
                        inputField.setText(sb.toString());
                        inputField.setCaretPosition(currentIndex + 1);
                        e.consume();
                        return;
                    }

                    // Determine the current context
                    CommandContext context;
                    if (Global.getSettings().isInCampaignState()) {
                        context = new ConsoleCampaignListener().getContext();
                    } else {
                        ConsoleCombatListener listener = new ConsoleCombatListener();
                        listener.init(Global.getCombatEngine());
                        context = listener.getContext();
                    }

                    // Cycle through matching commands from current index forward
                    // If no further matches are found, start again from beginning
                    String firstMatch = null;
                    String nextMatch = null;
                    List<String> commands = CommandStore.getApplicableCommands(context);
                    Collections.sort(commands);

                    // Reverse order when shift is held down
                    boolean shiftDown = e.isShiftDown();
                    if (shiftDown) {
                        Collections.reverse(commands);
                    }

                    for (String command : commands) {
                        if (command.regionMatches(true, 0, toIndex, 0, toIndex.length())) {
                            // Used to cycle back to the beginning when no more matches are found
                            if (firstMatch == null) {
                                firstMatch = command;
                            }

                            // Found next matching command
                            if ((shiftDown && command.compareToIgnoreCase(fullCommand) < 0)
                                || (!shiftDown && command.compareToIgnoreCase(fullCommand) > 0)
                            ) {
                                nextMatch = command;
                                break;
                            }
                        }
                    }

                    if (nextMatch != null) {
                        StringBuilder sb = new StringBuilder(inputText);
                        sb.replace(startIndex, endIndex, nextMatch);
                        inputField.setText(sb.toString());
                        inputField.setCaretPosition(startIndex + nextMatch.length());
                    } else if (firstMatch != null) {
                        StringBuilder sb = new StringBuilder(inputText);
                        sb.replace(startIndex, endIndex, firstMatch);
                        inputField.setText(sb.toString());
                        inputField.setCaretPosition(startIndex + firstMatch.length());
                    }
                    e.consume();
                    return;
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {}
        });

        inputField.setBackground(new Color(30, 30, 30));
        inputField.setForeground(Color.WHITE);
        inputField.setCaretColor(Color.WHITE);
        inputField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(100, 100, 100)),
            BorderFactory.createEmptyBorder(2, 5, 2, 5)
        ));

        this.getContentPane().add(inputField, BorderLayout.SOUTH);

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED && e.isControlDown() && e.getKeyCode() == KeyEvent.VK_V) {
                if (!inputField.hasFocus()) {
                    inputField.requestFocus();
                    inputField.paste();
                    e.consume();
                    return false;
                }
            }
            if (e.getID() == KeyEvent.KEY_TYPED && !Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) {
                if (!inputField.hasFocus()) {
                    char ch = e.getKeyChar();

                    if (!Character.isISOControl(ch) && !this.getSearchDialog().isFocused()) {
                        inputField.requestFocus();
                        String currentText = inputField.getText();
                        int pos = inputField.getCaretPosition();

                        StringBuilder sb = new StringBuilder(currentText);
                        sb.insert(pos, ch);

                        inputField.setText(sb.toString());
                        inputField.setCaretPosition(pos + 1);
                        e.consume();
                    }
                }
            }
            return false;
        });

        this.revalidate();
        this.repaint();
    }

    private void addToHistory(String command) {
        if (command.trim().isEmpty()) {
            return;
        }
        
        if (!inputHistory.isEmpty() && inputHistory.get(inputHistory.size() - 1).equals(command)) {
            return;
        }
        
        inputHistory.add(command);
        if (inputHistory.size() > 100) {
            inputHistory.remove(0);
        }

        historyIndex = -1;
    }

    private void navigateHistoryUp() {
        if (inputHistory.isEmpty()) {
            return;
        }
        
        if (historyIndex == -1) {
            currentInput = inputField.getText();
        }
        
        if (historyIndex < inputHistory.size() - 1) {
            historyIndex++;
            inputField.setText(inputHistory.get(inputHistory.size() - 1 - historyIndex));
            inputField.setCaretPosition(inputField.getText().length());
        }
    }

    private void navigateHistoryDown() {
        if (inputHistory.isEmpty()) {
            return;
        }
        
        if (historyIndex > 0) {
            historyIndex--;
            inputField.setText(inputHistory.get(inputHistory.size() - 1 - historyIndex));
            inputField.setCaretPosition(inputField.getText().length());
        } else if (historyIndex == 0) {
            historyIndex = -1;
            inputField.setText(currentInput);
            inputField.setCaretPosition(inputField.getText().length());
        }
    }

    private void parseInput(String command) {
        if (Global.getSettings().isInCampaignState()) {
            Console.parseInput(command, new ConsoleCampaignListener().getContext());
        } else {
            ConsoleCombatListener listener = new ConsoleCombatListener();
            listener.init(Global.getCombatEngine());
            Console.parseInput(command, listener.getContext());
        }
        
    }
}
