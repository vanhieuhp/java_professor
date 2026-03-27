import java.util.concurrent.Executors;

public class RequestContext {

    // Works fine with platform threads. What goes wrong with virtual threads?
    private static final ThreadLocal<String> userId = new ThreadLocal<>();

    public static void setUser(String id) { userId.set(id); }
    public static String getUser() { return userId.get(); }
    public static void clear() { userId.remove(); }

    // Middleware sets this for each request
    public void handleRequest(String user, Runnable task) {
        setUser(user);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(task); // Does this task see the right userId?
        }
    }
}
