package java_effective.enforce_singleton.exercise;

public enum Cache {
    INSTANCE;

    private int maxSize;
    private boolean initialized = false;

    public synchronized void initialize(int maxSize) {
        if (initialized) {
            throw new IllegalStateException("Cache already initialized");
        }
        this.maxSize = maxSize;
        initialized = true;
    }

    public synchronized void put(String key, Object value) {
        checkInitialized();
        System.out.println("Putting " + value + " in cache with key " + key);
    }

    public synchronized Object get(String key) {
        checkInitialized();
        System.out.println("Getting value for key " + key);
        return null;
    }

    public synchronized void clear() {
        checkInitialized();
        System.out.println("Clearing cache");
    }

    private void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("Cache not initialized. Call initialize(maxSize) first.");
        }
    }

    public static void main(String[] args) {
        Cache cache = Cache.INSTANCE;
        cache.initialize(100);
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.clear();
    }
}
