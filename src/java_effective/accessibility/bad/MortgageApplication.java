package java_effective.accessibility.bad;

import io.lettuce.core.event.EventBus;

public class MortgageApplication {
    private final String applicantName;
    private final double amount;

    public MortgageApplication(String name, double amount) {
        this.applicantName = name;
        this.amount = amount;

        // ❌ BUG: 'this' escapes before construction is complete!
        // The partially-constructed object is now visible to other threads/classes
//        EventBus.register(this);  // Listeners can access fields before they're set

        // If another listener calls getApplicantName() here → NPE or garbage data!
    }

}
