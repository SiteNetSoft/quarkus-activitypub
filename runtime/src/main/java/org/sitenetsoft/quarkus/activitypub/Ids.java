package org.sitenetsoft.quarkus.activitypub;

import java.security.SecureRandom;

/**
 * Identifier generation.
 */
public final class Ids {

    private static final char[] CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long RANDOM_HI_MASK = 0xFFFFL; // the 80 random bits are held as 16 high + 64 low bits

    private static long lastMillis = -1;
    private static long randomHi;
    private static long randomLo;

    private Ids() {
    }

    /**
     * A new ULID: 26 Crockford base32 characters, 48-bit millisecond timestamp then 80 random bits. Monotonic within
     * this JVM (within one millisecond the random part is incremented), so ids sort lexicographically by creation time.
     */
    public static String ulid() {
        long millis;
        long hi;
        long lo;
        synchronized (Ids.class) {
            millis = Math.max(System.currentTimeMillis(), lastMillis);
            if (millis == lastMillis) {
                randomLo++;
                if (randomLo == 0) {
                    randomHi = (randomHi + 1) & RANDOM_HI_MASK;
                    if (randomHi == 0) { // random part exhausted within this millisecond: move to the next one
                        millis++;
                        reseed();
                    }
                }
            } else {
                reseed();
            }
            lastMillis = millis;
            hi = randomHi;
            lo = randomLo;
        }
        char[] out = new char[26];
        for (int i = 9; i >= 0; i--) {
            out[i] = CROCKFORD[(int) (millis & 31)];
            millis >>>= 5;
        }
        for (int i = 25; i >= 10; i--) {
            out[i] = CROCKFORD[(int) (lo & 31)];
            lo = (lo >>> 5) | ((hi & 31) << 59);
            hi >>>= 5;
        }
        return new String(out);
    }

    private static void reseed() {
        randomHi = RANDOM.nextLong() & RANDOM_HI_MASK;
        randomLo = RANDOM.nextLong();
    }
}
