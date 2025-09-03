package com.example.mp4converterapp;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;


import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity {
    private Button btnPickVideo, btnCreateDest, btnExtract;
    private TextView txtVideoUri, txtDestUri, txtLog;
    private ProgressBar progress;

    @Nullable private Uri inputVideoUri = null;
    @Nullable private Uri outputAudioUri = null;

    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    private ActivityResultLauncher<String> pickVideoLauncher;
    private ActivityResultLauncher<Intent> createFileLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        btnPickVideo = findViewById(R.id.btnPickVideo);
        btnCreateDest = findViewById(R.id.btnCreateDest);
        btnExtract = findViewById(R.id.btnExtract);
        txtVideoUri = findViewById(R.id.txtVideoUri);
        txtDestUri = findViewById(R.id.txtDestUri);
        txtLog = findViewById(R.id.txtLog);
        progress = findViewById(R.id.progress);

        setupLaunchers();

        btnPickVideo.setOnClickListener(v -> pickVideoLauncher.launch("video/*"));
        btnCreateDest.setOnClickListener(v -> launchCreateFile());
        btnExtract.setOnClickListener(v -> runExtraction());

        updateExtractEnabled();
    }
    private void setupLaunchers() {
        // 1) Pick MP4 (or any video)
        pickVideoLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        inputVideoUri = uri;
                        takePersistableRead(uri);
                        txtVideoUri.setText("Video: " + uri);
                    }
                    updateExtractEnabled();
                });

        // 2) Create destination .m4a
        createFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            outputAudioUri = uri;
                            takePersistableWrite(uri);
                            txtDestUri.setText("Output: " + uri);
                        }
                    }
                    updateExtractEnabled();
                });
    }

    private void launchCreateFile() {
        String defaultName = "extracted_audio.m4a";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/mp4"); // m4a is audio-only MP4
        intent.putExtra(Intent.EXTRA_TITLE, defaultName);
        // Optional: initial dir
//        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, (Uri) null);
        createFileLauncher.launch(intent);
    }

    private void runExtraction() {
        if (inputVideoUri == null || outputAudioUri == null) {
            Toast.makeText(this, "Select input and output first.", Toast.LENGTH_SHORT).show();
            return;
        }
        setBusy(true);
        txtLog.setText("Starting extraction...\n");

        exec.submit(() -> {
            boolean ok = AudioExtractor.extractToM4A(
                    MainActivity.this,
                    inputVideoUri,
                    outputAudioUri,
                    msg -> runOnUiThread(() -> appendLog(msg))
            );
            runOnUiThread(() -> {
                setBusy(false);
                if (ok) {
                    appendLog("Success ✅ Saved to:\n" + outputAudioUri);
                    Toast.makeText(MainActivity.this, "Extraction complete!", Toast.LENGTH_LONG).show();
                } else {
                    appendLog("Failed ❌");
                    Toast.makeText(MainActivity.this, "Extraction failed.", Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void appendLog(String s) {
        txtLog.setText(txtLog.getText() + "\n" + s);
    }

    private void setBusy(boolean b) {
        progress.setVisibility(b ? ProgressBar.VISIBLE : ProgressBar.GONE);
        btnExtract.setEnabled(!b);
        btnPickVideo.setEnabled(!b);
        btnCreateDest.setEnabled(!b);
    }

    private void updateExtractEnabled() {
        btnExtract.setEnabled(inputVideoUri != null && outputAudioUri != null);
    }

    private void takePersistableRead(Uri uri) {
        final int flags = (Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Exception ignored) {}
    }

    private void takePersistableWrite(Uri uri) {
        final int flags = (Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        exec.shutdown();
    }
}