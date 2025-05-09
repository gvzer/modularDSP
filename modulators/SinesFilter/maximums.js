autowatch = 1;
inlets = 2;
outlets = 2; // Two outputs: original list and peaks-only list

var matrixSize = 1024; // Target output size
var sampleStart = 0;
var sampleEnd = 10000;
var bufferName = "sinesfilter";

function update() {
    var aud = new Buffer(bufferName);
    var len = aud.framecount();
    
    if (len <= 0) {
        //post("⚠️ Error: Buffer '" + bufferName + "' is empty or not loaded.\n");
        return;
    }

    // Ensure sampleStart and sampleEnd are within valid bounds
    sampleStart = Math.max(0, Math.min(sampleStart, len - 1));
    sampleEnd = Math.max(sampleStart + 1, Math.min(sampleEnd, len));

    var values = [];
    var peaks = []; // Second list for local maxima (initialized manually)

    var range = sampleEnd - sampleStart;
    var step = range / (matrixSize - 1);

    // Step 1: Resample values
    for (var i = 0; i < matrixSize; i++) {
        var interpIndex = sampleStart + i * step;
        var lower = Math.floor(interpIndex);
        var upper = Math.min(lower + 1, len - 1);
        var frac = interpIndex - lower;

        var sL = aud.peek(1, lower); // Sample at lower index
        var sU = aud.peek(1, upper); // Sample at upper index
        var sampleVal = (1 - frac) * sL + frac * sU; // Linear interpolation

        values.push(sampleVal);
        peaks.push(0); // Manually initialize peaks list with zeros
    }

    // Step 2: Detect local maxima and populate the peaks list
    for (var i = 1; i < matrixSize - 1; i++) {
        if (values[i] > values[i - 1] && values[i] > values[i + 1]) {
            peaks[i] = values[i]; // Keep original peak value
        }
    }

    // Output both lists
    outlet(0, values); // Original resampled list
    outlet(1, peaks); // Peaks-only list
}

// Receive integer inputs for sample start and end
function msg_int(v) {
    if (inlet === 0) {
        sampleStart = v;
        //post("🛠 sampleStart set to: " + sampleStart + "\n");
    } else if (inlet === 1) {
        sampleEnd = v;
        //post("🛠 sampleEnd set to: " + sampleEnd + "\n");
    }

    // Auto-update list output whenever a new value is received
    update();
}

// Support floating point values
function msg_float(v) {
    msg_int(v);
}
