# SleepyBaby – Cry Detection & Soothing

SleepyBaby este o aplicație Android construită cu Jetpack Compose care detectează plânsul local, folosind un detector energetic simplu, și redă un sunet liniștitor. Totul rulează offline, fără modele externe sau servicii cloud.

## Funcționalități

- **Detectare locală** bazată pe analiza energiei mel-spectrogramelor.
- **Serviciu în prim-plan** care continuă să ruleze în fundal cu notificare persistentă.
- **Setări persistente** (DataStore) pentru praguri de plâns/liniște și volum.
- **Înregistrare “shh” personalizat**: salvează un sample de 10s și previzualizează-l din aplicație.
- **UI Compose** modern cu Material 3 și culori alb/albastru inspirate de brandingul Facebook.

## Cum rulezi proiectul

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Permisiuni necesare:
1. Microfon – obligatoriu pentru detectarea plânsului.
2. Notificări – pentru a menține serviciul în prim-plan (Android 13+).

## Assets

Adaugă fișierele necesare în `app/src/main/assets/`:
```
app/src/main/assets/
└── shhh_loop.mp3    # audio liniștitor redat în fundal
```

## Flux de utilizare

1. Deschide aplicația și acordă permisiunea pentru microfon.
2. Înregistrează (opțional) un nou sunet “shh” din secțiunea „Detectare & sunete”.
3. Ajustează pragurile și volumul după preferințe.
4. Pornește detectarea – serviciul va rămâne activ în fundal și va reda sunetul când detectează plânsul.

## Arhitectură

- `CryDetectionEngine` – gestionează captarea audio, clasificarea și automatizarea stărilor.
- `MelSpecExtractor` – transformă PCM-ul în mel-spectrograme.
- `EnergyCryClassifier` – classifierul simplu, bazat pe energie, folosit atât pentru UI cât și pentru logica de fundal.
- `SleepyBabyService` – serviciul în prim-plan care rulează motorul de detecție.
- `NoisePlayer` / `ShushRecorder` – controlul audio pentru redare și înregistrare.

## Note tehnice

- **AudioRecord** la 16kHz mono, ferestre de 1s cu suprapunere.
- **Compose Material 3** pentru UI, cu teme alb/albastru dedicate.
- **Media3 ExoPlayer** pentru redarea sunetelor liniștitoare.
- **Coroutines** și `StateFlow` pentru actualizări reactive.

## Testare

```bash
./gradlew test
```

## Licență

Proiect personal – folosește-l responsabil și adaptează-l după nevoi.
