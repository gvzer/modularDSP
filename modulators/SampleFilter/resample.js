autowatch = 1;
inlets = 2;
outlets = 1;

var matrixSize = 1024; // Target output size
var sampleStart = 0;
var sampleEnd = 10000;
var bufferName = "samplefilter";

function update() {
    var aud = new Buffer(bufferName);
    var len = aud.framecount();
    
    if (len <= 0) {
        post("⚠️ Error: Buffer '" + bufferName + "' is empty or not loaded.\n");
        return;
    }

    // Ensure sampleStart and sampleEnd are within valid bounds
    sampleStart = Math.max(0, Math.min(sampleStart, len - 1));
    sampleEnd = Math.max(sampleStart + 1, Math.min(sampleEnd, len));

    var values = [];
    var range = sampleEnd - sampleStart;
    var step = range / (matrixSize - 1);

    for (var i = 0; i < matrixSize; i++) {
        var interpIndex = sampleStart + i * step;
        var lower = Math.floor(interpIndex);
        var upper = Math.min(lower + 1, len - 1);
        var frac = interpIndex - lower;

        var sL = aud.peek(1, lower); // Sample at lower index
        var sU = aud.peek(1, upper); // Sample at upper index
        var sampleVal = (1 - frac) * sL + frac * sU; // Linear interpolation

        values.push(sampleVal);
    }

    // Ensure data is actually sent
    outlet(0, values);
}

// Receive integer inputs for sample start and end
function msg_int(v) {
    if (inlet === 0) {
        sampleStart = v;
        post("🛠 sampleStart set to: " + sampleStart + "\n");
    } else if (inlet === 1) {
        sampleEnd = v;
        post("🛠 sampleEnd set to: " + sampleEnd + "\n");
    }

    // Auto-update list output whenever a new value is received
    update();
}

// Support floating point values
function msg_float(v) {
    msg_int(v);
}
