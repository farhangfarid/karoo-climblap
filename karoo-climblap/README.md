# Karoo Climb Auto-Lap

A Hammerhead Karoo 3 extension that automatically marks a lap at the **start and end of every detected climb**.

## How it works

The extension runs silently in the background during a ride. It reads the live gradient (grade) data from the Karoo sensor stream. When the grade rises above a threshold and stays there long enough, it marks a lap (climb start). When grade drops back below the threshold and stays there, it marks another lap (climb end).

## Detection logic

- **Climb starts** when: grade ≥ 3% sustained for 10 seconds
- **Climb ends** when: grade < 2% sustained for 15 seconds (hysteresis prevents false endings on brief flat sections)
- Minimum 30 seconds between laps (debounce, avoids double-firing)

All thresholds are configurable in `ClimbLapService.kt`.

## Project structure

```
app/
  src/main/
    kotlin/com/example/karoo_climblap/
      ClimbLapExtension.kt   ← The KarooExtension service entry point
      ClimbLapService.kt     ← Climb detection + lap-marking logic
      BootReceiver.kt        ← Auto-starts extension when Karoo boots
      MainActivity.kt        ← Simple settings screen
    res/
      xml/extension_info.xml ← Declares the extension to the Karoo system
    AndroidManifest.xml
  build.gradle.kts
build.gradle.kts
settings.gradle.kts
gradle.properties           ← You put your GitHub token here (not committed)
```

## Setup — no Android Studio needed!

The APK is built automatically by GitHub Actions. All you need is a GitHub account.

### Step 1 — Create a GitHub Personal Access Token

1. Go to https://github.com/settings/tokens
2. Click **Generate new token (classic)**
3. Give it any name (e.g. "karoo-ext")
4. Check the **`read:packages`** scope — nothing else needed
5. Click **Generate token** and **copy it** (you won't see it again)

### Step 2 — Create a new GitHub repo and upload the code

1. Go to https://github.com/new and create a **public** repo called `karoo-climblap`
2. Upload all the files from this zip — maintaining the folder structure
   - Easiest way: drag the unzipped folder into the GitHub web UI after creating the repo

### Step 3 — Add your token as a Secret

1. In your new repo, go to **Settings → Secrets and variables → Actions**
2. Click **New repository secret**
3. Name: `KAROO_EXT_TOKEN`
4. Value: paste the token you copied in Step 1
5. Click **Add secret**

### Step 4 — Run the build

1. Go to the **Actions** tab in your repo
2. Click **Build APK** in the left sidebar
3. Click **Run workflow → Run workflow**
4. Wait ~2 minutes for it to finish (green tick ✓)
5. Click into the completed run, scroll down to **Artifacts**, and download **karoo-climblap-debug**

### Step 5 — Install on Karoo 3

1. Unzip the downloaded artifact to get the `.apk` file
2. Open the APK file on your phone's browser (or Files app)
3. Long-press it and **Share** → **Hammerhead Companion app**
4. The Karoo will show an install screen — press **Install**
5. Open **Climb Auto-Lap** from the Karoo main menu once to activate it

Any future changes you push to the repo will automatically trigger a new build.

## Sideloading reminder
This extension is not in the official Extension Library, so it requires sideloading. Hammerhead's official sideloading guide: https://support.hammerhead.io/hc/en-us/articles/31150180125083
