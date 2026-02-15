package ru.muslim.tajwid.service;

public record ReferralProcessResult(
    boolean payloadValid,
    boolean alreadyCounted,
    boolean newlyCounted,
    int referralPoints,
    boolean anomaly
) {

    public static ReferralProcessResult invalid() {
        return new ReferralProcessResult(false, false, false, 0, true);
    }
}
