package data.scripts;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;

public class CustomConsoleAppender extends AppenderSkeleton {
    private final CustomConsoleWindow windowInstance;

    public CustomConsoleAppender(CustomConsoleWindow windowInstance) {
        this.windowInstance = windowInstance;
    }

    @Override
    public void append(LoggingEvent event) {
        String message = this.layout.format(event);
        windowInstance.appendText(message);
    }

    @Override
    public void close() {}

    @Override
    public boolean requiresLayout() {
        return true;
    }
}