// Reference the buffer named "waveformshifter"
var buffer;
var samp_start = 0;
var samp_end = 1023;

inlets = 2; // 1st inlet for samp_start, 2nd for samp_end
outlets = 1; // Output 1024-sample list

// Initialize buffer
function initBuffer() {
    buffer = new Buffer("waveformshifter");
}

// Function for first inlet (sample start)
function msg_int(value) {
    if (!buffer) initBuffer(); // Ensure buffer is initialized

    if (inlet === 0) { 
        samp_start = value;
    } else if (inlet === 1) { 
        samp_end = value;
    }
    
    processBuffer();
}

// Function for a list input (alternative)
function list(start, end) {
    if (!buffer) initBuffer(); // Ensure buffer is initialized

    samp_start = start;
    samp_end = end;
    
    processBuffer();
}

function processBuffer() {
    if (!buffer) {
        post("Buffer not found\n");
        return;
    }

    var bufferSize = buffer.framecount(); // Get buffer size dynamically
    if (bufferSize <= 0) {
        post("Buffer is empty or not loaded yet\n");
        return;
    }

    // Ensure valid sample range
    samp_start = Math.max(0, Math.min(samp_start, bufferSize - 1));
    samp_end = Math.max(samp_start, Math.min(samp_end, bufferSize - 1));

    var rangeSize = samp_end - samp_start + 1;
    var scaledData = new Array(1024);
    var step = (rangeSize - 1) / 1023; // Interpolation step

    for (var i = 0; i < 1024; i++) {
        var index = samp_start + i * step;
        var leftIndex = Math.floor(index);
        var rightIndex = Math.ceil(index);

        if (leftIndex === rightIndex || rightIndex >= bufferSize) {
            scaledData[i] = buffer.peek(1, leftIndex); // No interpolation needed
        } else {
            var leftValue = buffer.peek(1, leftIndex);
            var rightValue = buffer.peek(1, rightIndex);
            var weight = index - leftIndex;
            scaledData[i] = leftValue * (1 - weight) + rightValue * weight; // Linear interpolation
        }
    }

    outlet(0, scaledData); // Output the 1024-sample list
}
