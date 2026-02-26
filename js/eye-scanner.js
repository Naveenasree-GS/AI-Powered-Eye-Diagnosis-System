// Eye Scanner Module - Real-time Eye Tracking & Scanning
let isEyeScanningActive = false;
let eyeTrackingData = {
    gazeX: 0,
    gazeY: 0,
    confidence: 0,
    predictions: [],
    scanProgress: 0,
    isComplete: false
};

let scanInterval = null;
let drawInterval = null;

// Initialize WebGazer and start eye tracking
async function startEyeScanning() {
    const statusText = document.getElementById('statusText');

    if (isEyeScanningActive) {
        showNotification('⚠️ Eye scanning already active', 'info');
        return;
    }

    // 1. Basic Requirement Checks
    if (typeof webgazer === 'undefined') {
        statusText.innerHTML = '<span style="color:#f44336">Library Error</span><br><small>WebGazer not loaded. Check internet.</small>';
        showNotification('❌ Eye Tracking Library (WebGazer) failed to load.', 'error');
        return;
    }

    if (!window.isSecureContext && window.location.hostname !== 'localhost') {
        showNotification('❌ Camera access requires a Secure Context (HTTPS or localhost).', 'error');
        return;
    }

    statusText.textContent = 'Starting Camera...';
    statusText.style.color = '#ff9800';

    try {
        console.log('🚀 Initializing WebGazer...');

        // Use standard WebGazer initialization
        // We call begin() directly without manual getUserMedia to prevent double-locking the device
        await webgazer.setRegression('ridge')
            .setTrackingModule('clmtrackr')
            .begin();

        console.log('✅ WebGazer Started');

        // Configure WebGazer visualization
        webgazer.showVideoPreview(true)
            .showPredictionPoints(true)
            .applyKalmanFilter(true);

        isEyeScanningActive = true;
        statusText.textContent = 'Scanning...';
        statusText.style.color = '#4caf50';

        // Reset tracking data
        eyeTrackingData.scanProgress = 0;
        eyeTrackingData.isComplete = false;
        eyeTrackingData.predictions = [];

        // Start internal logic loops
        trackGazePosition();
        startVisualization();
        monitorEyeData();
        addScanningOverlay();

        showNotification('👁️ Eye Tracker Ready!', 'success');

    } catch (err) {
        console.error('WebGazer Boot Error:', err);

        let errorMsg = 'Failed to access camera';
        let advice = 'Check browser settings';

        if (err.name === 'NotAllowedError' || err.name === 'PermissionDeniedError') {
            errorMsg = 'Access Denied';
            advice = 'Allow camera in browser settings (Lock icon in URL bar)';
        } else if (err.name === 'NotFoundError' || err.name === 'DevicesNotFoundError') {
            errorMsg = 'No Camera Found';
            advice = 'Connect a webcam';
        } else if (err.name === 'NotReadableError' || err.name === 'TrackStartError') {
            errorMsg = 'Camera Busy';
            advice = 'CLOSE ALL OTHER TABS of this app and other camera apps';
        }

        statusText.innerHTML = `<span style="color:#f44336; font-weight:bold;">${errorMsg}</span><br><small style="font-size:0.75rem">${advice}</small>`;
        showNotification(`❌ ${errorMsg}: ${advice}`, 'error');
    }
}

// Stop eye scanning
function stopEyeScanning() {
    const statusText = document.getElementById('statusText');

    if (!isEyeScanningActive) {
        showNotification('⚠️ Scanner is not active', 'info');
        return;
    }

    // Stop WebGazer
    webgazer.pause();
    webgazer.showVideoPreview(false).showPredictionPoints(false);

    // Stop loops
    clearInterval(scanInterval);
    clearInterval(drawInterval);

    // Remove scanning overlay
    const overlay = document.getElementById('scanOverlay');
    if (overlay) overlay.remove();

    isEyeScanningActive = false;
    statusText.textContent = 'Stopped';
    statusText.style.color = '#757575';

    const progContainer = document.getElementById('scanProgressContainer');
    if (progContainer) progContainer.style.display = 'none';

    document.getElementById('gazeText').textContent = 'Not tracking';
    document.getElementById('confidenceText').textContent = '0%';
    document.getElementById('signalBar').style.width = '0%';

    showNotification('⏹ Scanner Stopped', 'info');
}

// Calibrate eyes for better accuracy
function calibrateEyes() {
    if (!isEyeScanningActive) {
        showNotification('▶ Start eye scanning first', 'error');
        return;
    }

    const statusText = document.getElementById('statusText');
    statusText.textContent = 'Calibrating...';
    statusText.style.color = '#2196f3';

    showCalibrationPoints();
}

// Show calibration points
function showCalibrationPoints() {
    const calibrationDiv = document.createElement('div');
    calibrationDiv.id = 'calibrationOverlay';
    calibrationDiv.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(0, 0, 0, 0.85);
        z-index: 99999;
    `;

    const instructions = document.createElement('div');
    instructions.style.cssText = `
        color: white;
        text-align: center;
        padding-top: 20px;
        font-size: 1.5rem;
    `;
    instructions.textContent = 'Click and Look at each orange dot to calibrate';
    calibrationDiv.appendChild(instructions);

    document.body.appendChild(calibrationDiv);

    const positions = [
        { x: '10%', y: '10%' }, { x: '50%', y: '10%' }, { x: '90%', y: '10%' },
        { x: '10%', y: '50%' }, { x: '50%', y: '50%' }, { x: '90%', y: '50%' },
        { x: '10%', y: '90%' }, { x: '50%', y: '90%' }, { x: '90%', y: '90%' }
    ];

    let clickedCount = 0;

    positions.forEach(pos => {
        const dot = document.createElement('div');
        dot.className = 'calibration-dot';
        dot.style.cssText = `
            position: absolute;
            width: 25px;
            height: 25px;
            background: #ff5722;
            border-radius: 50%;
            left: ${pos.x};
            top: ${pos.y};
            cursor: pointer;
            box-shadow: 0 0 15px #ff5722;
            transition: transform 0.2s;
        `;

        dot.onclick = () => {
            dot.style.background = '#4caf50';
            dot.style.transform = 'scale(0.5)';
            dot.style.pointerEvents = 'none';
            clickedCount++;

            if (clickedCount >= positions.length) {
                setTimeout(() => {
                    calibrationDiv.remove();
                    showNotification('✅ Calibration Complete', 'success');
                    document.getElementById('statusText').textContent = 'Calibrated';
                }, 500);
            }
        };

        calibrationDiv.appendChild(dot);
    });
}

// Track real-time gaze position
function trackGazePosition() {
    scanInterval = setInterval(() => {
        if (!isEyeScanningActive) return;

        const prediction = webgazer.getCurrentPrediction();
        if (prediction) {
            eyeTrackingData.gazeX = Math.round(prediction.x);
            eyeTrackingData.gazeY = Math.round(prediction.y);
            eyeTrackingData.predictions.push(prediction);

            // Estimate confidence based on prediction continuity
            if (eyeTrackingData.predictions.length > 1) {
                const last = eyeTrackingData.predictions[eyeTrackingData.predictions.length - 2];
                const dist = Math.sqrt(Math.pow(prediction.x - last.x, 2) + Math.pow(prediction.y - last.y, 2));
                // Lower distance = higher stability/confidence
                const currentConf = Math.max(0, Math.min(1, 1 - (dist / 500)));
                eyeTrackingData.confidence = (eyeTrackingData.confidence * 0.8) + (currentConf * 0.2);
            } else {
                eyeTrackingData.confidence = 0.5;
            }

            // Keep only last 50 predictions
            if (eyeTrackingData.predictions.length > 50) {
                eyeTrackingData.predictions.shift();
            }

            // Update scan progress if confidence is good
            if (eyeTrackingData.confidence > 0.6 && !eyeTrackingData.isComplete) {
                eyeTrackingData.scanProgress += 0.5;
                if (eyeTrackingData.scanProgress >= 100) {
                    eyeTrackingData.scanProgress = 100;
                    eyeTrackingData.isComplete = true;
                    handleScanCompletion();
                }
            }
        }
    }, 50);
}

// Start visual drawing
function startVisualization() {
    const canvas = document.getElementById('gazeCanvas');
    if (!canvas) return;

    // Make canvas visible and position it over video
    canvas.style.display = 'block';
    canvas.style.position = 'absolute';
    canvas.style.top = '0';
    canvas.style.left = '0';
    canvas.style.zIndex = '5';
    canvas.style.pointerEvents = 'none';

    const ctx = canvas.getContext('2d');

    drawInterval = setInterval(() => {
        if (!isEyeScanningActive) return;

        ctx.clearRect(0, 0, canvas.width, canvas.height);

        // Draw real-time gaze marker (translated to local video coordinates roughly)
        const video = document.getElementById('eyeScanner');
        const rect = video.getBoundingClientRect();

        // This is a rough mapping of screen gaze to video local coords
        const localX = ((eyeTrackingData.gazeX - rect.left) / rect.width) * canvas.width;
        const localY = ((eyeTrackingData.gazeY - rect.top) / rect.height) * canvas.height;

        if (localX >= 0 && localX <= canvas.width && localY >= 0 && localY <= canvas.height) {
            // Draw crosshair
            ctx.strokeStyle = '#00ff00';
            ctx.lineWidth = 2;

            ctx.beginPath();
            ctx.moveTo(localX - 15, localY);
            ctx.lineTo(localX + 15, localY);
            ctx.stroke();

            ctx.beginPath();
            ctx.moveTo(localX, localY - 15);
            ctx.lineTo(localX, localY + 15);
            ctx.stroke();

            // Draw target circle
            ctx.beginPath();
            ctx.arc(localX, localY, 8, 0, Math.PI * 2);
            ctx.stroke();

            // Draw path history
            ctx.beginPath();
            ctx.strokeStyle = 'rgba(0, 255, 0, 0.3)';
            ctx.lineWidth = 1;
            for (let i = 0; i < eyeTrackingData.predictions.length; i++) {
                const p = eyeTrackingData.predictions[i];
                const px = ((p.x - rect.left) / rect.width) * canvas.width;
                const py = ((p.y - rect.top) / rect.height) * canvas.height;
                if (i === 0) ctx.moveTo(px, py);
                else ctx.lineTo(px, py);
            }
            ctx.stroke();
        }
    }, 30);
}

// Monitor and display eye tracking data
function monitorEyeData() {
    const progContainer = document.getElementById('scanProgressContainer');
    const progBar = document.getElementById('scanProgressBar');
    const progPercent = document.getElementById('progressPercent');

    if (progContainer) progContainer.style.display = 'block';

    setInterval(() => {
        if (!isEyeScanningActive) return;

        const gazeText = document.getElementById('gazeText');
        const confidenceText = document.getElementById('confidenceText');
        const statusText = document.getElementById('statusText');

        gazeText.textContent = `X: ${eyeTrackingData.gazeX}, Y: ${eyeTrackingData.gazeY}`;

        // Calculate confidence (0-100%)
        const confidenceValue = Math.round(eyeTrackingData.confidence * 100);
        confidenceText.textContent = `${confidenceValue}%`;

        // Update signal bar
        const signalBar = document.getElementById('signalBar');
        if (signalBar) {
            signalBar.style.width = `${confidenceValue}%`;
            signalBar.style.backgroundColor = confidenceValue > 70 ? '#4caf50' : (confidenceValue > 40 ? '#ff9800' : '#f44336');
        }

        // Update progress bar
        if (progBar && progPercent) {
            const roundedProg = Math.round(eyeTrackingData.scanProgress);
            progBar.style.width = `${roundedProg}%`;
            progPercent.textContent = `${roundedProg}%`;
        }

        // Update status if scanning
        if (!eyeTrackingData.isComplete) {
            statusText.textContent = `Scanning (${Math.round(eyeTrackingData.scanProgress)}%)`;
        } else {
            statusText.textContent = 'Scan Complete';
            statusText.style.color = '#4caf50';
            if (progBar) progBar.style.background = '#4caf50';
        }

        analyzeEyePatterns();
    }, 200);
}

function handleScanCompletion() {
    showNotification('✅ Advanced Eye Scan Complete!', 'success');

    // Auto-save to app state
    if (window.app) {
        window.app.scanResults = getEyeScanResults();
    }

    // Play a subtle sound or visual cue if needed
    console.log('🏁 Full Scan Profile Generated');
}

// Analyze eye tracking patterns for anomalies
function analyzeEyePatterns() {
    if (eyeTrackingData.predictions.length < 10) return;

    const recentPredictions = eyeTrackingData.predictions.slice(-10);

    // Calculate stability
    const xValues = recentPredictions.map(p => p.x);
    const yValues = recentPredictions.map(p => p.y);

    const xVariance = calculateVariance(xValues);
    const yVariance = calculateVariance(yValues);

    const stability = Math.max(0, 100 - (xVariance + yVariance) / 10);

    eyeTrackingData.analysis = {
        stability: stability,
        xVariance: xVariance.toFixed(2),
        yVariance: yVariance.toFixed(2),
        movementSpeed: calculateMovementSpeed(recentPredictions).toFixed(2),
        pupilStatus: stability > 80 ? 'Normal' : 'Jittery'
    };
}

// Calculate variance
function calculateVariance(values) {
    if (values.length === 0) return 0;
    const mean = values.reduce((a, b) => a + b) / values.length;
    return values.reduce((acc, val) => acc + Math.pow(val - mean, 2), 0) / values.length;
}

// Calculate movement speed
function calculateMovementSpeed(predictions) {
    if (predictions.length < 2) return 0;
    let totalDistance = 0;
    for (let i = 1; i < predictions.length; i++) {
        totalDistance += Math.sqrt(Math.pow(predictions[i].x - predictions[i - 1].x, 2) + Math.pow(predictions[i].y - predictions[i - 1].y, 2));
    }
    return totalDistance / predictions.length;
}

// Get eye scan results for medical analysis
function getEyeScanResults() {
    return {
        timestamp: new Date().toISOString(),
        gazePosition: {
            x: eyeTrackingData.gazeX,
            y: eyeTrackingData.gazeY
        },
        confidence: Math.round(eyeTrackingData.confidence * 100),
        analysis: eyeTrackingData.analysis || {},
        isComplete: eyeTrackingData.isComplete,
        scanHealth: eyeTrackingData.confidence > 0.8 ? 'Excellent' : (eyeTrackingData.confidence > 0.5 ? 'Good' : 'Poor')
    };
}

// Add a visual scanning line overlay to the video
function addScanningOverlay() {
    const video = document.getElementById('eyeScanner');
    if (!video) return;

    let wrapper = video.closest('.video-wrapper');
    if (!wrapper) {
        wrapper = document.createElement('div');
        wrapper.className = 'video-wrapper';
        wrapper.style.position = 'relative';
        video.parentNode.insertBefore(wrapper, video);
        wrapper.appendChild(video);

        // Re-append canvas to same wrapper
        const canvas = document.getElementById('gazeCanvas');
        if (canvas) wrapper.appendChild(canvas);
    }

    if (!document.getElementById('scanOverlay')) {
        const overlay = document.createElement('div');
        overlay.id = 'scanOverlay';
        overlay.className = 'scanner-overlay';
        wrapper.appendChild(overlay);
    }
}

// Initialize eye scanner on page load
document.addEventListener('DOMContentLoaded', function () {
    console.log('✅ Advanced Eye Scanner Module Ready');
});

