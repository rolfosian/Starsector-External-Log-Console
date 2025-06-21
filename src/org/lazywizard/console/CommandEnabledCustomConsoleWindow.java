package org.lazywizard.console;

import data.scripts.CustomConsoleWindow;
import data.scripts.TextMateGrammar.StyleInfo;

import org.apache.log4j.PatternLayout;
import org.lazywizard.console.CustomConsoleListeners.ConsoleCampaignListener;
import org.lazywizard.console.CustomConsoleListeners.ConsoleCombatListener;

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

    public CommandEnabledCustomConsoleWindow() {
        super();

        inputField = new JTextField();
        inputField.setFont(new Font("Consolas", Font.PLAIN, 14));
        inputField.addActionListener(e -> {
            String command = inputField.getText();
            if (!command.trim().isEmpty()) {
                
                // Add command to history
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
            public void keyTyped(KeyEvent e) {}

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_UP) {
                    navigateHistoryUp();
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    navigateHistoryDown();
                    e.consume();
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
