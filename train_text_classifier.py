import json
import os
import re
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix
import joblib

# Load cleaned dataset if available
DATA_CLEAN = 'data/patients_db_clean.json'
DATA_RAW = 'data/patients_db.json'

if os.path.exists(DATA_CLEAN):
    data = json.load(open(DATA_CLEAN, 'r', encoding='utf8'))
    print('Loaded cleaned dataset:', DATA_CLEAN)
else:
    data = json.load(open(DATA_RAW, 'r', encoding='utf8'))
    print('Loaded raw dataset:', DATA_RAW)

# Extract text and labels
texts = []
labels = []

for rec in data:
    # prefer cleaned label if present
    label = rec.get('diagnosis_clean') or rec.get('diagnosis') or rec.get('analysisResult') or ''
    label = str(label).strip()
    # prefer symptoms field; fallback to patient.diagnosis or imageFeatures string
    text = ''
    if rec.get('symptoms'):
        text = str(rec.get('symptoms'))
    elif rec.get('patient') and isinstance(rec.get('patient'), dict) and rec['patient'].get('name'):
        text = rec['patient'].get('name')
    elif rec.get('imageFeatures'):
        # stringify numeric features
        try:
            text = ' '.join([f"{k}:{v}" for k, v in rec['imageFeatures'].items()])
        except Exception:
            text = str(rec.get('imageFeatures'))
    else:
        text = rec.get('diagnosis', '')

    text = text.strip()
    if text == '':
        # If text is empty, use the diagnosis string as a fallback context
        text = rec.get('diagnosis', '')

    if label and text:
        texts.append(text)
        labels.append(label)

print('Records with text+label:', len(texts))
if len(texts) < 2:
    raise SystemExit('Not enough labeled records to train a classifier')

# Simple text cleanup function
def clean_text(s):
    s = s.lower()
    s = re.sub(r"[^a-z0-9\s]", ' ', s)
    s = re.sub(r"\s+", ' ', s).strip()
    return s

texts_clean = [clean_text(t) for t in texts]

# Train/test split
X_train, X_test, y_train, y_test = train_test_split(texts_clean, labels, test_size=0.2, random_state=42, stratify=labels if len(set(labels))>1 else None)

# TF-IDF + Logistic Regression pipeline (explicit)
vectorizer = TfidfVectorizer(stop_words='english', max_features=2000, ngram_range=(1,2))
X_train_tfidf = vectorizer.fit_transform(X_train)
X_test_tfidf = vectorizer.transform(X_test)

clf = LogisticRegression(max_iter=2000, class_weight='balanced', solver='liblinear')
clf.fit(X_train_tfidf, y_train)

# Predict and evaluate
y_pred = clf.predict(X_test_tfidf)
acc = accuracy_score(y_test, y_pred)
print(f"Test accuracy: {acc*100:.2f}%")
print('\nClassification report:\n')
print(classification_report(y_test, y_pred, zero_division=0))
print('\nConfusion matrix:\n')
print(confusion_matrix(y_test, y_pred))

# Save model and vectorizer and results
os.makedirs('models', exist_ok=True)
joblib.dump({'vectorizer': vectorizer, 'classifier': clf}, 'models/text_classifier.joblib')
results = {
    'accuracy': float(acc),
    'n_test': len(y_test),
}
with open('models/text_classification_results.json', 'w', encoding='utf8') as f:
    json.dump(results, f, indent=2)

print('\nSaved model to models/text_classifier.joblib and results to models/text_classification_results.json')
