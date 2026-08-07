package dev.hieunv.riskassessment.constant;

/**
 * The 3 conditions that trigger scoring — spec section A.1.
 */
public enum TriggerType {

    /** Blacklist was modified — scan all customers, match against the blacklist only (case 1). */
    T1,

    /**
     * Case 1 in REVERSE: start from the blacklist records that just changed and trace out the
     * feeds the queue differs.
     */
    T1R,

    /** Customer newly opened or updated a wallet — scan 1 customer, match against the blacklist only (case 2). */
    T2,

    /** Scheduled run, reference lists WERE modified — scan all customers, match against every criterion (case 3). */
    T3A,

    /** Scheduled run, reference lists were NOT modified — scan customers created on T-1, excluding the blacklist (case 3). */
    T3B;

    /**
     * Whether this scan matches against the blacklist only, or against all reference lists.
     */
    public boolean isBlacklistOnly() {
        return this == T1 || this == T1R || this == T2;
    }
}
