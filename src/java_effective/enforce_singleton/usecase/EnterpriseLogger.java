package java_effective.enforce_singleton.usecase;

import java.nio.file.Path;
import java.util.logging.Level;

public enum EnterpriseLogger {
    INSTANCE;

    private Path logPath;
    private Level logLevel;
    private boolean initialized = false;

    public synchronized void  initialize(Path logPath, Level logLevel) {
        if (initialized) {
            return;
        }
        this.logPath = logPath;
        this.logLevel = logLevel;
        initialized = true;
    }

    public void log(Level level, String message) {
        if (!initialized) throw new IllegalStateException("Logger not initialized");
        // write to files....
    }
}
