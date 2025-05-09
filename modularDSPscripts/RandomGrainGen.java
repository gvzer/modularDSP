import com.cycling74.max.*;
import com.cycling74.msp.*;
import java.util.Random;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class RandomGrainGen extends MSPObject {
    private String bufferName = "grainer";
    private long sampStart = 0;
    private long sampEnd = 1;
    private int rateMs = 100;
    private float maxSpeed = 1.0f;
    private float overallSpeed = 1.0f;
    private float sampleRate = 44100.0f;
    private float grainVolume = 1.0f; // Volume control for grains
    private final int minGrainSize = 3500;
    private final Random random = new Random();
    private final ArrayList<Grain> activeGrains = new ArrayList<>();
    private float phase = 0.0f;

    public RandomGrainGen() {
        // Declare 6 inlets, starting with case 0 (empty)
        declareInlets(new int[]{DataTypes.ALL, DataTypes.ALL, DataTypes.ALL, DataTypes.ALL, DataTypes.ALL, DataTypes.ALL, DataTypes.ALL});
        declareOutlets(new int[]{SIGNAL, SIGNAL});
        //post("RandomGrainGen initialized.");
    }

    @Override
    public Method dsp(MSPSignal[] ins, MSPSignal[] outs) {
        try {
            return getClass().getDeclaredMethod("perform", MSPSignal[].class, MSPSignal[].class);
        } catch (NoSuchMethodException e) {
            //post("Error: Could not find perform method.");
            return null;
        }
    }

    public void perform(MSPSignal[] ins, MSPSignal[] outs) {
        float[] outputSignalL = outs[0].vec;
        float[] outputSignalR = outs[1].vec;
        long bufferFrames = MSPBuffer.getFrames(bufferName);
        if (bufferFrames <= 0) {
            //post("Error: Buffer is empty or not found.");
            for (int i = 0; i < outputSignalL.length; i++) {
                outputSignalL[i] = 0;
                outputSignalR[i] = 0;
            }
            return;
        }

        if (sampEnd - sampStart < minGrainSize) {
            sampEnd = (sampStart + minGrainSize) - Math.max(0, sampStart + minGrainSize - bufferFrames);
            //post("Adjusted sampEnd to ensure min grain size: " + sampEnd);
        }

        float rateSamples = (rateMs / 1000.0f) * sampleRate;
        for (int i = 0; i < outputSignalL.length; i++) {
            phase += 1.0f / rateSamples;
            if (phase >= 1.0f) {
                phase -= 1.0f;
                spawnNewGrain(bufferFrames);
            }

            outputSignalL[i] = 0;
            outputSignalR[i] = 0;
            for (int j = activeGrains.size() - 1; j >= 0; j--) {
                Grain grain = activeGrains.get(j);
                if (grain.isFinished()) {
                    activeGrains.remove(j);
                } else {
                    float[] sample = grain.getNextSample();
                    float sampleL = sample[0];
                    float sampleR = sample[1];

                    // Apply the fade factor to each grain
                    float fadeFactor = grain.getFadeFactor();
                    sampleL *= fadeFactor * grain.getVolumeFactor(); // Apply fade and volume to left channel
                    sampleR *= fadeFactor * grain.getVolumeFactor(); // Apply fade and volume to right channel

                    outputSignalL[i] += sampleL;
                    outputSignalR[i] += sampleR;
                }
            }
        }
    }

    private void spawnNewGrain(long bufferFrames) {
        if (sampEnd - sampStart < minGrainSize) return;
        long grainStart = sampStart + random.nextInt((int) Math.max(1, sampEnd - sampStart - minGrainSize));
        long grainSize = minGrainSize + random.nextInt((int) Math.max(1, sampEnd - sampStart - minGrainSize));
        if (grainSize + grainStart > bufferFrames) grainSize -= (grainSize + grainStart - bufferFrames);
        activeGrains.add(new Grain(grainStart, grainSize, bufferFrames, grainVolume, random.nextFloat() * maxSpeed));
    }

    private class Grain {
        private long start;
        private long grainlength;
        private float currentPos;
        private long end;
        private float volumeFactor;  // Store the volume factor for each grain
        private float fadeInThreshold;
        private float fadeOutThreshold;
        private float speed;

        public Grain(long start, long length, long bufferFrames, float grainVolume, float speed) {
            this.start = start;
            this.grainlength = length;
            this.currentPos = start;
            this.end = Math.min(start + length, bufferFrames - 1);
            this.speed = Math.max(1.0f, speed);

            // Generate the random volume factor for this grain
            this.volumeFactor = grainVolume + random.nextFloat() * (1.0f - grainVolume);

            // Set the fade-in and fade-out thresholds
            this.fadeInThreshold = start + 0.2f * grainlength;  // 10% fade-in
            this.fadeOutThreshold = start + 0.8f * grainlength; // 10% fade-out
        }

        public boolean isFinished() {
            return currentPos >= end;
        }

        public float[] getNextSample() {
            if (isFinished()) return new float[]{0, 0};
            long sampleIndex = Math.round(currentPos);
            float sampleL = MSPBuffer.peek(bufferName, 1, sampleIndex); // Left channel
            float sampleR = MSPBuffer.peek(bufferName, 2, sampleIndex); // Right channel
            currentPos += overallSpeed;
            currentPos += speed;
            return new float[]{sampleL, sampleR};
        }

        // Return the volume factor for this grain
        public float getVolumeFactor() {
            return volumeFactor;
        }

        // Return the fade factor based on the current position of the grain
        public float getFadeFactor() {
            if (currentPos < fadeInThreshold) {
                // Fade-in: gradually increase from 0 to 1
                return (currentPos - start) / (fadeInThreshold - start);
            } else if (currentPos > fadeOutThreshold) {
                // Fade-out: gradually decrease from 1 to 0
                return (end - currentPos) / (end - fadeOutThreshold);
            } else {
                // No fade: full volume
                return 1.0f;
            }
        }
    }

    public void inlet(float value) {
        int inletIdx = getInlet();
        long bufferFrames = MSPBuffer.getFrames(bufferName);
        if (bufferFrames <= 0) return;

        // Debug log to check the inlet index and the received value
        //post("Inlet " + inletIdx + " received value: " + value);

        // Handle inlets properly based on their index
        switch (inletIdx) {
            case 0:  // Inlet 1: sampStart
                sampStart = Math.round(Math.max(0, Math.min(value, bufferFrames - 1)));
                break;
            case 1:  // Inlet 2: sampEnd
                sampEnd = Math.round(Math.max(0, Math.min(value, bufferFrames - 1)));
                break;
            case 2:  // Inlet 3: rateMs
                rateMs = Math.max(1, (int) value);
                break;
            case 3:  // Inlet 4: speed
                overallSpeed = Math.max(0.1f, value);
                break;
            case 4:  // Inlet 5: volume control for grains
                grainVolume = Math.max(0.0f, Math.min(value, 1.0f));
                break;
            case 5: 
                maxSpeed = Math.max(0.1f, value);
                break;
        }
    }
}
