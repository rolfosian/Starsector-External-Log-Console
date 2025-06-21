package org.lazywizard.console;

import data.scripts.CustomConsoleWindow;

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

public class CommandEnabledCustomConsoleWindow extends CustomConsoleWindow {
    // private JTextField inputField;

    public CommandEnabledCustomConsoleWindow() {
        super();

        inputField = new JTextField();
        inputField.setFont(new Font("Consolas", Font.PLAIN, 14));
        inputField.addActionListener(e -> {
            String command = inputField.getText();
            if (!command.trim().isEmpty()) {
                if (command.toLowerCase().equals("clear")) {
                    this.clearConsole();
                }

                parseInput(command);
                inputField.setText("");
            }
        });

        inputField.setBackground(new Color(30, 30, 30));
        inputField.setForeground(Color.WHITE);
        inputField.setCaretColor(Color.WHITE);
        inputField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0, 0, 0)),
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
