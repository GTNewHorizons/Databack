package databack.common.dto.worldgen.density_function;

import java.util.Arrays;

public final class DensityMask {

    private static final int BIT_COUNT = 16 * 16 * 16;
    private static final int WORD_COUNT = BIT_COUNT / 64;

    public final long[] mask = new long[WORD_COUNT];

    public DensityMask set(int x, int y, int z) {
        if (x < 0 || x >= 16) throw new IllegalArgumentException("x: " + x);
        if (y < 0 || y >= 16) throw new IllegalArgumentException("y: " + y);
        if (z < 0 || z >= 16) throw new IllegalArgumentException("z: " + z);

        int bit = z << 8 | y << 4 | x;

        int word = bit >> 6;

        mask[word] |= bitmask(bit);

        return this;
    }

    public boolean isSet(int bit) {
        if (bit < 0 || bit >= BIT_COUNT) throw new IllegalArgumentException("bit: " + bit);

        int word = bit >> 6;

        return (mask[word] & bitmask(bit)) != 0;
    }

    public boolean isSet(int x, int y, int z) {
        if (x < 0 || x >= 16) throw new IllegalArgumentException("x: " + x);
        if (y < 0 || y >= 16) throw new IllegalArgumentException("y: " + y);
        if (z < 0 || z >= 16) throw new IllegalArgumentException("z: " + z);

        int bit = z << 8 | y << 4 | x;

        int word = bit >> 6;

        return (mask[word] & bitmask(bit)) != 0;
    }

    public boolean isEmpty() {
        for (int w = 0; w < WORD_COUNT; w++) {
            if (this.mask[w] != 0) return false;
        }

        return true;
    }

    private static long bitmask(int bit) {
        return 1L << bit;
    }

    public boolean anySet(DensityMask mask) {
        for (int w = 0; w < WORD_COUNT; w++) {
            if ((this.mask[w] & mask.mask[w]) != 0) return true;
        }

         return false;
    }

    public boolean allSet(DensityMask mask) {
        for (int w = 0; w < WORD_COUNT; w++) {
            long target = mask.mask[w];

            if ((this.mask[w] & target) != target) return false;
        }

        return true;
    }

    public int nextSetBit(int bit) {
        if (bit >= BIT_COUNT) return -1;

        int wordIndex = bit >> 6;

        long word;

        for(word = this.mask[wordIndex] & -1L << bit; word == 0L; word = this.mask[wordIndex]) {
            wordIndex++;
            if (wordIndex == WORD_COUNT) {
                return -1;
            }
        }

        return wordIndex * 64 + Long.numberOfTrailingZeros(word);
    }

    public int nextClearBit(int bit) {
        int wordIndex = bit >> 6;

        long word;
        for(word = ~this.mask[wordIndex] & -1L << bit; word == 0L; word = ~this.mask[wordIndex]) {
            wordIndex++;
            if (wordIndex == WORD_COUNT) {
                return WORD_COUNT * 64;
            }
        }

        return wordIndex * 64 + Long.numberOfTrailingZeros(word);
    }

    public DensityMask remove(int x, int y, int z) {
        if (x < 0 || x >= 16) throw new IllegalArgumentException("x: " + x);
        if (y < 0 || y >= 16) throw new IllegalArgumentException("y: " + y);
        if (z < 0 || z >= 16) throw new IllegalArgumentException("z: " + z);

        int bit = z << 8 | y << 4 | x;

        int word = bit >> 6;

        mask[word] &= ~bitmask(bit);

        return this;
    }

    public DensityMask removeAll(DensityMask mask) {
        for (int w = 0; w < WORD_COUNT; w++) {
            this.mask[w] &= ~mask.mask[w];
        }

        return this;
    }

    /// Sets all bits starting from the given position (inclusive) through the end of the mask.
    /// Bits before this position are cleared.
    public DensityMask setStartingFrom(int x, int y, int z) {
        if (x < 0 || x >= 16) throw new IllegalArgumentException("x: " + x);
        if (y < 0 || y >= 16) throw new IllegalArgumentException("y: " + y);
        if (z < 0 || z >= 16) throw new IllegalArgumentException("z: " + z);

        int bit = z << 8 | y << 4 | x;

        int word = bit >> 6;

        for (int w = 0; w < WORD_COUNT; w++) {
            if (w < word) {
                mask[w] = 0;
            } else if (w > word) {
                mask[w] = -1L;
            } else {
                // Set all bits from local bit position to 63 (high end of word).
                // -(1L << n) == ~((1L << n) - 1) == all bits set from n..63.
                mask[w] = -bitmask(bit);
            }
        }

        return this;
    }

    /// Sets all bits up to and including the given position.
    /// Bits after this position are cleared.
    public DensityMask setUpTo(int x, int y, int z) {
        if (x < 0 || x >= 16) throw new IllegalArgumentException("x: " + x);
        if (y < 0 || y >= 16) throw new IllegalArgumentException("y: " + y);
        if (z < 0 || z >= 16) throw new IllegalArgumentException("z: " + z);

        int bit = z << 8 | y << 4 | x;
        int word = bit >> 6;
        int localBit = bit & 63;

        for (int w = 0; w < WORD_COUNT; w++) {
            if (w < word) {
                mask[w] = -1L;
            } else if (w > word) {
                mask[w] = 0;
            } else {
                // Set all bits from 0 to localBit inclusive.
                // (2 << n) - 1 sets bits 0..n; handle n==63 to avoid shift overflow.
                mask[w] = localBit == 63 ? -1L : (2L << localBit) - 1;
            }
        }

        return this;
    }

    /// Sets all 16 y-bits of the column at (x, z). More efficient than 16 set() calls.
    public DensityMask setColumn(int x, int z) {
        if (x < 0 || x >= 16) throw new IllegalArgumentException("x: " + x);
        if (z < 0 || z >= 16) throw new IllegalArgumentException("z: " + z);

        clear();

        // Each z-slice occupies 4 words (256 bits). Within a word, y varies in groups of 4:
        //   word 0: y=0..3, word 1: y=4..7, word 2: y=8..11, word 3: y=12..15
        // Within each word, the 4 y-values for column x sit at bits x, 16+x, 32+x, 48+x.
        long colBits = (1L << x) | (1L << (x + 16)) | (1L << (x + 32)) | (1L << (x + 48));
        int baseWord = z << 2;

        mask[baseWord]     = colBits;
        mask[baseWord + 1] = colBits;
        mask[baseWord + 2] = colBits;
        mask[baseWord + 3] = colBits;

        return this;
    }

    /// Sets all 256 bits at the given y level (all x, z).
    public DensityMask setLayer(int y) {
        if (y < 0 || y >= 16) throw new IllegalArgumentException("y: " + y);

        Arrays.fill(mask, 0);

        // Within a z-slice word, y values are grouped in 16-bit bands: y%4 selects the band.
        // The word within the slice is y>>2.
        long pattern = 0xFFFFL << ((y & 3) << 4);
        int wordOffset = y >> 2;

        for (int z = 0; z < 16; z++) {
            mask[(z << 2) + wordOffset] = pattern;
        }

        return this;
    }

    /// Copies source, projecting onto y=0: sets (x, 0, z) if any (x, *, z) is set in source.
    public DensityMask flatCopy(DensityMask source) {
        Arrays.fill(mask, 0);

        for (int z = 0; z < 16; z++) {
            int base = z << 2;
            // OR all 4 words of the z-slice to collapse the y dimension.
            long combined = source.mask[base] | source.mask[base + 1] | source.mask[base + 2] | source.mask[base + 3];
            // Fold the four 16-bit y-groups down to bits 0..15.
            long colMask = combined | (combined >>> 16) | (combined >>> 32) | (combined >>> 48);
            // y=0 bits live in the low 16 bits of word 0 of the z-slice.
            mask[base] = colMask & 0xFFFFL;
        }

        return this;
    }

    /// Bitwise-OR this mask with other in-place.
    public DensityMask or(DensityMask other) {
        for (int i = 0; i < WORD_COUNT; i++) {
            mask[i] |= other.mask[i];
        }
        return this;
    }

    /// Bitwise-AND this mask with other in-place.
    public DensityMask and(DensityMask other) {
        for (int i = 0; i < WORD_COUNT; i++) {
            mask[i] &= other.mask[i];
        }
        return this;
    }

    public DensityMask clear() {
        Arrays.fill(mask, 0);
        return this;
    }

    public DensityMask setAll() {
        Arrays.fill(mask, -1L);
        return this;
    }

    public DensityMask copy(DensityMask source) {
        System.arraycopy(source.mask, 0, this.mask, 0, WORD_COUNT);
        return this;
    }
}
