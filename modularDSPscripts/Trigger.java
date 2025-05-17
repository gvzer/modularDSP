import com.cycling74.max.*;
import com.cycling74.msp.*;
import java.util.Random;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class Trigger extends MSPObject {
    private String bufferName = "trigger"; // Buffer name should be "trigger"
    private float rateMs = 1000; // in milliseconds (trigger interval)
    private float maxSpeed = 1.0f; // max speed for playback
    private float volumeControl = 1.0f; // volume level, incoming lower limit for random volume range
    private float sampleRate = 44100.0f; // Sample rate for the audio
    private final Random random = new Random();
    private final ArrayList<TriggeredAudio> activeTriggers = new ArrayList<>();
    private float phase = 0.0f;

    // Limit the number of triggers that can be active at the same time
    private static final int MAX_ACTIVE_TRIGGERS = 10;

    public Trigger() {
        declareInlets(new int[]{DataTypes.ALL, DataTypes.ALL, DataTypes.ALL, DataTypes.ALL}); // Declare 4 inlets
        declareOutlets(new int[]{SIGNAL, SIGNAL}); // Declare outlets for stereo output
        post("Trigger initialized.");
    }

    @Override
    public Method dsp(MSPSignal[] ins, MSPSignal[] outs) {
        // Return the method that will be called to process the DSP
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

        // Ensure buffer is properly loaded before triggering audio
        if (bufferFrames <= 0) {
            //post("Error: Buffer is empty or not found during DSP processing.");
            for (int i = 0; i < outputSignalL.length; i++) {
                outputSignalL[i] = 0;
                outputSignalR[i] = 0;
            }
            return;
        }

        // Set the sampEnd to the buffer's end
        long sampEnd = bufferFrames;

        // Triggering at a fixed rate
        float rateSamples = (rateMs / 1000.0f) * sampleRate; // Default rateMs = 1000ms (1 second)
        //post("Trigger rate in samples: " + rateSamples);

        for (int i = 0; i < outputSignalL.length; i++) {
            phase += 1.0f / rateSamples;

            // Only spawn a new trigger if the rate allows it, and ensure that we don't spawn too many
            if (phase >= 1.0f && activeTriggers.size() < MAX_ACTIVE_TRIGGERS) { // Limit the number of active triggers
                phase -= 1.0f;
                spawnNewTrigger(bufferFrames); // Trigger new event at specified rate
            }

            outputSignalL[i] = 0;
            outputSignalR[i] = 0;

            for (int j = activeTriggers.size() - 1; j >= 0; j--) {
                TriggeredAudio trigger = activeTriggers.get(j);
                if (trigger.isFinished()) {
                    activeTriggers.remove(j); // Remove finished triggers
                    //post("Trigger " + j + " finished and removed.");
                } else {
                    float[] sample = trigger.getNextSample();
                    outputSignalL[i] += sample[0];
                    outputSignalR[i] += sample[1];
                }
            }
        }
    }

    private void spawnNewTrigger(long bufferFrames) {
        // Ensure we don't create too many triggers at once
        if (activeTriggers.size() >= MAX_ACTIVE_TRIGGERS) return;

        // Spawning a new trigger event with random speed and randomized volume
        TriggeredAudio trigger = new TriggeredAudio(bufferFrames, random.nextFloat() * maxSpeed, volumeControl);
        activeTriggers.add(trigger);
    }

    private class TriggeredAudio {
        private long start;
        private long end;
        private float speed;
        private float randomizedVolume; // Volume is randomized for each trigger
        private long currentPos;

        public TriggeredAudio(long bufferFrames, float speed, float volumeControl) {
            this.start = 0; // Play from the start of the buffer
            this.end = bufferFrames;
            this.speed = Math.max(1.0f, speed); // Ensure speed is never lower than 1.0

            // Randomize volume between 1.0 and the incoming volume (volumeControl is the lower limit)
            this.randomizedVolume = 1.0f + random.nextFloat() * (volumeControl - 1.0f); // Random volume between 1.0 and volumeControl

            this.currentPos = start;

            // Debugging: Check trigger initialization
            //post("New trigger created. Start: " + start + ", End: " + end + ", Speed: " + this.speed + ", Randomized Volume: " + this.randomizedVolume);
        }

        public boolean isFinished() {
            return currentPos >= end; // Trigger finishes when currentPos exceeds buffer end
        }

        public float[] getNextSample() {
            // Ensure we are not accessing out of bounds
            if (currentPos >= end) return new float[]{0, 0};

            long sampleIndex = Math.min(Math.round(currentPos), end - 1); // Safely limit the sample index within bounds
            float sampleL = MSPBuffer.peek(bufferName, 1, sampleIndex); // Left channel
            float sampleR = MSPBuffer.peek(bufferName, 2, sampleIndex); // Right channel

            // Apply the randomized volume directly to the sample
            //post("Sample at index " + sampleIndex + ": Randomized Volume = " + randomizedVolume);

            // Increment currentPos with speed (ensure it doesn't exceed end)
            currentPos += speed;

            // Return the sample values with randomized volume applied
            return new float[]{sampleL * randomizedVolume, sampleR * randomizedVolume};
        }
    }

    // Handling incoming values for rate, speed, and volume from the inlets
    public void inlet(float value) {
        int inletIdx = getInlet();
        long bufferFrames = MSPBuffer.getFrames(bufferName);
        if (bufferFrames <= 0) return;

        // Debug log to check the inlet index and the received value
        //post("Inlet " + inletIdx + " received value: " + value);

        // Handle inlets properly based on their index
        switch (inletIdx) {
            case 0:  // Trigger rate in milliseconds
                rateMs = Math.max(1, value); // Adjust rateMs, ensure it's never below 1ms
                break;
            case 1:  // Maximum playback speed
                maxSpeed = Math.max(1.0f, value); // Ensure maxSpeed is never less than 1.0
                break;
            case 2:  // Volume control for grains
                volumeControl = Math.max(0.0f, Math.min(1.0f, value)); // Ensure volume is between 0 and 1
                break;
            case 3:  // Empty case (you can keep it unused)
                break;
        }
    }
}
