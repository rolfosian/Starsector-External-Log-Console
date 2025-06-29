package data.scripts;

import org.apache.log4j.spi.LoggingEvent;

public class CustomConsoleAppenderNoColor extends CustomConsoleAppender {

    public CustomConsoleAppenderNoColor(CustomConsoleWindow windowInstance) {
        super(windowInstance);
    }

    @Override
    public void append(LoggingEvent event) {
        if (event.getMessage().toString().startsWith("Cleaned buffer for texture")) return;
        appendExecutor.submit(() -> {
            windowInstance.appendTextNoHighlight(event);
        });
    }
}
