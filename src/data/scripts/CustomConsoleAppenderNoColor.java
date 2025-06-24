package data.scripts;

import org.apache.log4j.spi.LoggingEvent;

public class CustomConsoleAppenderNoColor extends CustomConsoleAppender {

    public CustomConsoleAppenderNoColor(CustomConsoleWindow windowInstance) {
        super(windowInstance);
    }

    @Override
    public void append(LoggingEvent event) {
        appendExecutor.submit(() -> {
            windowInstance.appendTextNoHighlight(event);
        });
    }
}
