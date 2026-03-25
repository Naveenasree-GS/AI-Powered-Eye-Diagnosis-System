const fs = require('fs');

function loadJSON(path) {
  return JSON.parse(fs.readFileSync(path, 'utf8'));
}

const dataset = loadJSON('disease_dataset.json');
const patientsPath = 'data/patients_db.json';
const patients = loadJSON(patientsPath);

function normalize(s) {
  return (s || '').toString().trim();
}

function lower(s) {
  return normalize(s).toLowerCase();
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

function findMapping(diagnosis) {
  const raw = normalize(diagnosis);
  const d = lower(diagnosis);
  if (!d) return { mapped: 'Unknown', reason: 'empty' };

  // 1) Retina-analysis labels must be normalized before generic disease keyword matching.
  // Otherwise words like "retina" incorrectly map to "Retinal Detachment".
  if (d.includes('retinal analysis')) {
    // Try to capture structured label after the colon, e.g. "Retinal Analysis: EMMETROPIA (Normal Sight) (-0.30 D)"
    const after = raw.split(/:\s*/i).slice(1).join(':').trim();
    if (after) {
      const token = after.split(/\s|\(|\-/)[0].toLowerCase();
      if (token.includes('emmet') || after.toLowerCase().includes('normal sight')) return { mapped: 'Emmetropia', reason: 'retinal_analysis_emmetropia_token' };
      if (token.includes('myop')) return { mapped: 'Mild Myopia', reason: 'retinal_analysis_myopia_token' };
      if (token.includes('hyper')) return { mapped: 'Mild Hyperopia', reason: 'retinal_analysis_hyperopia_token' };
    }

    // Fallback: parse numeric diopter values and map by thresholds
    const diopterLabel = labelFromDiopter(extractDiopter(raw));
    if (diopterLabel) return { mapped: diopterLabel, reason: 'retinal_analysis_diopter' };

    // If nothing matches, leave as 'Retinal Analysis' to avoid dangerous disease mapping
    return { mapped: 'Retinal Analysis', reason: 'retinal_analysis_unknown' };
  }

  // 2) explicit retinal-detachment language outside the retinal power scan flow
  if (
    d.includes('retinal detachment') ||
    d.includes('retinal tear') ||
    d.includes('retina detached') ||
    d.includes('detachment')
  ) {
    return { mapped: 'Retinal Detachment', reason: 'retina_detachment_keyword' };
  }

  // 3) direct disease name substring
  for (const dis of dataset) {
    const name = dis.name.toLowerCase();
    if (d.includes(name)) return { mapped: dis.name, reason: 'name_substring' };
  }

  // 4) common refractive-error keywords that are not part of disease_dataset.json
  if (d.includes('high myopia')) return { mapped: 'High Myopia', reason: 'text_high_myopia' };
  if (d.includes('mild myopia')) return { mapped: 'Mild Myopia', reason: 'text_mild_myopia' };
  if (d.includes('high hyperopia')) return { mapped: 'High Hyperopia', reason: 'text_high_hyperopia' };
  if (d.includes('mild hyperopia')) return { mapped: 'Mild Hyperopia', reason: 'text_mild_hyperopia' };
  if (d.includes('emmetropia') || d.includes('normal sight')) return { mapped: 'Emmetropia', reason: 'text_emmetropia' };

  const diopterLabel = labelFromDiopter(extractDiopter(raw));
  if (diopterLabel) return { mapped: diopterLabel, reason: 'diopter_value' };

  // 5) match by disease keywords
  for (const dis of dataset) {
    for (const kw of dis.keywords || []) {
      const key = kw.toLowerCase();
      if (key === 'retina' && d.includes('retinal analysis')) continue;
      if (d.includes(key)) return { mapped: dis.name, reason: `keyword:${kw}` };
    }
  }

  // 6) generic AI or analysis messages
  if (d.includes('ai') || d.includes('analysis') || d.includes('complete')) return { mapped: 'Unknown', reason: 'ai_generic' };

  // fallback: no mapping
  return { mapped: diagnosis, reason: 'no_change' };
}

// Build unique diagnosis map
const diagCounts = {};
for (const rec of patients) {
  const diag = normalize(rec.diagnosis || rec.analysisResult || '');
  diagCounts[diag] = (diagCounts[diag] || 0) + 1;
}

const unique = Object.keys(diagCounts).sort((a,b)=>diagCounts[b]-diagCounts[a]);

const mappingReport = {};
unique.forEach(d => {
  const map = findMapping(d);
  mappingReport[d] = { count: diagCounts[d], mapped: map.mapped, reason: map.reason };
});

// Apply mapping to create cleaned records
const cleaned = patients.map(rec => {
  const original = normalize(rec.diagnosis || rec.analysisResult || '');
  const mapping = findMapping(original);
  const cleanedDiag = mapping.mapped;
  return Object.assign({}, rec, { diagnosis_clean: cleanedDiag, diagnosis_mapping_reason: mapping.reason });
});

// Save outputs
fs.writeFileSync('data/patients_db_clean.json', JSON.stringify(cleaned, null, 2));
fs.writeFileSync('data/diagnosis_mapping_report.json', JSON.stringify(mappingReport, null, 2));

console.log('Audit complete');
console.log('Unique diagnoses found:', unique.length);
console.log('Saved: data/patients_db_clean.json and data/diagnosis_mapping_report.json');

// Print summary table
console.log('\nSummary (diagnosis -> mapped)');
for (const d of unique) {
  const r = mappingReport[d];
  console.log(`${d}  ->  ${r.mapped}   (count=${r.count}, reason=${r.reason})`);
}
