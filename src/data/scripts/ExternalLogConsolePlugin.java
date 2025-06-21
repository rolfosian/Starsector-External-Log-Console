package data.scripts;

import org.lazywizard.console.CommandEnabledCustomConsoleWindow;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

public class ExternalLogConsolePlugin extends BaseModPlugin {
    @Override
    public void onApplicationLoad() {
        CustomConsoleAppender consoleAppender;

        if (Global.getSettings().getModManager().isModEnabled("lw_console")) {
            consoleAppender = new CustomConsoleAppender(new CommandEnabledCustomConsoleWindow().postInit());
        } else {
            consoleAppender = new CustomConsoleAppender(new CustomConsoleWindow().postInit());
        }

        consoleAppender.setLayout(new PatternLayout("%r [%t] %-5p %c - %m%n"));
        Logger.getRootLogger().addAppender(consoleAppender);;
    }
}
