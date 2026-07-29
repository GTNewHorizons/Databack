package databack.common.worldgen.rng;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.github.bsideup.jabel.Desugar;
import com.google.common.primitives.Longs;

public class RNGUtils {

    /**
     * The first 32 bits of the golden ratio (1+sqrt(5))/2, forced to be odd.
     * Useful for producing good Weyl sequences or as an arbitrary nonzero odd
     * value.
     */
    public static final int  GOLDEN_RATIO_32 = 0x9e3779b9;
    /**
     * The first 64 bits of the golden ratio (1+sqrt(5))/2, forced to be odd.
     * Useful for producing good Weyl sequences or as an arbitrary nonzero odd
     * value.
     */
    public static final long GOLDEN_RATIO_64 = 0x9e3779b97f4a7c15L;
    /**
     * The first 32 bits of the silver ratio 1+sqrt(2), forced to be odd. Useful
     * for producing good Weyl sequences or as an arbitrary nonzero odd value.
     */
    public static final int  SILVER_RATIO_32 = 0x6A09E667;
    /**
     * The first 64 bits of the silver ratio 1+sqrt(2), forced to be odd. Useful
     * for producing good Weyl sequences or as an arbitrary nonzero odd value.
     */
    public static final long SILVER_RATIO_64 = 0x6A09E667F3BCC909L;

    /**
     * Computes Stafford variant 13 of the 64-bit mixing function for
     * MurmurHash3. This is a 64-bit hashing function with excellent avalanche
     * statistics.
     * http://zimbry.blogspot.com/2011/09/better-bit-mixing-improving-on.html
     *
     * <p> Note that if the argument {@code z} is 0, the result is 0.
     *
     * @param z any long value
     *
     * @return the result of hashing z
     */
    public static long mixStafford13(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    public static Seed128bit upgradeSeedTo128bitUnmixed(long legacySeed) {
        long lowBits = legacySeed ^ SILVER_RATIO_64;
        long highBits = lowBits + GOLDEN_RATIO_64;
        return new Seed128bit(lowBits, highBits);
    }

    public static Seed128bit upgradeSeedTo128bit(long legacySeed) {
        return upgradeSeedTo128bitUnmixed(legacySeed).mixed();
    }

    @Desugar
    public record Seed128bit(long seedLo, long seedHi) {
        public Seed128bit xor(long lo, long hi) {
            return new Seed128bit(this.seedLo ^ lo, this.seedHi ^ hi);
        }

        public Seed128bit xor(Seed128bit other) {
            return this.xor(other.seedLo, other.seedHi);
        }

        public Seed128bit mixed() {
            return new Seed128bit(mixStafford13(this.seedLo), mixStafford13(this.seedHi));
        }
    }

    public static Seed128bit seedFromHashOf(String key) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(key.getBytes(StandardCharsets.UTF_8));
            long hashLo = Longs.fromBytes(digest[0], digest[1], digest[2], digest[3], digest[4], digest[5], digest[6], digest[7]);
            long hashHi = Longs.fromBytes(digest[8], digest[9], digest[10], digest[11], digest[12], digest[13], digest[14], digest[15]);
            return new Seed128bit(hashLo, hashHi);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("MD5 not available", e);
        }
    }

    public static long getPosSeed(int x, int y, int z) {
        long seed = x * 3129871L ^ (long)z * 116129781L ^ (long)y;
        seed = seed * seed * 42317861L + seed * 11L;
        return seed >> 16;
    }
}
