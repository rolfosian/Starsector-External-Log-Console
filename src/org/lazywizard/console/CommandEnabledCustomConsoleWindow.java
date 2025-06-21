package org.lazywizard.console;

import data.scripts.CustomConsoleWindow;

import org.lazywizard.console.CustomConsoleListeners.ConsoleCampaignListener;
import org.lazywizard.console.CustomConsoleListeners.ConsoleCombatListener;

import com.fs.starfarer.api.Global;

import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;

public class CommandEnabledCustomConsoleWindow extends CustomConsoleWindow {
    private JTextField inputField;

    public CommandEnabledCustomConsoleWindow() {
        super();

        inputField = new JTextField();
        inputField.setFont(new Font("Monospaced", Font.PLAIN, 14));
        inputField.addActionListener(e -> {
            String command = inputField.getText();
            if (!command.trim().isEmpty()) {
                parseInput(command);
                inputField.setText("");
            }
        });

        inputField.setBackground(new Color(30, 30, 30));
        inputField.setForeground(Color.WHITE);
        inputField.setCaretColor(Color.WHITE);

        this.getContentPane().add(inputField, BorderLayout.SOUTH);

        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == java.awt.event.KeyEvent.KEY_TYPED) {
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
