package databack.common.dto.worldgen.noise;

import com.gtnewhorizon.gtnhlib.noise.NoiseSampler;
import com.gtnewhorizon.gtnhlib.noise.OctavesSampler;
import com.gtnewhorizon.gtnhlib.noise.SimplexSampler;
import com.gtnewhorizon.gtnhlib.util.StdLCG;

public class DatapackNoise {

    public int firstOctave;
    public double[] amplitudes;

    public OctavesSampler createSampler(long seed) {
        StdLCG rand = new StdLCG(seed);

        int n = this.amplitudes.length;

        NoiseSampler[] octaves = new NoiseSampler[n];
        double[] amp = new double[n];
        double[] freq = new double[n];

        for (int i = 0; i < n; i++) {
            if (amplitudes[i] == 0) continue;

            int octaveIndex = i - firstOctave;

            octaves[i] = new SimplexSampler(rand);
            amp[i] = Math.pow(2, octaveIndex);
            freq[i] = amplitudes[i] * Math.pow(2, n - i - 1) / (Math.pow(2, n) - 1);
        }

        return new OctavesSampler(octaves, amp, freq);
    }
}
