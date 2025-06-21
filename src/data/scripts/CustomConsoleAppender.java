package data.scripts;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;

public class CustomConsoleAppender extends AppenderSkeleton {
    private final CustomConsoleWindow windowInstance;

    public CustomConsoleAppender(CustomConsoleWindow windowInstance) {
        this.windowInstance = windowInstance;
        this.windowInstance.init(this);
    }

    @Override
    public void append(LoggingEvent event) {
        windowInstance.appendText(event);
    }

    @Override
    public void close() {}

    @Override
    public boolean requiresLayout() {
        return true;
    }
}