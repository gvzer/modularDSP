var bufferName = "transfervalues"; // Match your buffer~ name
var buffer = null;
var storedList = []; // Stores incoming list

// Function to initialize or refresh the buffer
function initBuffer() {
    buffer = new Buffer(bufferName);
    var frameCount = buffer.framecount();

    if (!buffer || frameCount <= 0) {
        //post("❌ Error: buffer~ '" + bufferName + "' not found or invalid framecount!\n");
        return false; // Buffer not ready
    } else {
        //post("✅ Buffer initialized: " + frameCount + " samples.\n");
        return true; // Buffer is ready
    }
}

// First inlet: receives a list from multislider
function list() {
    storedList = arrayfromargs(arguments); // Store received list
    //post("📥 Received list with " + storedList.length + " values\n");
}

// Second inlet: bang triggers writing into buffer~
function bang() {
    if (!buffer || buffer.framecount() <= 0) {
        //post("⚠️ Buffer not ready. Refreshing...\n");
        if (!initBuffer()) {
            //post("❌ Could not initialize buffer. Check if 'buffer~ transfervalues' exists!\n");
            return;
        }
    }

    if (storedList.length === 0) {
        //post("⚠️ No data stored. Send a list first.\n");
        return;
    }

    var bufferLength = buffer.framecount();
    var numSamples = Math.min(storedList.length, bufferLength); // Ensure safe range

    //post("✍️ Writing " + numSamples + " samples to buffer~ '" + bufferName + "'\n");

    for (var i = 0; i < numSamples; i++) {
        buffer.poke(1, i, storedList[i]); // Write values into buffer
    }

    buffer.send(); // Ensure Max updates the buffer
    //post("✅ Buffer '" + bufferName + "' updated.\n");
}

// Define two inlets:
// - Inlet 1 (list input from multislider)
// - Inlet 2 (bang to trigger writing)
inlets = 2;

// Force manual buffer initialization
function refreshBuffer() {
    if (initBuffer()) {
        //post("🔄 Manual buffer refresh successful.\n");
    } else {
        //post("❌ Manual buffer refresh failed! Ensure 'buffer~ transfervalues' exists in Max.\n");
    }
}

// Create a new function to allow manual refresh from Max
function refresh() {
    refreshBuffer();
}