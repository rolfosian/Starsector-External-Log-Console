package data.scripts;

import java.util.concurrent.ExecutorService;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;

public class CustomConsoleAppender extends AppenderSkeleton {
    private final CustomConsoleWindow windowInstance;
    private final ExecutorService appendExecutor;

    public CustomConsoleAppender(CustomConsoleWindow windowInstance) {
        this.windowInstance = windowInstance;
        this.windowInstance.init(this);
        this.appendExecutor = windowInstance.getAppendExecutor();
    }

    @Override
    public void append(LoggingEvent event) {
        appendExecutor.submit(() -> {
            windowInstance.appendText(event);
        });
    }

    @Override
    public void close() {}

    @Override
    public boolean requiresLayout() {
        return true;
    }
}