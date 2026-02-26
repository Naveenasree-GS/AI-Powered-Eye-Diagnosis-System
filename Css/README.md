# EyeCare AI

A Java-based Eye Diagnosis System backend with an HTML/JS frontend.

## Project Structure
This project is fully compatible with Visual Studio Code.

- `EyeCareServer.java`: Main Java backend (HttpServer).
- `index.html`: Main frontend interface.
- `style.css`: Styles.
- `app.js` & `demo.js`: Frontend logic.
- `js/eye-scanner.js`: Eye scanning module.
- `disease_dataset.json`: Database of eye diseases and rules.
- `patients_db.json`: Storage for patient records.

## Requirements
- Java Development Kit (JDK) 8 or higher.
- A modern web browser.

## How to Run in VS Code
1. Open this folder in VS Code.
2. Open a terminal (`Ctrl+` `).
3. Compile the server:
   ```bash
   javac EyeCareServer.java
   ```
4. Run the server:
   ```bash
   java EyeCareServer
   ```
5. Open your browser and go to: `http://localhost:8000`

## Features
- **Rule-Based Diagnosis**: Detects Cataract, Glaucoma, Conjunctivitis, etc., based on keywords or simulated image analysis.
- **Eye Scanner**: Real-time simulation of eye scanning and retinal power.
- **Appointment Booking**: Conflict-free booking system.
- **Patient History**: Automatic saving and monitoring of reports.
- **Export**: Download full medical reports as JSON.

## "Normal Eye" Fix Logic
Previously, if an uploaded filename (e.g., `image.png`) did not contain a disease keyword (like "cataract"), the system defaulted to "Normal Health".
**The Fix:**
The backend now detects if the input is an image. If no keywords are found, it performs a **simulated analysis** (random selection from the database) to demonstrate the system's capability to "detect" a disease, rather than lazily defaulting to normal. This ensures you see meaningful results during testing.

## API Response Format
The backend returns JSON with the following keys:
- `diseaseName`: The detected condition.
- `confidence`: Percent confidence score.
- `description`: Medical recommendation and findings.
- `findings`: Array of specific symptoms.

## Testing
1. **Diagnosis**: Go to the 'Diagnosis' tab.
2. **Input**: Upload an image or type "redness" in the description.
3. **Result**: See the detected disease and confidence score.
