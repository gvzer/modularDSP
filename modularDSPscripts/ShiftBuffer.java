import com.cycling74.max.*;
import com.cycling74.msp.*;
import java.lang.reflect.Method;

public class ShiftBuffer extends MSPObject {

    private static final int BUFFER_SIZE = 1024;
    private float[] lastBufferState = new float[BUFFER_SIZE];
    private MaxClock clock;
    private float frequency = 440.0f;  // Default sine wave frequency
    private float phase = 0.0f;
    private static final float SAMPLE_RATE = 44100.0f;  // Standard sample rate

    public ShiftBuffer() {
        declareInlets(new int[]{ SIGNAL, DataTypes.ALL });  // Inlet 1: Signal, Inlet 2: Frequency Control
        declareOutlets(new int[]{ SIGNAL });  // Outlet for the sine wave

        startMonitoring();
    }

    private void startMonitoring() {
        clock = new MaxClock(new Executable() {
            public void execute() {
                detectChangesAndShift("amplitudes", "transfervalues", "newamplitudes");
                clock.delay(10);
            }
        });
        clock.delay(10);
    }

    private void detectChangesAndShift(String bufferName, String transferName, String newBufferName) {
        float[] amplitudes = MSPBuffer.peek(bufferName, 1);

        boolean hasChanged = false;
        for (int i = 0; i < BUFFER_SIZE; i++) {
            if (amplitudes[i] != lastBufferState[i]) {
                hasChanged = true;
                break;
            }
        }

        if (hasChanged) {
            shiftBuffer(bufferName, transferName, newBufferName);
            System.arraycopy(amplitudes, 0, lastBufferState, 0, BUFFER_SIZE);
        }
    }

    public void shiftBuffer(String bufferName, String transferName, String newBufferName) {
        float[] amplitudes = MSPBuffer.peek(bufferName, 1);
        float[] phases = MSPBuffer.peek(bufferName, 2);
        float[] transfervalues = MSPBuffer.peek(transferName, 1);
        float[] newAmplitudes = new float[BUFFER_SIZE];
        float[] newPhases = new float[BUFFER_SIZE];

        for (int i = 0; i < BUFFER_SIZE; i++) {
            newAmplitudes[i] = 0;
            newPhases[i] = phases[i]; 
        }

        for (int i = 0; i < BUFFER_SIZE; i++) {
            int shiftedIndex = Math.round(transfervalues[i]);

            if (shiftedIndex >= 0 && shiftedIndex < BUFFER_SIZE) {
                newAmplitudes[shiftedIndex] += amplitudes[i]; 
                newPhases[shiftedIndex] = phases[i]; 
            }
        }

        MaxSystem.deferLow(() -> {
            MSPBuffer.poke(newBufferName, 1, newAmplitudes);
            MSPBuffer.poke(newBufferName, 2, newPhases);
        });
    }

    public Method dsp(MSPSignal[] in, MSPSignal[] out) {
        try {
            return getClass().getDeclaredMethod("perform", MSPSignal[].class, MSPSignal[].class);
        } catch (NoSuchMethodException e) {
            //post("Error: Could not find perform method.");
            return null;
        }
    }

    // Generate a sine wave based on input frequency
    public void perform(MSPSignal[] in, MSPSignal[] out) {
        float[] outputSignal = out[0].vec;
        float phaseIncrement = (float) (2.0 * Math.PI * frequency / SAMPLE_RATE);

        for (int i = 0; i < outputSignal.length; i++) {
            outputSignal[i] = (float) Math.sin(phase);
            phase += phaseIncrement;
            if (phase > (float) (2.0 * Math.PI)) {
                phase -= (float) (2.0 * Math.PI);
            }
        }
    }

    // Receive frequency from inlet
    public void inlet(float value) {
        frequency = Math.max(1.0f, Math.min(value, 20000.0f));  // Clamp between 1 Hz and 20 kHz
    }

    public void notifyDeleted() {
        if (clock != null) {
            clock.unset();
        }
    }
}
