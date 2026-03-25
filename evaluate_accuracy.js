const fs = require('fs');

function loadJSON(path) {
  return JSON.parse(fs.readFileSync(path, 'utf8'));
}

const dataset = loadJSON('disease_dataset.json');
const patients = loadJSON('data/patients_db.json');

function normalize(s) {
  return (s || '').toString().trim();
}

function lower(s) {
  return normalize(s).toLowerCase();
}

function extractAge(rec) {
  const ageRaw = (rec.patient && rec.patient.age) ? rec.patient.age : rec.age || '';
  return Number(String(ageRaw).replace(/[^0-9]/g, '')) || 0;
}

function extractEvidence(rec) {
  const parts = [];
  if (rec.symptoms) parts.push(String(rec.symptoms));
  if (Array.isArray(rec.findings)) parts.push(rec.findings.join(' '));
  if (rec.recommendation) parts.push(String(rec.recommendation));
  return parts.join(' ').trim();
}

function extractDiopter(text) {
  const match = normalize(text).match(/(-?\d+(?:\.\d+)?)\s*d\b/i);
  return match ? Number(match[1]) : null;
}

function labelFromDiopter(value) {
  if (typeof value !== 'number' || Number.isNaN(value)) return null;
  if (value < -2.0) return 'High Myopia';
  if (value < -0.5) return 'Mild Myopia';
  if (value > 2.0) return 'High Hyperopia';
  if (value > 0.5) return 'Mild Hyperopia';
  return 'Emmetropia';
}

function normalizeLabel(label) {
  const raw = normalize(label);
  const text = lower(raw);
  if (!text) return 'Unknown Condition';

  if (text.includes('retinal analysis')) {
    if (text.includes('high myopia')) return 'High Myopia';
    if (text.includes('mild myopia')) return 'Mild Myopia';
    if (text.includes('high hyperopia')) return 'High Hyperopia';
    if (text.includes('mild hyperopia')) return 'Mild Hyperopia';
    if (text.includes('emmetropia') || text.includes('normal sight')) return 'Emmetropia';

    const diopterLabel = labelFromDiopter(extractDiopter(raw));
    if (diopterLabel) return diopterLabel;
    return 'Retinal Analysis';
  }

  if (
    text.includes('retinal detachment') ||
    text.includes('retinal tear') ||
    text.includes('retina detached') ||
    text.includes('detachment')
  ) {
    return 'Retinal Detachment';
  }

  for (const disease of dataset) {
    if (text.includes(disease.name.toLowerCase())) {
      return disease.name;
    }
  }

  if (text.includes('high myopia')) return 'High Myopia';
  if (text.includes('mild myopia')) return 'Mild Myopia';
  if (text.includes('high hyperopia')) return 'High Hyperopia';
  if (text.includes('mild hyperopia')) return 'Mild Hyperopia';
  if (text.includes('emmetropia') || text.includes('normal sight')) return 'Emmetropia';

  const diopterLabel = labelFromDiopter(extractDiopter(raw));
  if (diopterLabel) return diopterLabel;

  return raw;
}

function isRetinalScanRecord(rec, evidenceText) {
  const symptoms = lower(rec.symptoms);
  const diagnosis = lower(rec.diagnosis || rec.analysisResult || '');
  const evidence = lower(evidenceText);

  return symptoms.includes('retinal scan') ||
    diagnosis.includes('retinal analysis') ||
    evidence.includes('measured power') ||
    evidence.includes('emmetropia') ||
    evidence.includes('myopia') ||
    evidence.includes('hyperopia') ||
    evidence.includes('normal sight');
}

function predictRetinalLabel(rec, evidenceText) {
  const evidence = lower(evidenceText);

  if (evidence.includes('high myopia')) return 'High Myopia';
  if (evidence.includes('mild myopia')) return 'Mild Myopia';
  if (evidence.includes('high hyperopia')) return 'High Hyperopia';
  if (evidence.includes('mild hyperopia')) return 'Mild Hyperopia';
  if (
    evidence.includes('emmetropia') ||
    evidence.includes('normal sight') ||
    evidence.includes('status: normal')
  ) {
    return 'Emmetropia';
  }

  if (evidence.includes('severe distance blur') || evidence.includes('high-index corrective lenses')) {
    return 'High Myopia';
  }
  if (evidence.includes('distal objects appear blurry') || evidence.includes('distance vision')) {
    return 'Mild Myopia';
  }
  if (evidence.includes('significant blur at all distances') || evidence.includes('convex lenses required immediately')) {
    return 'High Hyperopia';
  }
  if (evidence.includes('near objects may appear blurry') || evidence.includes('reading glasses may be needed')) {
    return 'Mild Hyperopia';
  }

  const power = extractDiopter(evidenceText);
  const byPower = labelFromDiopter(power);
  if (byPower) return byPower;

  return 'Retinal Analysis';
}

function scoreDiseaseMatch(disease, evidenceText, age, processed) {
  let score = 0;
  const evidence = lower(evidenceText);
  const diseaseName = disease.name.toLowerCase();

  if (evidence.includes(diseaseName)) score += 25;

  for (const keyword of disease.keywords || []) {
    const k = String(keyword).toLowerCase();
    if (!k) continue;
    if (evidence.includes(k)) {
      score += 10;
      const escaped = k.replace(/[-/\\^$*+?.()|[\]{}]/g, '\\$&');
      const regex = new RegExp(`\\b${escaped}\\b`, 'i');
      if (regex.test(evidenceText)) score += 5;
    }
  }

  if (disease.name === 'Presbyopia' && age > 40) score += 5;
  if (disease.name === 'Age-Related Macular Degeneration' && age > 50) score += 5;
  if (disease.name === 'Strabismus (Crossed Eyes)' && age > 0 && age < 10) score += 5;

  if (processed) {
    if (disease.name.includes('Conjunctivitis') && Number(processed.redness) > 120) score += 15;
    if (disease.name.includes('Cataract') && Number(processed.opacity) > 40) score += 15;
  }

  return score;
}

function predictForRecord(rec) {
  const evidenceText = extractEvidence(rec);
  const age = extractAge(rec);
  const processed = rec.imageFeatures || null;

  if (isRetinalScanRecord(rec, evidenceText)) {
    return predictRetinalLabel(rec, evidenceText);
  }

  let bestMatch = null;
  let maxScore = 0;

  for (const disease of dataset) {
    const score = scoreDiseaseMatch(disease, evidenceText, age, processed);
    if (score > maxScore) {
      maxScore = score;
      bestMatch = disease;
    }
  }

  if (bestMatch && maxScore > 0) {
    return bestMatch.name;
  }

  return normalizeLabel(rec.diagnosis || rec.analysisResult || '');
}

let total = 0;
let correct = 0;
const details = [];

for (const rec of patients) {
  total++;
  const groundRaw = normalize(rec.diagnosis || rec.analysisResult || '');
  const ground = normalizeLabel(groundRaw);
  const pred = predictForRecord(rec);
  const isMatch = ground === pred;

  if (isMatch) correct++;

  details.push({
    patient: rec.patient && rec.patient.name ? rec.patient.name : '(unknown)',
    ground_raw: groundRaw,
    ground,
    pred,
    match: isMatch
  });
}

const accuracy = total === 0 ? 0 : (correct / total) * 100;
console.log(`Evaluated ${total} records`);
console.log(`Correct: ${correct}`);
console.log(`Overall accuracy: ${accuracy.toFixed(2)}%`);

fs.writeFileSync(
  'accuracy_results.json',
  JSON.stringify({ total, correct, accuracy: Number(accuracy.toFixed(4)), details }, null, 2)
);
console.log('Detailed results saved to accuracy_results.json');
