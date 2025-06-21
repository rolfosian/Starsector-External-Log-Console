package data.scripts;

import org.lazywizard.console.CommandEnabledCustomConsoleWindow;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

public class ExternalLogConsolePlugin extends BaseModPlugin {
    @Override
    public void onApplicationLoad() {
        if (Global.getSettings().getModManager().isModEnabled("lw_console")) {
            new CustomConsoleAppender(new CommandEnabledCustomConsoleWindow().preInit());
        } else {
            new CustomConsoleAppender(new CustomConsoleWindow().preInit());
        }
    }
}
