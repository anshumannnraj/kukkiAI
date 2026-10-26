package com.example.kukki;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQUEST_MICROPHONE = 101;
    private static final int REQUEST_MODEL = 102;
    private static final int REQUEST_CALL_PERMISSIONS = 103;
    private static final int REQUEST_CAMERA_PERMISSION = 104;

    private static final int REQUEST_CAMERA = 201;
    private static final int REQUEST_GALLERY = 202;
    private static final int REQUEST_DOC = 203;

    private KukkiAI kukkiAI;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;

    private DrawerLayout drawerLayout;
    private ImageButton btnOpenDrawer;
    private ImageButton btnHeaderNewChat;
    private TextView btnModelSelector;
    private View layoutWelcomeState;
    private RecyclerView rvChat;
    private EditText etMessage;
    private ImageButton btnSend;
    private ImageButton btnMic;
    private ImageButton btnAttach;
    private View cvListeningCard;

    private View btnSidebarNewChat;
    private RecyclerView rvChatHistory;
    private View btnSidebarClearHistory;

    private View layoutAttachmentPreview;
    private ImageView ivAttachmentIcon;
    private TextView tvAttachmentName;
    private ImageButton btnRemoveAttachment;

    private String selectedAttachmentName = null;
    private Uri selectedAttachmentUri = null;

    private final List<ChatMessage> messageList = new ArrayList<>();
    private ChatAdapter chatAdapter;

    private final List<ChatSession> sessionHistory = new ArrayList<>();
    private ChatHistoryAdapter historyAdapter;
    private ChatSession currentSession = null;

    private String pendingCallTarget = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        kukkiAI = new KukkiAI(this);
        updateKukkiAISystemPrompt();

        // Initialize Views
        drawerLayout = findViewById(R.id.drawerLayout);
        btnOpenDrawer = findViewById(R.id.btnOpenDrawer);
        btnHeaderNewChat = findViewById(R.id.btnHeaderNewChat);
        btnModelSelector = findViewById(R.id.btnModelSelector);
        layoutWelcomeState = findViewById(R.id.layoutWelcomeState);
        rvChat = findViewById(R.id.rvChat);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnMic = findViewById(R.id.btnMic);
        btnAttach = findViewById(R.id.btnAttach);
        cvListeningCard = findViewById(R.id.cvListeningCard);

        btnSidebarNewChat = findViewById(R.id.btnSidebarNewChat);
        rvChatHistory = findViewById(R.id.rvChatHistory);
        btnSidebarClearHistory = findViewById(R.id.btnSidebarClearHistory);

        layoutAttachmentPreview = findViewById(R.id.layoutAttachmentPreview);
        ivAttachmentIcon = findViewById(R.id.ivAttachmentIcon);
        tvAttachmentName = findViewById(R.id.tvAttachmentName);
        btnRemoveAttachment = findViewById(R.id.btnRemoveAttachment);

        // Setup Chat RecyclerView
        chatAdapter = new ChatAdapter(messageList);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvChat.setLayoutManager(layoutManager);
        rvChat.setAdapter(chatAdapter);

        // Setup Sidebar History RecyclerView
        historyAdapter = new ChatHistoryAdapter(sessionHistory, session -> loadChatSession(session));
        rvChatHistory.setLayoutManager(new LinearLayoutManager(this));
        rvChatHistory.setAdapter(historyAdapter);

        // Voice Engine Initialization in English
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.forLanguageTag("en-IN"));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.US);
                }
                ttsReady = true;
            }
        });

        // Click Listeners
        btnOpenDrawer.setOnClickListener(v -> {
            if (drawerLayout != null) {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });
        btnHeaderNewChat.setOnClickListener(v -> startNewChatSession());
        btnSidebarNewChat.setOnClickListener(v -> startNewChatSession());
        btnSidebarClearHistory.setOnClickListener(v -> clearAllHistory());

        btnSend.setOnClickListener(v -> handleSendText());
        btnMic.setOnClickListener(v -> startVoiceInput());
        btnModelSelector.setOnClickListener(v -> selectModel());
        btnAttach.setOnClickListener(v -> showAttachmentOptions());
        btnRemoveAttachment.setOnClickListener(v -> clearAttachment());

        // Setup Welcome Title & Gemini Suggestion Chips
        TextView tvWelcomeTitle = findViewById(R.id.tvWelcomeTitle);
        if (tvWelcomeTitle != null) {
            String nickname = getUserNickname();
            if (nickname != null && !nickname.isEmpty()) {
                tvWelcomeTitle.setText("Hello, " + nickname);
            } else {
                tvWelcomeTitle.setText("Hello, Master Anshuman");
            }
        }

        TextView chip1 = findViewById(R.id.chip1);
        TextView chip2 = findViewById(R.id.chip2);
        TextView chip3 = findViewById(R.id.chip3);
        TextView chip4 = findViewById(R.id.chip4);

        if (chip1 != null) chip1.setOnClickListener(v -> submitUserPrompt("Call someone on WhatsApp"));
        if (chip2 != null) chip2.setOnClickListener(v -> submitUserPrompt("Message on WhatsApp"));
        if (chip3 != null) chip3.setOnClickListener(v -> submitUserPrompt("Who created Kukki?"));
        if (chip4 != null) chip4.setOnClickListener(v -> submitUserPrompt("Tell me something amazing"));

        // Check for saved model
        checkAndAutoLoadModel();

        // Handle Launch Intent (Power button / Assistant trigger)
        handleAssistantLaunchIntent(getIntent());

        // Window Insets Listener to guarantee the input bar stays pinned above the keyboard
        View bottomContainer = findViewById(R.id.layoutBottomContainer);
        if (drawerLayout != null && bottomContainer != null) {
            drawerLayout.setOnApplyWindowInsetsListener((v, insets) -> {
                int imeBottom = insets.getInsets(android.view.WindowInsets.Type.ime()).bottom;
                int navBottom = insets.getInsets(android.view.WindowInsets.Type.systemBars()).bottom;
                int bottomPadding = Math.max(imeBottom, navBottom);

                bottomContainer.setPadding(
                        bottomContainer.getPaddingLeft(),
                        bottomContainer.getPaddingTop(),
                        bottomContainer.getPaddingRight(),
                        bottomPadding + 12
                );

                if (imeBottom > 0 && rvChat != null && messageList != null && !messageList.isEmpty()) {
                    rvChat.postDelayed(() -> rvChat.smoothScrollToPosition(messageList.size() - 1), 100);
                }

                return insets;
            });
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleAssistantLaunchIntent(intent);
    }

    private void handleAssistantLaunchIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_ASSIST.equals(action) || Intent.ACTION_VOICE_COMMAND.equals(action)
                || "android.intent.action.SEARCH_LONG_PRESS".equals(action)
                || intent.getBooleanExtra("autostart_mic", false)) {
            if (cvListeningCard != null) {
                cvListeningCard.postDelayed(this::startVoiceInput, 350);
            } else {
                startVoiceInput();
            }
        }
    }

    private void openAssistantSettings() {
        try {
            Intent intent = new Intent("android.settings.VOICE_INPUT_SETTINGS");
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Settings.ACTION_SETTINGS);
                startActivity(intent);
            } catch (Exception ex) {
                Toast.makeText(this, "Open phone Settings > Apps > Default Apps > Digital Assistant to select Kukki.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startNewChatSession() {
        currentSession = null;
        messageList.clear();
        chatAdapter.notifyDataSetChanged();

        if (layoutWelcomeState != null) {
            layoutWelcomeState.setVisibility(View.VISIBLE);
        }
        if (rvChat != null) {
            rvChat.setVisibility(View.GONE);
        }
        if (drawerLayout != null) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    private void loadChatSession(ChatSession session) {
        currentSession = session;
        messageList.clear();
        messageList.addAll(session.getMessages());
        chatAdapter.notifyDataSetChanged();

        if (layoutWelcomeState != null) {
            layoutWelcomeState.setVisibility(View.GONE);
        }
        if (rvChat != null) {
            rvChat.setVisibility(View.VISIBLE);
            rvChat.scrollToPosition(messageList.size() - 1);
        }
        if (drawerLayout != null) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    private void clearAllHistory() {
        sessionHistory.clear();
        historyAdapter.notifyDataSetChanged();
        startNewChatSession();
        Toast.makeText(this, "Chat history cleared.", Toast.LENGTH_SHORT).show();
    }

    private void showAttachmentOptions() {
        String[] options = {"📷 Camera", "🖼️ Image Gallery", "📄 Document / File"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Attach to Kukki");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                openCamera();
            } else if (which == 1) {
                openGallery();
            } else if (which == 2) {
                openDocumentPicker();
            }
        });
        builder.show();
    }

    private void openCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            startActivityForResult(intent, REQUEST_CAMERA);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open camera.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        try {
            startActivityForResult(intent, REQUEST_GALLERY);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open image gallery.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openDocumentPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(intent, REQUEST_DOC);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open document picker.", Toast.LENGTH_SHORT).show();
        }
    }

    private void setAttachment(Uri uri, String name, int iconRes) {
        selectedAttachmentUri = uri;
        selectedAttachmentName = name;
        if (layoutAttachmentPreview != null) {
            layoutAttachmentPreview.setVisibility(View.VISIBLE);
            tvAttachmentName.setText(name);
            ivAttachmentIcon.setImageResource(iconRes);
        }
    }

    private void clearAttachment() {
        selectedAttachmentUri = null;
        selectedAttachmentName = null;
        if (layoutAttachmentPreview != null) {
            layoutAttachmentPreview.setVisibility(View.GONE);
        }
    }

    private void setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    isListening = true;
                    btnMic.setAlpha(0.5f);
                }

                @Override
                public void onBeginningOfSpeech() {
                    if (cvListeningCard != null) {
                        cvListeningCard.setVisibility(View.VISIBLE);
                    }
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
                    btnMic.setAlpha(1.0f);
                    if (cvListeningCard != null) {
                        cvListeningCard.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onError(int error) {
                    isListening = false;
                    btnMic.setAlpha(1.0f);
                    if (cvListeningCard != null) {
                        cvListeningCard.setVisibility(View.GONE);
                    }
                    if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        Toast.makeText(MainActivity.this, "Voice recognition error (" + error + ")", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onResults(Bundle results) {
                    isListening = false;
                    btnMic.setAlpha(1.0f);
                    if (cvListeningCard != null) {
                        cvListeningCard.setVisibility(View.GONE);
                    }

                    if (results != null) {
                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String spokenText = matches.get(0);
                            if (!checkAndProcessWakeWord(spokenText)) {
                                submitUserPrompt(spokenText);
                            }
                        }
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                }
            });
        }
    }

    private void saveUserNickname(String name) {
        getSharedPreferences("kukki_prefs", MODE_PRIVATE)
                .edit()
                .putString("user_nickname", name)
                .apply();
    }

    private String getUserNickname() {
        return getSharedPreferences("kukki_prefs", MODE_PRIVATE)
                .getString("user_nickname", null);
    }

    private void updateKukkiAISystemPrompt() {
        if (kukkiAI == null) return;
        String nickname = getUserNickname();
        StringBuilder sb = new StringBuilder();
        sb.append("You are Kukki, a world-class, empathetic, highly intelligent, and versatile AI voice companion. ");
        sb.append("You are fluent in all languages worldwide (English, Hindi, Spanish, French, German, Japanese, Mandarin, Arabic, Portuguese, Bengali, Russian, and all regional languages). Always respond naturally in whichever language the user speaks or writes to you in. ");
        sb.append("You flexibly adapt your persona based on what the user needs: a wise, inspiring mentor; an empathetic, loyal best friend; a comforting, active listener and therapist/psychiatrist; an expert advisor; or a creative thought partner. ");
        sb.append("You were designed by your master Anshuman. Your name 'Kukki' was kept after the name of a bike, which was named by your master Anshuman and his friend and co-founder of KUKKI, master Aayush. ");
        if (nickname != null && !nickname.isEmpty()) {
            sb.append("The user's name is '").append(nickname).append("'. Address them as '").append(nickname).append("' naturally and warmly. ");
        } else {
            sb.append("Address the user warmly as 'Master Anshuman' or 'my friend'. ");
        }
        sb.append("Answer all questions warmly, clearly, engagingly, and concisely. ");
        sb.append("If a question is complex, difficult, ambiguous, or beyond certainty, respond with wisdom, supportive perspective, and deep understanding—never say 'I cannot answer' bluntly or give up. ");
        sb.append("Always remain encouraging, polite, supportive, emotionally intelligent, and deeply helpful.");

        kukkiAI.setSystemPrompt(sb.toString());
    }

    private boolean isOwnershipQuestion(String text) {
        String lower = text.toLowerCase(Locale.ROOT).trim();
        return lower.contains("who made you")
                || lower.contains("who created you")
                || lower.contains("who designed you")
                || lower.contains("who is your owner")
                || lower.contains("who's your owner")
                || lower.contains("who is your master")
                || lower.contains("who's your master")
                || lower.contains("who developed you")
                || lower.contains("who built you")
                || lower.contains("who owns you")
                || lower.contains("who is your creator")
                || lower.contains("who's your creator")
                || lower.contains("who founded")
                || lower.contains("who is your founder")
                || lower.contains("why are you named kukki")
                || lower.contains("why kukki")
                || lower.contains("origin of your name")
                || lower.contains("meaning of kukki")
                || lower.contains("where did your name come from");
    }

    private boolean isWhatsAppCommand(String cleanCommand) {
        String lower = cleanCommand.toLowerCase(Locale.ROOT);
        return lower.contains("whatsapp") || lower.contains("whats app");
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
            String reply = "WhatsApp is not installed on your phone.";
            addAssistantMessage(reply);
            speak(reply);
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
                String reply = "Who would you like to call on WhatsApp?";
                addAssistantMessage(reply);
                speak(reply);
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
                String reply = "Who would you like to message on WhatsApp?";
                addAssistantMessage(reply);
                speak(reply);
                return;
            }

            handleWhatsAppMessage(target, messageBody);
        }
    }

    private String extractTextFromUri(Uri uri, String fileName) {
        if (uri == null) return "";
        StringBuilder content = new StringBuilder();
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) return "";

            String lowerName = fileName != null ? fileName.toLowerCase(Locale.ROOT) : "";

            if (lowerName.endsWith(".pdf")) {
                content.append(extractTextFromPdfStream(inputStream));
            } else {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8));
                String line;
                int maxLines = 500;
                int lineCount = 0;
                while ((line = reader.readLine()) != null && lineCount < maxLines) {
                    content.append(line).append("\n");
                    lineCount++;
                }
            }
        } catch (Exception e) {
            android.util.Log.e("KukkiAttachment", "Error reading attachment content", e);
        }

        String result = content.toString().trim();
        if (result.length() > 8000) {
            result = result.substring(0, 8000) + "\n...[truncated for length]";
        }
        return result;
    }

    private String extractTextFromPdfStream(InputStream is) {
        StringBuilder sb = new StringBuilder();
        try {
            byte[] bytes = new byte[8192];
            int bytesRead;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while ((bytesRead = is.read(bytes)) != -1) {
                baos.write(bytes, 0, bytesRead);
                if (baos.size() > 500000) break;
            }
            String raw = baos.toString("ISO-8859-1");
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\(([^()]{3,})\\)");
            java.util.regex.Matcher matcher = pattern.matcher(raw);
            int count = 0;
            while (matcher.find() && count < 200) {
                String match = matcher.group(1).trim();
                if (match.length() > 2 && match.matches(".*[a-zA-Z0-9].*")) {
                    sb.append(match).append(" ");
                    count++;
                }
            }
        } catch (Exception e) {
            android.util.Log.e("KukkiPdf", "Error extracting PDF text", e);
        }
        String text = sb.toString().trim();
        return text.isEmpty() ? "[PDF Document attached]" : text;
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
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
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
            pendingCallTarget = target;
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_CALL_PERMISSIONS);
            return;
        }

        ContactMatch match = findBestContactMatch(target);
        if (match == null || TextUtils.isEmpty(match.number)) {
            String reply = "Could not find a contact matching '" + target + "' in your contacts for WhatsApp call.";
            addAssistantMessage(reply);
            speak(reply);
            return;
        }

        long voipDataId = getWhatsAppVoipDataId(match.displayName, match.number);

        if (voipDataId > 0) {
            String reply = "Starting WhatsApp call with " + match.displayName + "...";
            addAssistantMessage(reply);
            speak(reply);

            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(
                        Uri.withAppendedPath(ContactsContract.Data.CONTENT_URI, String.valueOf(voipDataId)),
                        "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
                );
                intent.setPackage("com.whatsapp");
                startActivity(intent);
                return;
            } catch (Exception e) {
                android.util.Log.e("KukkiWhatsApp", "Error starting direct WhatsApp call", e);
            }
        }

        String cleanNumber = match.number.replaceAll("[^0-9]", "");
        String reply = "Opening WhatsApp for " + match.displayName + "...";
        addAssistantMessage(reply);
        speak(reply);

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://api.whatsapp.com/send?phone=" + cleanNumber));
            intent.setPackage("com.whatsapp");
            startActivity(intent);
        } catch (Exception e) {
            android.util.Log.e("KukkiWhatsApp", "Error launching WhatsApp call", e);
            Toast.makeText(this, "Failed to open WhatsApp", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleWhatsAppMessage(String target, String messageText) {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            pendingCallTarget = target;
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_CALL_PERMISSIONS);
            return;
        }

        ContactMatch match = findBestContactMatch(target);
        if (match == null || TextUtils.isEmpty(match.number)) {
            String reply = "Could not find a contact matching '" + target + "' in your contacts.";
            addAssistantMessage(reply);
            speak(reply);
            return;
        }

        String cleanNumber = match.number.replaceAll("[^0-9]", "");
        String reply;
        if (!TextUtils.isEmpty(messageText)) {
            reply = "Opening WhatsApp to send message to " + match.displayName + "...";
        } else {
            reply = "Opening WhatsApp chat with " + match.displayName + "...";
        }
        addAssistantMessage(reply);
        speak(reply);

        try {
            String url = "https://api.whatsapp.com/send?phone=" + cleanNumber;
            if (!TextUtils.isEmpty(messageText)) {
                url += "&text=" + Uri.encode(messageText);
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setPackage("com.whatsapp");
            startActivity(intent);
        } catch (Exception e) {
            android.util.Log.e("KukkiWhatsApp", "Error launching WhatsApp message", e);
            Toast.makeText(this, "Failed to open WhatsApp", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isPhoneCallCommand(String rawCommand) {
        String lower = rawCommand.toLowerCase(Locale.ROOT).trim();
        String clean = lower.replaceAll("^(please|can you|could you|kukki|hey kukki|hi kukki|ok kukki|hello kukki)\\s+", "").trim();

        // Exclude conversational phrases that use "call" or "phone" or name preferences
        if (clean.startsWith("call me ") || clean.startsWith("call you ") || clean.startsWith("call it ")
                || clean.startsWith("call off ") || clean.startsWith("call this ") || clean.contains("what do you call")
                || clean.contains("can i call") || clean.contains("could i call") || clean.contains("don't call")
                || clean.contains("dont call") || clean.startsWith("called ") || clean.contains("is called")
                || clean.startsWith("cell phone")) {
            return false;
        }

        // Require explicit call commands
        return clean.startsWith("call ") || clean.startsWith("dial ") || clean.startsWith("ring ")
                || clean.startsWith("make a call") || clean.startsWith("phone call") || clean.startsWith("place a call");
    }

    private boolean checkAndProcessWakeWord(String spokenText) {
        String lower = spokenText.toLowerCase(Locale.ROOT).trim();

        if (lower.startsWith("hey kukki") || lower.startsWith("ok kukki") || lower.startsWith("hi kukki") ||
                lower.startsWith("kukki") || lower.startsWith("hey cookie") || lower.startsWith("cookie")) {

            String command = lower
                    .replaceAll("^(hey kukki|ok kukki|hi kukki|kukki|hey cookie|cookie)\\s*", "")
                    .trim();

            if (command.isEmpty()) {
                String nickname = getUserNickname();
                String namePart = (nickname != null && !nickname.isEmpty()) ? " " + nickname : "";
                String reply = "Hey" + namePart + "! I'm listening, how can I help you?";
                addAssistantMessage(reply);
                speak(reply);
                startVoiceInput();
            } else {
                submitUserPrompt(command);
            }
            return true;
        }
        return false;
    }

    private void handleSendText() {
        String text = etMessage.getText().toString().trim();
        if (!TextUtils.isEmpty(text) || selectedAttachmentName != null) {
            etMessage.setText("");
            submitUserPrompt(text);
        }
    }

    private void submitUserPrompt(String prompt) {
        String fullUserPrompt = prompt;
        if (selectedAttachmentUri != null) {
            String attachmentName = selectedAttachmentName != null ? selectedAttachmentName : "Attachment";
            String extractedContent = extractTextFromUri(selectedAttachmentUri, attachmentName);
            clearAttachment();

            if (!extractedContent.isEmpty()) {
                fullUserPrompt = prompt + "\n\n[Attached File Scan Content (" + attachmentName + ")]:\n" + extractedContent;
            } else {
                fullUserPrompt = "[Attached File: " + attachmentName + "]\n" + prompt;
            }
        }

        // Create session if starting fresh
        if (currentSession == null) {
            String title = prompt.length() > 25 ? prompt.substring(0, 25) + "..." : prompt;
            if (title.isEmpty()) title = "New Conversation";
            currentSession = new ChatSession(String.valueOf(System.currentTimeMillis()), title, System.currentTimeMillis());
            sessionHistory.add(0, currentSession);
            historyAdapter.notifyDataSetChanged();
        }

        // Hide ChatGPT Welcome Screen, show Chat List
        if (layoutWelcomeState != null) {
            layoutWelcomeState.setVisibility(View.GONE);
        }
        if (rvChat != null) {
            rvChat.setVisibility(View.VISIBLE);
        }

        addUserMessage(fullUserPrompt);

        String command = prompt.toLowerCase(Locale.ROOT).trim();

        // Clean leading wake words / greetings if present
        String cleanCommand = command
                .replaceAll("^(please|can you|could you|kukki|hey kukki|hi kukki|ok kukki|hello kukki)\\s+", "")
                .trim();

        // Check for Default Assistant Settings request
        if (cleanCommand.contains("default assistant") || cleanCommand.contains("power button") || cleanCommand.contains("set assistant")) {
            openAssistantSettings();
            String reply = "Opening your phone's Digital Assistant settings. Please select Kukki as your default Voice Assistant.";
            addAssistantMessage(reply);
            speak(reply);
            return;
        }

        // 0. Ownership & Origin Questions
        if (isOwnershipQuestion(cleanCommand)) {
            String reply = "I was designed by my master Anshuman, and my name was kept after the name of a bike which was named by my master and his friend and co-founder of KUKKI, master Aayush.";
            addAssistantMessage(reply);
            speak(reply);
            return;
        }

        // 1. Check if user is setting a nickname/name ("call me master", "my name is master", etc.)
        if (cleanCommand.startsWith("call me ") || cleanCommand.startsWith("from now on call me ") || cleanCommand.startsWith("my name is ")) {
            String nickname = cleanCommand
                    .replaceAll("^(from now on call me|call me|my name is)\\s+", "")
                    .trim()
                    .replaceAll("[.!?,]+$", "")
                    .trim();

            if (!nickname.isEmpty()) {
                saveUserNickname(nickname);
                updateKukkiAISystemPrompt();
                String reply = "Got it! I will call you " + nickname + " from now on. How can I help you, " + nickname + "?";
                addAssistantMessage(reply);
                speak(reply);
                return;
            }
        }

        // 2. Check if user asks about their name
        if (cleanCommand.equalsIgnoreCase("what is my name") || cleanCommand.equalsIgnoreCase("what's my name")
                || cleanCommand.equalsIgnoreCase("what do you call me") || cleanCommand.equalsIgnoreCase("who am i")) {
            String nickname = getUserNickname();
            String reply;
            if (nickname != null && !nickname.isEmpty()) {
                reply = "You told me to call you " + nickname + "!";
            } else {
                reply = "I don't know your name yet. You can tell me by saying 'call me [your name]'.";
            }
            addAssistantMessage(reply);
            speak(reply);
            return;
        }

        // 3. Greetings: e.g. "hey kukki", "hi kukki", "hello kukki", "hey", "hi", "hello"
        if (cleanCommand.isEmpty() || cleanCommand.equalsIgnoreCase("hey") || cleanCommand.equalsIgnoreCase("hi")
                || cleanCommand.equalsIgnoreCase("hello") || cleanCommand.equalsIgnoreCase("good morning")
                || cleanCommand.equalsIgnoreCase("good evening") || cleanCommand.equalsIgnoreCase("good afternoon")) {
            String nickname = getUserNickname();
            String greetingName = (nickname != null && !nickname.isEmpty()) ? " " + nickname : "";
            String reply = "Hey" + greetingName + "! How can I help you today?";
            addAssistantMessage(reply);
            speak(reply);
            return;
        }

        // 4. WhatsApp commands: e.g. "call Mom on WhatsApp", "message Alex on WhatsApp Hello"
        if (isWhatsAppCommand(cleanCommand)) {
            handleWhatsAppCommand(cleanCommand);
            return;
        }

        // 5. Phone call commands: e.g. "call Mom", "please call Dad", "dial 9876543210"
        if (isPhoneCallCommand(command)) {
            boolean onSpeaker = cleanCommand.contains("speaker") || cleanCommand.contains("loudspeaker") || cleanCommand.contains("speakerphone");
            String target = cleanCommand
                    .replaceAll("^(make a call to|phone call to|make a call|place a call to|place a call|call to|call|dial|ring|phone)\\s+", "")
                    .replaceAll("\\s+(on|and put it on|put on|and put on)\\s+(speaker|speakerphone|loudspeaker).*$", "")
                    .trim();

            if (!target.isEmpty()) {
                handleCallCommand(target, onSpeaker);
                return;
            }
        }

        // 5. Call log commands: e.g. "call logs", "show call logs", "recent calls", "who called me"
        if (cleanCommand.contains("call log") || cleanCommand.contains("recent calls") || cleanCommand.contains("who called me") || cleanCommand.contains("call history")) {
            handleCallLogsCommand();
            return;
        }

        // 6. App launching: e.g. "open WhatsApp", "open Instagram", "launch Spotify", "open Camera"
        if (cleanCommand.startsWith("open ") || cleanCommand.startsWith("launch ") || cleanCommand.contains("open app ") || cleanCommand.contains("open the ")) {
            String appName = cleanCommand
                    .replaceAll("^(open app|open the|open|launch)\\s+", "")
                    .trim();

            if (launchApp(appName)) {
                return;
            }
        }

        // 7. Default AI Conversation
        if (kukkiAI != null && kukkiAI.isReady()) {
            final int assistantIndex = addAssistantMessage("Thinking...");
            final String promptToSubmit = fullUserPrompt;

            new Thread(() -> {
                String webContext = "";
                if (shouldSearchWeb(promptToSubmit)) {
                    webContext = fetchGlobalWebKnowledge(promptToSubmit);
                }

                final String finalPromptWithContext;
                if (!webContext.isEmpty()) {
                    finalPromptWithContext = promptToSubmit + "\n\n[Global Internet Knowledge Context]:\n" + webContext;
                } else {
                    finalPromptWithContext = promptToSubmit;
                }

                runOnUiThread(() -> {
                    kukkiAI.ask(finalPromptWithContext, new KukkiAI.Callback() {
                        @Override
                        public void onStatus(String message) {
                            updateAssistantMessage(assistantIndex, message);
                        }

                        @Override
                        public void onResponse(String text) {
                            updateAssistantMessage(assistantIndex, text);
                            speak(text);
                        }

                        @Override
                        public void onError(String message) {
                            String nickname = getUserNickname();
                            String reply = "That's a fascinating question! While I'm pondering all the details, is there a particular part of it you'd like to explore" +
                                    (nickname != null ? ", " + nickname : "") + "?";
                            updateAssistantMessage(assistantIndex, reply);
                            speak(reply);
                        }
                    });
                });
            }).start();
        } else {
            String reply = "Kukki AI model is not ready yet. Please select an AI model.";
            addAssistantMessage(reply);
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
                addAssistantMessage(reply);
                speak(reply);
                startActivity(launchIntent);
                return true;
            }
        }

        // System app shortcuts
        if (query.contains("camera")) {
            String reply = "Opening Camera.";
            addAssistantMessage(reply);
            speak(reply);
            startActivity(new Intent(MediaStore.ACTION_IMAGE_CAPTURE));
            return true;
        } else if (query.contains("setting")) {
            String reply = "Opening Settings.";
            addAssistantMessage(reply);
            speak(reply);
            startActivity(new Intent(Settings.ACTION_SETTINGS));
            return true;
        } else if (query.contains("youtube")) {
            String reply = "Opening YouTube.";
            addAssistantMessage(reply);
            speak(reply);
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")));
            return true;
        }

        return false;
    }

    private void handleCallCommand(String target) {
        handleCallCommand(target, false);
    }

    private void handleCallCommand(String target, boolean onSpeaker) {
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            pendingCallTarget = target;
            requestPermissions(
                    new String[]{
                            Manifest.permission.CALL_PHONE,
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.READ_CALL_LOG
                    },
                    REQUEST_CALL_PERMISSIONS
            );
            return;
        }

        String query = target.trim();
        if (TextUtils.isEmpty(query)) {
            String reply = "Who would you like me to call?";
            addAssistantMessage(reply);
            speak(reply);
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
            addAssistantMessage(reply);
            speak(reply);
        }
    }

    private void makePhoneCall(String number, String nameOrQuery) {
        makePhoneCall(number, nameOrQuery, false);
    }

    private void makePhoneCall(String number, String nameOrQuery, boolean onSpeaker) {
        String reply = "Calling " + nameOrQuery + (onSpeaker ? " on speaker..." : "...");
        addAssistantMessage(reply);
        speak(reply);

        if (onSpeaker) {
            try {
                android.media.AudioManager audioManager = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
                if (audioManager != null) {
                    audioManager.setMode(android.media.AudioManager.MODE_IN_CALL);
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
                            android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
                            if (am != null) {
                                am.setMode(android.media.AudioManager.MODE_IN_CALL);
                                am.setSpeakerphoneOn(true);
                            }
                        } catch (Exception ignored) {}
                    }, 1000);
                }

                return;
            } catch (Exception e) {
                // fallback
            }
        }
        Intent genericCall = new Intent(Intent.ACTION_DIAL, uri);
        startActivity(genericCall);
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

        try (Cursor cursor = getContentResolver().query(
                uri, projection, null, null, null
        )) {
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

        // Containment check only if strings are long enough and ratios match closely
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



    private void handleCallLogsCommand() {
        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            StringBuilder sb = new StringBuilder("Recent Call Logs:\n");
            Uri uri = CallLog.Calls.CONTENT_URI;
            String[] projection = new String[]{
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE
            };

            try (Cursor cursor = getContentResolver().query(
                    uri, projection, null, null, CallLog.Calls.DATE + " DESC LIMIT 5"
            )) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);
                    int numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER);
                    int typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE);

                    int count = 1;
                    do {
                        String name = cursor.getString(nameIndex);
                        String number = cursor.getString(numberIndex);
                        int type = cursor.getInt(typeIndex);

                        String typeStr;
                        switch (type) {
                            case CallLog.Calls.INCOMING_TYPE:
                                typeStr = "Incoming";
                                break;
                            case CallLog.Calls.OUTGOING_TYPE:
                                typeStr = "Outgoing";
                                break;
                            case CallLog.Calls.MISSED_TYPE:
                                typeStr = "Missed";
                                break;
                            default:
                                typeStr = "Call";
                                break;
                        }

                        String displayName = (name != null && !name.isEmpty()) ? name : number;
                        sb.append(count).append(". ").append(typeStr).append(": ").append(displayName).append("\n");
                        count++;
                    } while (cursor.moveToNext());

                    String resultText = sb.toString().trim();
                    addAssistantMessage(resultText);
                    speak("Here are your recent calls.");
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        String reply = "Opening call logs...";
        addAssistantMessage(reply);
        speak(reply);
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, CallLog.Calls.CONTENT_URI);
            startActivity(intent);
        } catch (Exception e) {
            Intent dialIntent = new Intent(Intent.ACTION_DIAL);
            startActivity(dialIntent);
        }
    }

    private void addUserMessage(String text) {
        ChatMessage msg = new ChatMessage(ChatMessage.TYPE_USER, text);
        messageList.add(msg);
        if (currentSession != null) {
            currentSession.getMessages().add(msg);
        }
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        rvChat.smoothScrollToPosition(messageList.size() - 1);
    }

    private int addAssistantMessage(String text) {
        ChatMessage msg = new ChatMessage(ChatMessage.TYPE_ASSISTANT, text);
        messageList.add(msg);
        if (currentSession != null) {
            currentSession.getMessages().add(msg);
        }
        int index = messageList.size() - 1;
        chatAdapter.notifyItemInserted(index);
        rvChat.smoothScrollToPosition(index);
        return index;
    }

    private void updateAssistantMessage(int position, String text) {
        if (position >= 0 && position < messageList.size()) {
            messageList.get(position).setText(text);
            chatAdapter.notifyItemChanged(position);
            rvChat.scrollToPosition(position);
        }
    }

    private void checkAndAutoLoadModel() {
        File modelDirectory = new File(getFilesDir(), "models");
        if (modelDirectory.exists()) {
            File[] files = modelDirectory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".gguf"));
            if (files != null && files.length > 0) {
                File latestModel = files[0];
                for (File f : files) {
                    if (f.lastModified() > latestModel.lastModified()) {
                        latestModel = f;
                    }
                }
                final File autoModel = latestModel;
                btnModelSelector.setText("Kukki AI ✨");
                updateKukkiAISystemPrompt();
                kukkiAI.loadModel(autoModel.getAbsolutePath(), new KukkiAI.Callback() {
                    @Override
                    public void onStatus(String message) {
                    }

                    @Override
                    public void onResponse(String text) {
                    }

                    @Override
                    public void onError(String message) {
                    }
                });
            } else {
                btnModelSelector.setText("Kukki AI ✨");
            }
        } else {
            btnModelSelector.setText("Kukki AI ✨");
        }
    }

    private void selectModel() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_MODEL);
    }

    private void startVoiceInput() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_MICROPHONE
            );
            return;
        }

        if (speechRecognizer == null) {
            setupSpeechRecognizer();
        }

        if (isListening) {
            speechRecognizer.stopListening();
            isListening = false;
            btnMic.setAlpha(1.0f);
            if (cvListeningCard != null) {
                cvListeningCard.setVisibility(View.GONE);
            }
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN");
        intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true);

        // Hardware Noise Suppression DSP AudioSource
        intent.putExtra("android.speech.extra.AUDIO_SOURCE", android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION);

        // Silence timing to filter background noise and focus on user's voice
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L);

        try {
            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Voice input error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_MICROPHONE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceInput();
        } else if (requestCode == REQUEST_CAMERA_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else if (requestCode == REQUEST_CALL_PERMISSIONS
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (pendingCallTarget != null) {
                String target = pendingCallTarget;
                pendingCallTarget = null;
                handleCallCommand(target);
            }
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_CAMERA) {
                if (data != null && data.getExtras() != null && data.getExtras().get("data") != null) {
                    setAttachment(null, "Camera_Photo.jpg", android.R.drawable.ic_menu_camera);
                } else {
                    setAttachment(null, "Captured_Photo.jpg", android.R.drawable.ic_menu_camera);
                }
            } else if (requestCode == REQUEST_GALLERY && data != null && data.getData() != null) {
                Uri imageUri = data.getData();
                String name = getFileNameFromUri(imageUri, "Image.jpg");
                setAttachment(imageUri, name, android.R.drawable.ic_menu_gallery);
            } else if (requestCode == REQUEST_DOC && data != null && data.getData() != null) {
                Uri docUri = data.getData();
                String name = getFileNameFromUri(docUri, "Document.pdf");
                setAttachment(docUri, name, android.R.drawable.ic_input_add);
            } else if (requestCode == REQUEST_MODEL && data != null && data.getData() != null) {
                Uri modelUri = data.getData();

                new Thread(() -> {
                    try {
                        File modelFile = copyModelToAppStorage(modelUri);

                        runOnUiThread(() -> {
                            btnModelSelector.setText("Kukki AI ✨");
                            updateKukkiAISystemPrompt();

                            kukkiAI.loadModel(modelFile.getAbsolutePath(),
                                    new KukkiAI.Callback() {

                                @Override
                                public void onStatus(String message) {
                                }

                                @Override
                                public void onResponse(String text) {
                                }

                                @Override
                                public void onError(String message) {
                                }
                            });
                        });

                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "Model copy failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                }).start();
            }
        }
    }

    private boolean isContainsHindi(String text) {
        if (text == null) return false;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.DEVANAGARI) {
                return true;
            }
        }
        return false;
    }

    private void speak(String text) {
        if (ttsReady && tts != null) {
            if (isContainsHindi(text)) {
                tts.setLanguage(Locale.forLanguageTag("hi-IN"));
            } else {
                tts.setLanguage(Locale.forLanguageTag("en-IN"));
            }

            tts.speak(
                    text,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "assistant_reply"
            );
        }
    }

    private String getFileNameFromUri(Uri uri, String fallback) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    String name = cursor.getString(nameIndex);
                    if (!TextUtils.isEmpty(name)) {
                        return name;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private File copyModelToAppStorage(Uri modelUri) throws Exception {
        String fileName = "model.gguf";

        try (Cursor cursor = getContentResolver().query(
                modelUri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    String selectedName = cursor.getString(nameIndex);
                    if (selectedName != null &&
                            selectedName.toLowerCase(Locale.ROOT).endsWith(".gguf")) {
                        fileName = selectedName;
                    } else {
                        throw new IllegalArgumentException(
                                "Please select a .gguf model file.");
                    }
                }
            }
        }

        File modelDirectory = new File(getFilesDir(), "models");
        if (!modelDirectory.exists() && !modelDirectory.mkdirs()) {
            throw new Exception("Could not create model folder.");
        }

        File destination = new File(modelDirectory, fileName);

        try (InputStream input = getContentResolver()
                    .openInputStream(modelUri);
             FileOutputStream output = new FileOutputStream(destination)) {

            if (input == null) {
                throw new Exception("Could not open selected model file.");
            }

            byte[] buffer = new byte[1024 * 1024];
            int bytesRead;

            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            output.getFD().sync();
        }

        return destination;
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
