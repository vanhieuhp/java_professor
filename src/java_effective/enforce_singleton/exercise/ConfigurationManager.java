package java_effective.enforce_singleton.exercise;

import java.util.Properties;

public class ConfigurationManager {

    private static ConfigurationManager instance;
    private Properties properties;

    private ConfigurationManager() {
        properties = new Properties();
        properties.setProperty("app.name", "MyApp");
    }

    public static ConfigurationManager getInstance() {
        if (instance == null) {
            instance = new ConfigurationManager();
        }
        return instance;
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }
/*
* What you need to do: Rewrite using enum pattern, maintaining all functionality

Expected outcome: Single enum with INSTANCE, properties field, and methods
* */
    enum ConfigurationEnum {
        INSTANCE;

        private final Properties properties;
        ConfigurationEnum() {
            properties = new Properties();
            properties.setProperty("app.name", "MyApp");
        }

        public String getProperty(String key) {
            return properties.getProperty(key);
        }

        public void setProperty(String key, String value) {
            properties.setProperty(key, value);
        }
    }
}
