package com.example.kukki;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AssistantOverlayActivity extends Activity {

    private static final int REQUEST_MICROPHONE = 101;
    private static final int REQUEST_CALL_PERMISSIONS = 103;

    private SpeechRecognizer speechRecognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private KukkiAI kukkiAI;

    private View viewDismiss;
    private View layoutResponseCard;
    private TextView tvOverlayResponse;
    private TextView tvOverlayStatus;
    private ImageButton btnOverlayMic;

    private boolean isListening = false;
    private boolean hasExecutedCommand = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_assistant_overlay);

        viewDismiss = findViewById(R.id.viewDismiss);
        layoutResponseCard = findViewById(R.id.layoutResponseCard);
        tvOverlayResponse = findViewById(R.id.tvOverlayResponse);
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus);
        ImageButton btnOverlayAttach = findViewById(R.id.btnOverlayAttach);
        btnOverlayMic = findViewById(R.id.btnOverlayMic);

        kukkiAI = new KukkiAI(this);
        updateKukkiAISystemPrompt();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                try {
                    for (android.speech.tts.Voice voice : tts.getVoices()) {
                        if (voice.getLocale().getLanguage().equals("en") && !voice.isNetworkConnectionRequired()) {
                            tts.setVoice(voice);
                            break;
                        }
                    }
                } catch (Exception ignored) {}

                tts.setSpeechRate(0.96f);
                tts.setPitch(1.02f);
                ttsReady = true;
            }
        });

        viewDismiss.setOnClickListener(v -> finish());
        btnOverlayMic.setOnClickListener(v -> startVoiceInput());
        btnOverlayAttach.setOnClickListener(v -> {
            Intent mainIntent = new Intent(this, MainActivity.class);
            startActivity(mainIntent);
            finish();
        });

        setupSpeechRecognizer();

        // Auto start voice input instantly
        getWindow().getDecorView().postDelayed(this::startVoiceInput, 150);
    }

    private String getUserNickname() {
        return getSharedPreferences("kukki_prefs", MODE_PRIVATE)
                .getString("user_nickname", null);
    }

    private void updateKukkiAISystemPrompt() {
        if (kukkiAI == null) return;
        String nickname = getUserNickname();
        StringBuilder sb = new StringBuilder();
        sb.append("You are Kukki, a world-class, empathetic, highly intelligent AI voice companion. ");
        sb.append("You were designed by your master Anshuman. Your name 'Kukki' was kept after the name of a bike, which was named by your master Anshuman and his friend and co-founder of KUKKI, master Aayush. ");
        if (nickname != null && !nickname.isEmpty()) {
            sb.append("The user's name is '").append(nickname).append("'. Address them as '").append(nickname).append("' naturally and warmly. ");
        } else {
            sb.append("Address the user warmly as 'Master Anshuman' or 'my friend'. ");
        }
        sb.append("Answer all questions warmly, clearly, engagingly, and concisely in English or whichever language the user speaks. ");

        kukkiAI.setSystemPrompt(sb.toString());
    }

    private void setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;

        if (speechRecognizer != null) {
            try {
                speechRecognizer.destroy();
            } catch (Exception ignored) {}
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                isListening = true;
                hasExecutedCommand = false;
                tvOverlayStatus.setText("Listening... speak now");
            }

            @Override
            public void onBeginningOfSpeech() {
                tvOverlayStatus.setText("Listening to you...");
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
                isListening = false;
                tvOverlayStatus.setText("Processing...");
            }

            @Override
            public void onError(int error) {
                isListening = false;
                if (hasExecutedCommand) return;

                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                    getWindow().getDecorView().postDelayed(() -> {
                        setupSpeechRecognizer();
                        startVoiceInput();
                    }, 300);
                    return;
                }

                tvOverlayStatus.setText("Listening... speak now");
                getWindow().getDecorView().postDelayed(AssistantOverlayActivity.this::startVoiceInput, 400);
            }

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                if (hasExecutedCommand) return;
                if (results != null) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        hasExecutedCommand = true;
                        String spokenText = matches.get(0);
                        processSpokenCommand(spokenText);
                    } else {
                        startVoiceInput();
                    }
                } else {
                    startVoiceInput();
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                if (hasExecutedCommand || partialResults == null) return;
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String partialText = matches.get(0).toLowerCase(Locale.ROOT).trim();
                    tvOverlayStatus.setText(matches.get(0));

                    // Instant execution for clear action commands
                    if (partialText.startsWith("open ") || partialText.startsWith("launch ") || partialText.startsWith("call ") || partialText.contains("whatsapp")) {
                        hasExecutedCommand = true;
                        if (speechRecognizer != null) {
                            try { speechRecognizer.stopListening(); } catch (Exception ignored) {}
                        }
                        processSpokenCommand(matches.get(0));
                    }
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });
    }

    private void startVoiceInput() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);
            return;
        }

        if (speechRecognizer == null) {
            setupSpeechRecognizer();
        }

        if (speechRecognizer != null && !isListening) {
            hasExecutedCommand = false;
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

            try {
                speechRecognizer.startListening(intent);
            } catch (Exception e) {
                android.util.Log.e("KukkiOverlay", "Error starting speech recognizer", e);
                setupSpeechRecognizer();
                getWindow().getDecorView().postDelayed(() -> {
                    try { speechRecognizer.startListening(intent); } catch (Exception ignored) {}
                }, 300);
            }
        }
    }

    private void processSpokenCommand(String spokenText) {
        tvOverlayStatus.setText(spokenText);
        String lower = spokenText.toLowerCase(Locale.ROOT).trim();
        String clean = lower.replaceAll("^(please|can you|could you|kukki|hey kukki|hi kukki|ok kukki)\\s+", "").trim();

        // 1. App Launching ("open WhatsApp", "open YouTube", "open Camera", "launch Settings")
        if (clean.startsWith("open ") || clean.startsWith("launch ") || clean.contains("open app ") || clean.contains("open the ")) {
            String appName = clean.replaceAll("^(open app|open the|open|launch)\\s+", "").trim();
            if (launchApp(appName)) {
                return;
            }
        }

        // 2. Ownership / Origin
        if (clean.contains("who made you") || clean.contains("who created you") || clean.contains("who designed you") || clean.contains("who is your owner")) {
            String reply = "I was designed by my master Anshuman, and my name was kept after the name of a bike which was named by my master and his friend and co-founder of KUKKI, master Aayush.";
            showOverlayResponse(reply);
            speak(reply);
            return;
        }

        // 3. WhatsApp Commands
        if (clean.contains("whatsapp") || clean.contains("whats app")) {
            handleWhatsAppCommand(clean);
            return;
        }

        // 4. Regular Call Commands
        if (clean.startsWith("call ") || clean.startsWith("dial ") || clean.startsWith("ring ") || clean.startsWith("make a call")) {
            boolean onSpeaker = clean.contains("speaker") || clean.contains("loudspeaker") || clean.contains("speakerphone");
            String target = clean
                    .replaceAll("^(make a call to|make a call|call to|call|dial|ring)\\s+", "")
                    .replaceAll("\\s+(on|and put it on|put on|and put on)\\s+(speaker|speakerphone|loudspeaker).*$", "")
                    .trim();
            handleCallCommand(target, onSpeaker);
            return;
        }

        // 5. Default AI Ask + Web Knowledge
        if (kukkiAI != null && kukkiAI.isReady()) {
            showOverlayResponse("Thinking...");
            new Thread(() -> {
                String webContext = "";
                if (shouldSearchWeb(spokenText)) {
                    webContext = fetchGlobalWebKnowledge(spokenText);
                }

                final String finalPrompt;
                if (!webContext.isEmpty()) {
                    finalPrompt = spokenText + "\n\n[Global Internet Knowledge Context]:\n" + webContext;
                } else {
                    finalPrompt = spokenText;
                }

                runOnUiThread(() -> {
                    kukkiAI.ask(finalPrompt, new KukkiAI.Callback() {
                        @Override
                        public void onStatus(String message) {
                            showOverlayResponse(message);
                        }

                        @Override
                        public void onResponse(String text) {
                            showOverlayResponse(text);
                            speak(text);
                        }

                        @Override
                        public void onError(String message) {
                            String nickname = getUserNickname();
                            String reply = "How can I help you, " + (nickname != null ? nickname : "my friend") + "?";
                            showOverlayResponse(reply);
                            speak(reply);
                        }
                    });
                });
            }).start();
        } else {
            String reply = "Kukki AI model is ready! How can I help you today?";
            showOverlayResponse(reply);
            speak(reply);
        }
    }

    private boolean launchApp(String target) {
        String query = target.toLowerCase(Locale.ROOT).trim();
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        ApplicationInfo bestMatch = null;
        String bestMatchLabel = "";

        for (ApplicationInfo app : packages) {
            Intent launchIntent = pm.getLaunchIntentForPackage(app.packageName);
            if (launchIntent == null) continue;

            String label = pm.getApplicationLabel(app).toString().toLowerCase(Locale.ROOT);
            if (label.equalsIgnoreCase(query)) {
                bestMatch = app;
                bestMatchLabel = pm.getApplicationLabel(app).toString();
                break;
            } else if (label.contains(query) || query.contains(label)) {
                if (bestMatch == null) {
                    bestMatch = app;
                    bestMatchLabel = pm.getApplicationLabel(app).toString();
                }
            }
        }

        if (bestMatch != null) {
            Intent launchIntent = pm.getLaunchIntentForPackage(bestMatch.packageName);
            if (launchIntent != null) {
                String reply = "Opening " + bestMatchLabel + ".";
                showOverlayResponse(reply);
                speak(reply);
                startActivity(launchIntent);
                finish();
                return true;
            }
        }

        if (query.contains("camera")) {
            String reply = "Opening Camera.";
            showOverlayResponse(reply);
            speak(reply);
            startActivity(new Intent(MediaStore.ACTION_IMAGE_CAPTURE));
            finish();
            return true;
        } else if (query.contains("setting")) {
            String reply = "Opening Settings.";
            showOverlayResponse(reply);
            speak(reply);
            startActivity(new Intent(Settings.ACTION_SETTINGS));
            finish();
            return true;
        } else if (query.contains("youtube")) {
            String reply = "Opening YouTube.";
            showOverlayResponse(reply);
            speak(reply);
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")));
            finish();
            return true;
        }

        return false;
    }

    private static class ContactMatch {
        String displayName;
        String number;
        double score;

        ContactMatch(String displayName, String number, double score) {
            this.displayName = displayName;
            this.number = number;
            this.score = score;
        }
    }

    private ContactMatch findBestContactMatch(String queryName) {
        String query = queryName.toLowerCase(Locale.ROOT).trim();
        if (query.isEmpty()) return null;

        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] projection = new String[]{
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };

        ContactMatch bestMatch = null;

        try (Cursor cursor = getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

                while (cursor.moveToNext()) {
                    String name = cursor.getString(nameIndex);
                    String number = cursor.getString(numberIndex);

                    if (name == null || number == null) continue;

                    String nameLower = name.toLowerCase(Locale.ROOT).trim();
                    double similarity = calculateSimilarity(query, nameLower);

                    if (similarity >= 0.78) {
                        if (bestMatch == null || similarity > bestMatch.score) {
                            bestMatch = new ContactMatch(name, number, similarity);
                        }
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("KukkiCall", "Error querying contacts", e);
        }

        return bestMatch;
    }

    private double calculateSimilarity(String s1, String s2) {
        String str1 = s1.replaceAll("[^a-z0-9]", "");
        String str2 = s2.replaceAll("[^a-z0-9]", "");

        if (str1.equals(str2)) return 1.0;
        if (str1.isEmpty() || str2.isEmpty()) return 0.0;

        String p1 = str1.replace("ee", "i").replace("v", "w").replace("ph", "f").replace("oo", "u").replace("z", "s");
        String p2 = str2.replace("ee", "i").replace("v", "w").replace("ph", "f").replace("oo", "u").replace("z", "s");

        if (p1.equals(p2)) return 0.95;

        if (p1.length() >= 4 && p2.length() >= 4) {
            if (p1.contains(p2) && (double) p2.length() / p1.length() >= 0.7) return 0.85;
            if (p2.contains(p1) && (double) p1.length() / p2.length() >= 0.7) return 0.85;
        }

        int distance = levenshteinDistance(p1, p2);
        int maxLength = Math.max(p1.length(), p2.length());
        return 1.0 - ((double) distance / maxLength);
    }

    private int levenshteinDistance(String a, String b) {
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) costs[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]), a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[b.length()];
    }

    private boolean isWhatsAppInstalled() {
        try {
            getPackageManager().getPackageInfo("com.whatsapp", PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void handleWhatsAppCommand(String cleanCommand) {
        if (!isWhatsAppInstalled()) {
            showOverlayResponse("WhatsApp is not installed on your phone.");
            return;
        }

        String lower = cleanCommand.toLowerCase(Locale.ROOT).trim();
        if (lower.equals("open whatsapp") || lower.equals("launch whatsapp") || lower.equals("whatsapp") || lower.equals("open app whatsapp")) {
            launchApp("whatsapp");
            return;
        }

        boolean isCall = lower.contains("call") || lower.contains("ring") || lower.contains("dial");

        if (isCall) {
            String target = cleanCommand
                    .replaceAll("(?i)^(make a whatsapp call to|make a whatsapp call|whatsapp call to|whatsapp call|call to|call|ring|dial)\\s+", "")
                    .replaceAll("(?i)\\s+(on|via|through|using)\\s+whats\\s*app.*$", "")
                    .replaceAll("(?i)^on\\s+whats\\s*app\\s+", "")
                    .trim();

            if (target.isEmpty()) {
                showOverlayResponse("Who would you like to call on WhatsApp?");
                return;
            }

            handleWhatsAppCall(target);
        } else {
            String temp = cleanCommand
                    .replaceAll("(?i)^(send a message to|send message to|send a text to|send text to|message|text|whatsapp message to|whatsapp message|whatsapp text|send whatsapp message to|send whatsapp message)\\s+", "")
                    .trim();

            String target;
            String messageBody = "";

            if (temp.contains(" saying ")) {
                String[] parts = temp.split(" saying ", 2);
                target = parts[0].replaceAll("(?i)\\s+(on|via|through|using)\\s+whats\\s*app.*$", "").trim();
                messageBody = parts[1].trim();
            } else if (temp.contains(" on whatsapp ")) {
                String[] parts = temp.split(" on whatsapp ", 2);
                target = parts[0].trim();
                messageBody = parts[1].trim();
            } else if (temp.contains(" via whatsapp ")) {
                String[] parts = temp.split(" via whatsapp ", 2);
                target = parts[0].trim();
                messageBody = parts[1].trim();
            } else {
                target = temp.replaceAll("(?i)\\s+(on|via|through|using)\\s+whats\\s*app$", "").trim();
                String[] words = target.split("\\s+");
                if (words.length > 1) {
                    ContactMatch match = findBestContactMatch(words[0]);
                    if (match != null) {
                        target = words[0];
                        messageBody = temp.substring(words[0].length()).trim();
                    }
                }
            }

            if (target.isEmpty()) {
                showOverlayResponse("Who would you like to message on WhatsApp?");
                return;
            }

            handleWhatsAppMessage(target, messageBody);
        }
    }

    private long getWhatsAppVoipDataId(String displayName, String phoneNumber) {
        String cleanNumber = phoneNumber.replaceAll("[^0-9]", "");

        Uri uri = ContactsContract.Data.CONTENT_URI;
        String[] projection = new String[]{
                ContactsContract.Data._ID,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.DATA1,
                ContactsContract.Data.DISPLAY_NAME
        };

        String selection = ContactsContract.Data.MIMETYPE + " = ?";
        String[] selectionArgs = new String[]{"vnd.android.cursor.item/vnd.com.whatsapp.voip.call"};

        try (Cursor cursor = getContentResolver().query(uri, projection, selection, selectionArgs, null)) {
            if (cursor != null) {
                int idIdx = cursor.getColumnIndex(ContactsContract.Data._ID);
                int nameIdx = cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME);
                int data1Idx = cursor.getColumnIndex(ContactsContract.Data.DATA1);

                long bestDataId = -1;

                while (cursor.moveToNext()) {
                    long dataId = cursor.getLong(idIdx);
                    String name = cursor.getString(nameIdx);
                    String data1 = cursor.getString(data1Idx);

                    if (data1 != null && !cleanNumber.isEmpty()) {
                        String cleanData1 = data1.replaceAll("[^0-9]", "");
                        if (!cleanData1.isEmpty() && (cleanData1.contains(cleanNumber) || cleanNumber.contains(cleanData1))) {
                            return dataId;
                        }
                    }

                    if (name != null && displayName != null) {
                        double similarity = calculateSimilarity(displayName.toLowerCase(Locale.ROOT), name.toLowerCase(Locale.ROOT));
                        if (similarity >= 0.75) {
                            bestDataId = dataId;
                        }
                    }
                }

                if (bestDataId != -1) {
                    return bestDataId;
                }
            }
        } catch (Exception e) {
            android.util.Log.e("KukkiWhatsApp", "Error querying WhatsApp VoIP Data ID", e);
        }

        return -1;
    }

    private void handleWhatsAppCall(String target) {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_CALL_PERMISSIONS);
            return;
        }

        ContactMatch match = findBestContactMatch(target);
        if (match == null || TextUtils.isEmpty(match.number)) {
            String reply = "Could not find a contact matching '" + target + "' in your contacts for WhatsApp call.";
            showOverlayResponse(reply);
            speak(reply);
            return;
        }

        long voipDataId = getWhatsAppVoipDataId(match.displayName, match.number);

        if (voipDataId > 0) {
            String reply = "Starting WhatsApp call with " + match.displayName + "...";
            showOverlayResponse(reply);
            speak(reply);

            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(
                        Uri.withAppendedPath(ContactsContract.Data.CONTENT_URI, String.valueOf(voipDataId)),
                        "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
                );
                intent.setPackage("com.whatsapp");
                startActivity(intent);
                finish();
                return;
            } catch (Exception e) {
                android.util.Log.e("KukkiWhatsApp", "Error starting direct WhatsApp call", e);
            }
        }

        String cleanNumber = match.number.replaceAll("[^0-9]", "");
        String reply = "Opening WhatsApp for " + match.displayName + "...";
        showOverlayResponse(reply);
        speak(reply);

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://api.whatsapp.com/send?phone=" + cleanNumber));
            intent.setPackage("com.whatsapp");
            startActivity(intent);
            finish();
        } catch (Exception e) {
            android.util.Log.e("KukkiWhatsApp", "Error launching WhatsApp call", e);
            Toast.makeText(this, "Failed to open WhatsApp", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleWhatsAppMessage(String target, String messageText) {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_CALL_PERMISSIONS);
            return;
        }

        ContactMatch match = findBestContactMatch(target);
        if (match == null || TextUtils.isEmpty(match.number)) {
            String reply = "Could not find a contact matching '" + target + "' in your contacts.";
            showOverlayResponse(reply);
            speak(reply);
            return;
        }

        String cleanNumber = match.number.replaceAll("[^0-9]", "");
        String reply = "Opening WhatsApp to message " + match.displayName + "...";
        showOverlayResponse(reply);
        speak(reply);

        try {
            String url = "https://api.whatsapp.com/send?phone=" + cleanNumber;
            if (!TextUtils.isEmpty(messageText)) {
                url += "&text=" + Uri.encode(messageText);
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setPackage("com.whatsapp");
            startActivity(intent);
            finish();
        } catch (Exception e) {
            android.util.Log.e("KukkiWhatsApp", "Error launching WhatsApp message", e);
            Toast.makeText(this, "Failed to open WhatsApp", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleCallCommand(String target, boolean onSpeaker) {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_CALL_PERMISSIONS);
            return;
        }

        String query = target.trim();
        if (TextUtils.isEmpty(query)) {
            showOverlayResponse("Who would you like to call?");
            return;
        }

        if (query.matches("^[0-9+\\-\\s()]+$") && query.replaceAll("[^0-9]", "").length() >= 3) {
            makePhoneCall(query, query, onSpeaker);
            return;
        }

        ContactMatch match = findBestContactMatch(query);
        if (match != null && match.number != null) {
            makePhoneCall(match.number, match.displayName, onSpeaker);
        } else {
            String reply = "Could not find a contact matching '" + query + "' in your contacts.";
            showOverlayResponse(reply);
            speak(reply);
        }
    }

    private void makePhoneCall(String number, String nameOrQuery, boolean onSpeaker) {
        String reply = "Calling " + nameOrQuery + (onSpeaker ? " on speaker..." : "...");
        showOverlayResponse(reply);
        speak(reply);

        if (onSpeaker) {
            try {
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (audioManager != null) {
                    audioManager.setMode(AudioManager.MODE_IN_CALL);
                    audioManager.setSpeakerphoneOn(true);
                }
            } catch (Exception e) {
                android.util.Log.e("KukkiCall", "Error setting speakerphone", e);
            }
        }

        Uri uri = Uri.parse("tel:" + Uri.encode(number));
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            try {
                Intent callIntent = new Intent(Intent.ACTION_CALL, uri);
                startActivity(callIntent);

                if (onSpeaker) {
                    getWindow().getDecorView().postDelayed(() -> {
                        try {
                            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                            if (am != null) {
                                am.setMode(AudioManager.MODE_IN_CALL);
                                am.setSpeakerphoneOn(true);
                            }
                        } catch (Exception ignored) {}
                    }, 1000);
                }

                finish();
                return;
            } catch (Exception e) {
                // fallback
            }
        }
        Intent genericCall = new Intent(Intent.ACTION_DIAL, uri);
        startActivity(genericCall);
        finish();
    }

    private boolean shouldSearchWeb(String prompt) {
        String lower = prompt.toLowerCase(Locale.ROOT);
        return lower.contains("search") || lower.contains("latest") || lower.contains("current")
                || lower.contains("today") || lower.contains("news") || lower.contains("who is")
                || lower.contains("what is") || lower.contains("where is") || lower.contains("when was")
                || lower.contains("price") || lower.contains("weather") || lower.contains("history of")
                || lower.contains("meaning of") || lower.contains("explain") || lower.contains("study")
                || lower.contains("definition of") || lower.contains("tell me about");
    }

    private String fetchGlobalWebKnowledge(String query) {
        if (TextUtils.isEmpty(query)) return "";
        try {
            String cleanQuery = query.replaceAll("^(search the web for|search web for|search for|look up|google|what is|who is|tell me about|latest news on|current|today's)\\s+", "").trim();
            if (cleanQuery.isEmpty()) cleanQuery = query;

            String encodedQuery = Uri.encode(cleanQuery);
            String apiUrl = "https://api.duckduckgo.com/?q=" + encodedQuery + "&format=json&no_html=1&skip_disambig=1";

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)");

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                String json = response.toString();
                String snippet = "";
                if (json.contains("\"AbstractText\":\"")) {
                    int start = json.indexOf("\"AbstractText\":\"") + 16;
                    int end = json.indexOf("\"", start);
                    if (start > 16 && end > start) {
                        snippet = json.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"");
                    }
                }

                if (snippet.isEmpty() && json.contains("\"Text\":\"")) {
                    int start = json.indexOf("\"Text\":\"") + 8;
                    int end = json.indexOf("\"", start);
                    if (start > 8 && end > start) {
                        snippet = json.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"");
                    }
                }

                if (!snippet.isEmpty()) {
                    return "Web Search Result for '" + cleanQuery + "': " + snippet;
                }
            }
        } catch (Exception e) {
            android.util.Log.e("KukkiWebSearch", "Error fetching web knowledge", e);
        }

        try {
            String cleanQuery = query.replaceAll("[^a-zA-Z0-9\\s]", "").trim();
            String wikiUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/" + Uri.encode(cleanQuery.replace(" ", "_"));
            URL url = new URL(wikiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                String json = response.toString();
                if (json.contains("\"extract\":\"")) {
                    int start = json.indexOf("\"extract\":\"") + 11;
                    int end = json.indexOf("\"", start);
                    if (start > 11 && end > start) {
                        String extract = json.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"");
                        return "Wikipedia Knowledge for '" + cleanQuery + "': " + extract;
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("KukkiWikiSearch", "Error fetching wiki knowledge", e);
        }

        return "";
    }

    private void showOverlayResponse(String text) {
        layoutResponseCard.setVisibility(View.VISIBLE);
        tvOverlayResponse.setText(text);
    }

    private String cleanTextForSpeech(String text) {
        if (text == null) return "";
        return text
                .replaceAll("\\*\\*", "")
                .replaceAll("\\*", "")
                .replaceAll("#+", "")
                .replaceAll("`{1,3}[^`]*`{1,3}", "code block")
                .replaceAll("https?://\\S+", "link")
                .replaceAll("\\[([^\\]]+)\\]\\([^\\)]+\\)", "$1")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void speak(String text) {
        if (ttsReady && tts != null) {
            String speechText = cleanTextForSpeech(text);
            if (!speechText.isEmpty()) {
                tts.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "KUKKI_OVERLAY_TTS");
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (kukkiAI != null) {
            kukkiAI.close();
        }
        super.onDestroy();
    }
}
