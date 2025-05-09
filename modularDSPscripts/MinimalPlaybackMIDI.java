import com.cycling74.max.*;
import com.cycling74.msp.*;

import java.lang.reflect.Method;

public class MinimalPlaybackMIDI extends MSPObject {
    private String bufferName = "polyphonicPlayback";   // Name of the buffer
    private long sampStart = 0;          // Start sample index
    private long sampEnd = 1;            // End sample index
    private long pendingStart = 0;       // Pending start sample index
    private long pendingEnd = 1;         // Pending end sample index
    private float phasorRate = 1.0f;     // Cycles per second
    private float currentPhase = 0.0f;   // Internal phasor phase
    private float previousPhase = 0.0f;  // Previous phase value
    private float sampleRate = 44100.0f; // Default sample rate
    private boolean rangeChanged = false; // Flag for pending range update

    public MinimalPlaybackMIDI() {
        declareInlets(new int[]{SIGNAL, DataTypes.ALL, DataTypes.ALL, DataTypes.ALL}); // Empty, Cycles/sec, Start, End
        declareOutlets(new int[]{SIGNAL});                                           // Output audio signal
        //post("MinimalPlayback initialized.");
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
        float[] outputSignal = outs[0].vec; // Output signal buffer

        long bufferFrames = MSPBuffer.getFrames(bufferName);
        if (bufferFrames <= 0) {
            //post("Error: Buffer is empty or not found.");
            for (int i = 0; i < outputSignal.length; i++) outputSignal[i] = 0;
            return;
        }

        // Detect start of a new cycle using a phase delta
        float phaseIncrement = phasorRate / sampleRate;
        for (int i = 0; i < outputSignal.length; i++) {
            currentPhase += phaseIncrement;

            if (currentPhase >= 1.0f) {
                currentPhase -= 1.0f;
            }

            if (currentPhase < previousPhase && rangeChanged) {
                // Phase has wrapped, apply pending range
                sampStart = Math.max(0, Math.min(pendingStart, bufferFrames - 1));
                sampEnd = Math.max(sampStart + 1, Math.min(pendingEnd, bufferFrames));
                rangeChanged = false;
                //post("Range updated: sampStart = " + sampStart + ", sampEnd = " + sampEnd);
            }

            previousPhase = currentPhase;

            if (phasorRate <= 0.0f || sampEnd <= sampStart) {
                //post("Error: Invalid phasorRate or range, outputting silence.");
                outputSignal[i] = 0;
                continue;
            }

            long rangeLength = sampEnd - sampStart;
            long sampleIndex = sampStart + Math.round(currentPhase * rangeLength);
            sampleIndex = Math.max(sampStart, Math.min(sampleIndex, sampEnd - 1));

            float sample = MSPBuffer.peek(bufferName, 0, sampleIndex);

            // Apply fades
            float fadeFactor = 1.0f;
            if (currentPhase < 0.1f) { // Fade-in over first 10% of the range
                fadeFactor = currentPhase / 0.1f;
            } else if (currentPhase > 0.9f) { // Fade-out over last 10% of the range
                fadeFactor = (1.0f - currentPhase) / 0.1f;
            }

            outputSignal[i] = sample * fadeFactor;
        }
    }

    public void inlet(float value) {
        int inletIdx = getInlet(); // Get the index of the inlet that received the value

        // Debug: Log which inlet is triggered
        //post("Debug: Value received on inlet " + inletIdx + ": " + value);

        long bufferFrames = MSPBuffer.getFrames(bufferName);

        if (bufferFrames <= 0) {
            //post("Error: Buffer not loaded or empty.");
            return;
        }

        switch (inletIdx) {
            case 1: // Second inlet: phasorRate
                if (value <= 0) {
                    //post("Warning: Received invalid phasorRate (" + value + "), ignoring.");
                } else {
                    phasorRate = value; // Accept valid phasorRate
                    //post("Phasor rate updated to: " + phasorRate + " cycles/sec");
                }
                break;

            case 2: // Third inlet: sampStart
                pendingStart = Math.round(Math.max(0, Math.min(value, bufferFrames - 1)));
                rangeChanged = true; // Mark range as pending
                //post("Pending start updated to: " + pendingStart);
                break;

            case 3: // Fourth inlet: sampEnd
                pendingEnd = Math.round(Math.max(0, Math.min(value, bufferFrames - 1)));
                rangeChanged = true; // Mark range as pending
                //post("Pending end updated to: " + pendingEnd);
                break;

            default:
                //post("Warning: Unhandled inlet index: " + inletIdx);
        }
    }
}
