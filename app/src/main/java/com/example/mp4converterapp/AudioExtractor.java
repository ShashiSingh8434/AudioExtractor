package com.example.mp4converterapp;

import android.content.ContentResolver;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;

public class AudioExtractor {

    private static final String TAG = "AudioExtractor";

    public interface Listener {
        void onLog(String msg);
    }

    public static boolean extractToM4A(Context ctx, Uri inputVideoUri, Uri outputAudioUri, Listener listener) {
        ContentResolver resolver = ctx.getContentResolver();
        MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = null;

        try (var inPfd = resolver.openFileDescriptor(inputVideoUri, "r");
             var outPfd = resolver.openFileDescriptor(outputAudioUri, "rw")) {

            if (inPfd == null || outPfd == null) {
                log(listener, "Failed to open file descriptors.");
                return false;
            }

            FileDescriptor inFd = inPfd.getFileDescriptor();
            FileDescriptor outFd = outPfd.getFileDescriptor();

            extractor.setDataSource(inFd);

            // Find audio track
            int trackCount = extractor.getTrackCount();
            int audioTrackIndex = -1;
            MediaFormat audioFormat = null;

            for (int i = 0; i < trackCount; i++) {
                MediaFormat fmt = extractor.getTrackFormat(i);
                String mime = fmt.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    audioTrackIndex = i;
                    audioFormat = fmt;
                    break;
                }
            }

            if (audioTrackIndex == -1) {
                log(listener, "No audio track found in this video.");
                return false;
            }

            String mime = audioFormat.getString(MediaFormat.KEY_MIME);
            log(listener, "Audio MIME: " + mime);

            // We are re-muxing (no re-encode). Works best if the audio is AAC.
            // We'll output an audio-only MP4 (.m4a file name).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                muxer = new MediaMuxer(outFd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            }
            int outTrackIndex = muxer.addTrack(audioFormat);
            muxer.start();

            extractor.selectTrack(audioTrackIndex);
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

            int maxSize = 256 * 1024; // default
            if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                maxSize = Math.max(maxSize, audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
            }
            ByteBuffer buffer = ByteBuffer.allocateDirect(maxSize);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            long samples = 0;
            while (true) {
                info.offset = 0;
                info.size = extractor.readSampleData(buffer, 0);
                if (info.size < 0) {
                    break; // EOF
                }
                info.presentationTimeUs = extractor.getSampleTime();
                info.flags = 0;
                muxer.writeSampleData(outTrackIndex, buffer, info);
                extractor.advance();
                samples++;
                if (samples % 256 == 0) {
                    log(listener, "Processed samples: " + samples);
                }
            }

            log(listener, "Done. Samples written: " + samples);
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Extraction error", e);
            log(listener, "Error: " + e.getMessage());
            return false;
        } finally {
            try {
                extractor.release();
            } catch (Exception ignored) {}
            try {
                if (muxer != null) {
                    muxer.stop();
                    muxer.release();
                }
            } catch (Exception e) {
                Log.w(TAG, "Muxer close warning", e);
            }
        }
    }

    private static void log(Listener l, String msg) {
        if (l != null) l.onLog(msg);
        Log.d(TAG, msg);
    }
}
