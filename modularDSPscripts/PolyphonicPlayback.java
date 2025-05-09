import com.cycling74.max.*;
import com.cycling74.msp.*;
import java.lang.reflect.Method;

public class PolyphonicPlayback extends MSPObject {
    private String bufferName = "polyphonicPlaybackBuf"; // The name of the buffer
    private long sampStart = 0;           // Start sample index
    private long sampEnd = 1;             // End sample index
    private long pendingStart = 0;        // Pending start sample index
    private long pendingEnd = 1;          // Pending end sample index
    private float phasorRate = 1.0f;      // Cycles per second
    private float sampleRate = 44100.0f;  // Default sample rate
    private boolean rangeChanged = false; // Flag for pending range update
    private Voice[] voices = new Voice[6]; // Array to hold 6 voices

    public PolyphonicPlayback() {
        declareInlets(new int[]{SIGNAL, DataTypes.ALL, DataTypes.ALL, DataTypes.ALL}); // Inlets for signal, MIDI data, sampStart, sampEnd
        declareOutlets(new int[]{SIGNAL});                                          // Output audio signal
        for (int i = 0; i < voices.length; i++) {
            voices[i] = new Voice(); // Initialize all voices
        }
    }

    @Override
    public Method dsp(MSPSignal[] ins, MSPSignal[] outs) {
        try {
            return getClass().getDeclaredMethod("perform", MSPSignal[].class, MSPSignal[].class);
        } catch (NoSuchMethodException e) {
            post("Error: Could not find perform method.");
            return null;
        }
    }

    public void perform(MSPSignal[] ins, MSPSignal[] outs) {
        float[] outputSignal = outs[0].vec; // Output signal buffer

        long bufferFrames = MSPBuffer.getFrames(bufferName);
        if (bufferFrames <= 0) {
            for (int i = 0; i < outputSignal.length; i++) outputSignal[i] = 0;
            return;
        }

        // Process each voice
        for (Voice voice : voices) {
            if (voice.isActive()) {
                voice.process(outputSignal, bufferFrames, sampStart, sampEnd, phasorRate, sampleRate);
            }
        }
    }

    public void inlet(Object value) {
        int inletIdx = getInlet(); // Get the index of the inlet that received the value

        // Check for MIDI data (note and velocity)
        if (inletIdx == 1 && value instanceof Object[]) {
            Object[] midiData = (Object[]) value; // Cast the value to an Object array
            if (midiData.length == 2) { // Expected format: [note, velocity]
                float midiNote = ((Number) midiData[0]).floatValue();
                float velocity = ((Number) midiData[1]).floatValue();
                post("Received MIDI note: " + midiNote + ", velocity: " + velocity);

                // Assign the note and velocity to an available voice
                assignVoice(midiNote, velocity);
            } else {
                post("Error: Expected a list of 2 elements (note and velocity). Received: " + midiData.length);
            }
        }
        // Check for sample start and end updates
        else if (inletIdx == 2) {
            pendingStart = Math.round(Math.max(0, Math.min(((Number) value).floatValue(), MSPBuffer.getFrames(bufferName) - 1)));
            rangeChanged = true;
        } else if (inletIdx == 3) {
            pendingEnd = Math.round(Math.max(0, Math.min(((Number) value).floatValue(), MSPBuffer.getFrames(bufferName))));
            rangeChanged = true;
        }

        // Apply pending range change
        if (rangeChanged) {
            sampStart = Math.max(0, Math.min(pendingStart, MSPBuffer.getFrames(bufferName) - 1));
            sampEnd = Math.max(sampStart + 1, Math.min(pendingEnd, MSPBuffer.getFrames(bufferName)));
            rangeChanged = false;
            post("Range updated: sampStart = " + sampStart + ", sampEnd = " + sampEnd);
        }
    }

    // Assign a voice to a MIDI note
    private void assignVoice(float midiNote, float velocity) {
        for (Voice voice : voices) {
            if (!voice.isActive()) {
                voice.activate(midiNote, velocity);
                break;
            }
        }
    }

    // Voice class to represent each polyphonic voice
    private class Voice {
        private boolean active = false;
        private float midiNote;
        private float velocity;
        private float frequency;

        public void activate(float midiNote, float velocity) {
            this.active = true;
            this.midiNote = midiNote;
            this.velocity = velocity;
            this.frequency = midiToFreq(midiNote); // Convert MIDI note to frequency
            post("Voice activated: MidiNote = " + midiNote + ", Frequency = " + frequency + ", Velocity = " + velocity);
        }

        public void deactivate() {
            this.active = false;
            post("Voice deactivated");
        }

        public boolean isActive() {
            return active;
        }

        public void process(float[] outputSignal, long bufferFrames, long sampStart, long sampEnd, float phasorRate, float sampleRate) {
            float phaseIncrement = phasorRate / sampleRate;
            for (int i = 0; i < outputSignal.length; i++) {
                // Sample processing logic
                float sample = MSPBuffer.peek(bufferName, 0, Math.round((i / (float) outputSignal.length) * (sampEnd - sampStart)));
                outputSignal[i] += sample * velocity; // Apply velocity scaling
            }
        }

        private float midiToFreq(float midiNote) {
            return 440.0f * (float) Math.pow(2.0, (midiNote - 69) / 12.0);
        }
    }
}
