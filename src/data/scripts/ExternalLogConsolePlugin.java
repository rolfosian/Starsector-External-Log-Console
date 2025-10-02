package data.scripts;

import org.lazywizard.console.CommandEnabledCustomConsoleWindow;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

import javax.swing.SwingUtilities;

public class ExternalLogConsolePlugin extends BaseModPlugin {
    private static CustomConsoleWindow currentWindow = null;
    private static CustomConsoleAppender currentAppender = null;
    
    public static CustomConsoleWindow getCurrentWindow() {
        return currentWindow;
    }
    
    public static boolean isWindowVisible() {
        return currentWindow != null && currentWindow.isVisible();
    }
    
    public static void showWindow() {
        if (currentWindow != null && !currentWindow.isVisible()) {
            SwingUtilities.invokeLater(() -> {
                try {
                    currentWindow.setVisible(true);
                    currentWindow.toFront();
                } catch (Exception e) {
                    Global.getLogger(ExternalLogConsolePlugin.class).error("Error showing window", e);
                }
            });
        } else if (currentWindow == null) {
            createWindow();
        }
    }
    
    private static void createWindow() {
        SwingUtilities.invokeLater(() -> {
            try {
                if (currentWindow != null && currentWindow.isVisible()) {
                    return;
                }
                
                if (currentAppender != null) {
                    try {
                        org.apache.log4j.Logger.getRootLogger().removeAppender(currentAppender);
                    } catch (Exception e) {
                        // Ignore cleanup errors
                    }
                }
                
                if (currentWindow != null) {
                    try {
                        currentWindow.dispose();
                    } catch (Exception e) {
                    }
                }
                
                try {
                    if (Global.getSettings().getModManager().isModEnabled("lw_console")) {
                        if (Global.getSettings().getBoolean("externalconsolewindowcolorize")) {
                            currentWindow = new CommandEnabledCustomConsoleWindow();
                            currentAppender = new CustomConsoleAppender(currentWindow.preInit());
                        } else {
                            currentWindow = new CommandEnabledCustomConsoleWindow();
                            currentAppender = new CustomConsoleAppenderNoColor(currentWindow.preInit());
                        }
                    } else {
                        if (Global.getSettings().getBoolean("externalconsolewindowcolorize")) {
                            currentWindow = new CustomConsoleWindow();
                            currentAppender = new CustomConsoleAppender(currentWindow.preInit());
                        } else {
                            currentWindow = new CustomConsoleWindow();
                            currentAppender = new CustomConsoleAppenderNoColor(currentWindow.preInit());
                        }
                    }
                    
                    if (currentWindow != null && !currentWindow.isVisible()) {
                        currentWindow.setVisible(true);
                    }
                    
                } catch (Exception e) {
                    Global.getLogger(ExternalLogConsolePlugin.class).error("Failed to create console window", e);
                    currentWindow = null;
                    currentAppender = null;
                }
                
            } catch (Exception e) {
                Global.getLogger(ExternalLogConsolePlugin.class).error("Error in window creation", e);
            }
        });
    }
    
    @Override
    public void onApplicationLoad() {
        createWindow();
    }
}
