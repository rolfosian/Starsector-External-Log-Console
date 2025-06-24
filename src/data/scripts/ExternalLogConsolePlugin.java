package data.scripts;

import org.lazywizard.console.CommandEnabledCustomConsoleWindow;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

public class ExternalLogConsolePlugin extends BaseModPlugin {
    @Override
    public void onApplicationLoad() {


        if (Global.getSettings().getModManager().isModEnabled("lw_console")) {
            if (Global.getSettings().getBoolean("externalconsolewindowcolorize")) {
                new CustomConsoleAppender(new CommandEnabledCustomConsoleWindow().preInit());
            } else {
                new CustomConsoleAppenderNoColor(new CommandEnabledCustomConsoleWindow().preInit());
            }
            
        } else {
            if (Global.getSettings().getBoolean("externalconsolewindowcolorize")) {
                new CustomConsoleAppender(new CustomConsoleWindow().preInit());
            } else {
                new CustomConsoleAppenderNoColor(new CustomConsoleWindow().preInit());
            }
        }
    }
}
