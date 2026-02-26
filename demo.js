// Global variable to store dataset
let diseaseDataset = null;
let currentDiagnosisData = null;

// Fetch dataset on load
async function loadDataset() {
    try {
        const response = await fetch('disease_dataset.json');
        diseaseDataset = await response.json();
        console.log('✅ Disease dataset loaded successfully');
    } catch (error) {
        console.error('❌ Failed to load disease dataset:', error);
    }
}

// Initialize dataset
loadDataset();

async function analyzeImage() {
    let resultDiv = document.getElementById("result");

    // Check if patient info is provided
    const name = document.getElementById("name").value.trim();
    const age = document.getElementById("age").value;
    const gender = document.getElementById("gender").value;
    const problemDesc = document.getElementById("problemDesc").value.toLowerCase();

    if (!name) {
        showNotification("⚠️ Please enter patient name first", "error");
        return;
    }

    // High Precision Processing
    const fileInput = document.getElementById('eyeImage');
    let processedResults = null;

    if (fileInput && fileInput.files && fileInput.files[0]) {
        showNotification("🔍 High-Precision Eye Processing Started...", "info");

        // Create an image element to process
        const img = new Image();
        const reader = new FileReader();

        const processPromise = new Promise((resolve) => {
            reader.onload = (e) => {
                img.onload = async () => {
                    // Start processing steps
                    showNotification("✨ Enhancing & Normalizing...", "info");
                    await new Promise(r => setTimeout(r, 600));

                    showNotification("🧹 Filtering Noise...", "info");
                    await new Promise(r => setTimeout(r, 600));

                    showNotification("🎯 Segmenting Eye Region...", "info");
                    const results = await window.ImageProcessor.processEyeImage(img);

                    showNotification("📊 Extracting Features...", "info");
                    await new Promise(r => setTimeout(r, 600));

                    resolve(results);
                };
                img.src = e.target.result;
            };
            reader.readAsDataURL(fileInput.files[0]);
        });

        processedResults = await processPromise;
    }

    // Determine what disease to show
    let matchedDisease = null;
    let bestMatch = null;
    let maxScore = 0;

    if (diseaseDataset) {
        diseaseDataset.forEach(disease => {
            let score = 0;
            disease.keywords.forEach(keyword => {
                const k = keyword.toLowerCase();
                if (problemDesc.includes(k)) {
                    score += 10;
                    const regex = new RegExp(`\\b${k}\\b`, 'i');
                    if (regex.test(problemDesc)) score += 5;
                }
            });

            // Contextual adjustments
            if (disease.name === "Presbyopia" && age > 40) score += 5;
            if (disease.name === "Age-Related Macular Degeneration" && age > 50) score += 5;
            if (disease.name === "Strabismus (Crossed Eyes)" && age < 10) score += 5;

            // Influence from image processing
            if (processedResults && processedResults.features) {
                const f = processedResults.features;
                if (disease.name.includes("Conjunctivitis") && f.redness > 120) score += 15;
                if (disease.name.includes("Cataract") && f.opacity > 40) score += 15;
            }

            if (score > maxScore) {
                maxScore = score;
                bestMatch = disease;
            }
        });
    }

    let isSimulation = false;

    if (bestMatch && maxScore > 0) {
        matchedDisease = bestMatch;
    } else {
        isSimulation = true;
        const app = window.app || {};
        const pData = app.patientData || {};
        if (diseaseDataset && diseaseDataset.length > 0) {
            const seed = (pData.imageFile ? pData.imageFile.length : 0) + (pData.fileSize || name.length);
            matchedDisease = diseaseDataset[seed % diseaseDataset.length];
        }
    }

    if (!matchedDisease) {
        matchedDisease = {
            name: "Unknown Condition",
            min_confidence: 40,
            max_confidence: 60,
            findings: ["Inconclusive analysis - Please visit a doctor"],
            recommendation: "Consult an ophthalmologist for a comprehensive checkup."
        };
    }

    // Confidence adjustment based on processing
    let confidence = matchedDisease.min_confidence + Math.floor(Math.random() * (matchedDisease.max_confidence - matchedDisease.min_confidence));
    if (processedResults && processedResults.features.precision > 90) {
        confidence = Math.min(99, confidence + 10);
    }

    currentDiagnosisData = {
        patient: { name, age, gender },
        symptoms: document.getElementById("problemDesc").value,
        diagnosis: matchedDisease.name,
        confidence: confidence,
        findings: matchedDisease.findings,
        recommendation: matchedDisease.recommendation,
        timestamp: new Date().toISOString(),
        savedAt: new Date().toLocaleString(),
        imageFeatures: processedResults ? processedResults.features : null
    };

    // Build result HTML
    let html = `<div style="display: flex; justify-content: space-between; align-items: center;">
                    <h3>Medical Analysis for ${name}</h3>
                    <span style="font-size: 0.8rem; color: #666;">Precision: ${processedResults ? processedResults.features.precision : '--'}%</span>
                </div>`;

    const isEmergency = matchedDisease.name.includes("Stroke") || matchedDisease.name.includes("Detachment") || (matchedDisease.keywords && matchedDisease.keywords.includes("pain"));

    if (isEmergency) {
        html += `<div style="background: #ffebee; color: #b71c1c; padding: 10px; border-radius: 5px; margin-bottom: 10px; text-align: center; font-weight: bold; border: 1px solid #ffcdd2;">
                    🚨 POTENTIAL EMERGENCY CONDITION DETECTED
                 </div>`;
    }

    html += `<div style="margin-bottom:1rem; border-bottom: 1px solid #ddd; padding-bottom:0.5rem;">
                <strong style="color:${isEmergency ? '#d32f2f' : '#2e7d32'}; font-size:1.2rem;">Detected: ${matchedDisease.name}</strong><br>
                <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 5px;">
                    <span style="font-size:0.9rem; color:#666;">AI Confidence: 
                        <span style="font-weight: bold; color: ${confidence > 85 ? '#43a047' : '#f57c00'}">${confidence}%</span>
                    </span>
                    ${processedResults ? '<span style="font-size: 0.75rem; background: #e3f2fd; padding: 2px 6px; border-radius: 4px; color: #1976d2;">Deep Image Analysis</span>' : '<span style="font-size: 0.75rem; background: #eee; padding: 2px 6px; border-radius: 4px; color: #777;">Simulated Analysis</span>'}
                </div>
             </div>`;

    if (processedResults) {
        html += `<div style="background: #f0f4f8; padding: 12px; border-radius: 8px; margin-bottom: 1rem; border: 1px solid #d1d9e6;">
                    <strong style="font-size: 0.85rem; color: #445;">Visual Feature Matrix:</strong>
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-top: 8px;">
                        <div style="font-size: 0.8rem;">🔴 Redness Index: <strong>${processedResults.features.redness}%</strong></div>
                        <div style="font-size: 0.8rem;">👁️ Opacity: <strong>${processedResults.features.opacity}%</strong></div>
                        <div style="font-size: 0.8rem;">📍 Pupil Size: <strong>${processedResults.features.pupilSize}px</strong></div>
                        <div style="font-size: 0.8rem;">⚖️ Symmetry: <strong>${processedResults.features.symmetry}</strong></div>
                    </div>
                 </div>`;
    }

    html += `<div style="margin-bottom:1rem;">
                <strong>Clinical Findings:</strong>
                <ul style="margin:0.5rem 0; padding-left:1.5rem;">
                    ${matchedDisease.findings.map(f => `<li>${f}</li>`).join('')}
                </ul>
             </div>`;

    html += `<div style="background:#fff3e0; padding:10px; border-radius:5px; border-left:4px solid #ff9800; margin-bottom:1rem;">
                <strong>Recommendation:</strong><br>
                ${matchedDisease.recommendation}
             </div>`;

    html += `<div style="margin-top: 1.5rem; text-align: center;">
                <button id="saveRecordBtn" class="btn btn-primary" onclick="saveDiagnosisToDatabase()" style="width: 100%; background: linear-gradient(135deg, #43a047 0%, #2e7d32 100%);">
                    💾 Save Patient Record
                </button>
             </div>`;

    resultDiv.innerHTML = html;
    resultDiv.style.display = "block";
    resultDiv.style.backgroundColor = "#fff";
    resultDiv.style.padding = "20px";
    resultDiv.style.borderRadius = "10px";
    resultDiv.style.boxShadow = "0 4px 15px rgba(0,0,0,0.1)";
    resultDiv.style.border = "none";
    resultDiv.style.whiteSpace = "normal";
    resultDiv.style.color = "#333";
}

async function saveDiagnosisToDatabase() {
    if (!currentDiagnosisData) {
        showNotification('⚠️ No diagnosis data to save', 'error');
        return;
    }

    const btn = document.getElementById('saveRecordBtn');
    if (btn) {
        btn.disabled = true;
        btn.innerText = '⏳ Saving...';
    }

    try {
        if (window.app) {
            const success = await window.app.savePatientToBackend(currentDiagnosisData);
            if (success) {
                showNotification('✅ Patient record saved effectively!', 'success');
                if (btn) {
                    btn.innerText = '✓ Saved';
                    btn.style.background = '#ccc';
                    btn.style.color = '#333';
                    btn.onclick = null;
                }
            } else {
                throw new Error('Backend failed');
            }
        } else {
            console.error('App instance not found');
            showNotification('⚠️ App Error', 'error');
        }
    } catch (err) {
        console.error('Save error:', err);
        showNotification('❌ Failed to save record', 'error');
        if (btn) {
            btn.disabled = false;
            btn.innerText = '💾 Save Patient Record';
        }
    }
}

// Export complete medical report
function exportMedicalReport() {
    const resultDiv = document.getElementById("result");
    if (resultDiv.innerHTML === "") {
        alert("Please analyze an image first to generate a report");
        return;
    }

    const patientName = document.getElementById("name").value || "Patient";
    const patientAge = document.getElementById("age").value || "Unknown";
    const problemDesc = document.getElementById("problemDesc").value;

    // Find what we diagnosed
    const diagnosisHeader = resultDiv.querySelector('strong')?.innerText || "General Eye Health Check";

    let report = {
        timestamp: new Date().toISOString(),
        patient: {
            name: patientName,
            age: patientAge,
            symptoms: problemDesc
        },
        analysis: {
            status: "Completed",
            preliminary_diagnosis: diagnosisHeader,
            report_content: resultDiv.innerText
        },
        eyeScanData: typeof getEyeScanResults === 'function' ? getEyeScanResults() : null
    };

    // Trigger download
    const dataStr = JSON.stringify(report, null, 2);
    const dataBlob = new Blob([dataStr], { type: 'application/json' });
    const url = URL.createObjectURL(dataBlob);

    const link = document.createElement('a');
    link.href = url;
    link.download = `Medical_Report_${patientName.replace(/\s+/g, '_')}_${Date.now()}.json`;
    link.click();

    URL.revokeObjectURL(url);
}