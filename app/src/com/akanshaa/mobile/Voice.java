package com.akanshaa.mobile;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

/**
 * The ears and the mouth. Uses the Android system speech stack, which is free and
 * needs no key of its own. SpeechRecognizer must be touched from a thread with a
 * Looper, so every public method here is expected to run on the main thread.
 */
public final class Voice {

    public interface Heard {
        void onHeard(String text);
        void onMissed(String reason);
    }

    private static final String TAG = "AkanshaaVoice";

    private final Context ctx;
    private final Prefs prefs;
    private SpeechRecognizer rec;
    private TextToSpeech tts;
    private boolean ttsReady;
    private boolean listening;
    private Heard pending;

    public Voice(Context ctx, Prefs prefs) {
        this.ctx = ctx.getApplicationContext();
        this.prefs = prefs;
        initTts();
    }

    // ---- speaking ----------------------------------------------------------

    private void initTts() {
        tts = new TextToSpeech(ctx, new TextToSpeech.OnInitListener() {
            @Override public void onInit(int status) {
                if (status != TextToSpeech.SUCCESS) {
                    Log.w(TAG, "TTS init failed, status=" + status);
                    return;
                }
                ttsReady = applyVoice();
            }
        });
    }

    private boolean applyVoice() {
        if (tts == null) return false;
        Locale loc = toLocale(prefs.language());
        int r = tts.setLanguage(loc);
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            r = tts.setLanguage(Locale.ENGLISH);
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) return false;
        }
        tts.setSpeechRate(1.0f);
        tts.setPitch(1.0f);
        return true;
    }

    public boolean canSpeak() { return ttsReady; }

    public void speak(String text) {
        if (text == null) return;
        String clean = stripForSpeech(text);
        if (clean.length() == 0) return;
        if (!ttsReady) { applyVoice(); if (!ttsReady) return; }
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "akanshaa-reply");
    }

    public void stopSpeaking() {
        if (tts != null) tts.stop();
    }

    /** Removes markdown, emoji and URLs so the voice does not read junk aloud. */
    private static String stripForSpeech(String s) {
        String out = s.replaceAll("(?s)```.*?```", " ")
                      .replaceAll("[*_#`]", " ")
                      .replaceAll("https?://\\S+", " a link ")
                      .replaceAll("[\\u0000-\\u001F\\u2000-\\uFFFF]", " ")
                      .replaceAll("\\s+", " ")
                      .trim();
        return out.length() > 700 ? out.substring(0, 700) : out;
    }

    // ---- hearing -----------------------------------------------------------

    public boolean available() { return SpeechRecognizer.isRecognitionAvailable(ctx); }

    public boolean isListening() { return listening; }

    public void listen(final Heard cb) {
        if (listening) { return; }
        if (!available()) {
            cb.onMissed("No speech recogniser on this phone. Install or enable Google app speech services.");
            return;
        }
        pending = cb;
        if (rec == null) {
            rec = SpeechRecognizer.createSpeechRecognizer(ctx);
            rec.setRecognitionListener(new Listener());
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, prefs.language());
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.getPackageName());
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500);
        listening = true;
        try {
            rec.startListening(i);
        } catch (Exception e) {
            listening = false;
            cb.onMissed("Could not start the microphone: " + e.getMessage());
        }
    }

    public void stopListening() {
        if (rec == null) return;
        try { rec.stopListening(); } catch (Exception ignored) { }
    }

    public void release() {
        if (rec != null) {
            try { rec.destroy(); } catch (Exception ignored) { }
            rec = null;
        }
        if (tts != null) {
            try { tts.shutdown(); } catch (Exception ignored) { }
            tts = null;
            ttsReady = false;
        }
    }

    private final class Listener implements RecognitionListener {
        @Override public void onReadyForSpeech(Bundle params) { }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onRmsChanged(float rmsdB) { }
        @Override public void onBufferReceived(byte[] buffer) { }
        @Override public void onEndOfSpeech() { }

        @Override public void onError(int error) {
            listening = false;
            if (pending != null) pending.onMissed(sttError(error));
            pending = null;
        }

        @Override public void onResults(Bundle results) {
            listening = false;
            String text = first(results);
            Heard cb = pending;
            pending = null;
            if (cb != null) {
                if (text.length() == 0) cb.onMissed("I did not catch that.");
                else cb.onHeard(text);
            }
        }

        @Override public void onPartialResults(Bundle partialResults) { }

        @Override public void onEvent(int eventType, Bundle params) { }
    }

    private static String first(Bundle b) {
        if (b == null) return "";
        java.util.ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return (r == null || r.isEmpty()) ? "" : String.valueOf(r.get(0));
    }

    private static String sttError(int code) {
        switch (code) {
            case SpeechRecognizer.ERROR_AUDIO:              return "Microphone read error.";
            case SpeechRecognizer.ERROR_CLIENT:             return "The speech client failed.";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Microphone permission is not granted.";
            case SpeechRecognizer.ERROR_NETWORK:            return "Speech needs the network and it did not answer.";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:    return "Speech service timed out.";
            case SpeechRecognizer.ERROR_NO_MATCH:           return "I did not catch that.";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:    return "Still busy from the last request.";
            case SpeechRecognizer.ERROR_SERVER:             return "Speech server error.";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:     return "You did not speak in time.";
            default:                                        return "Speech error " + code + ".";
        }
    }

    private static Locale toLocale(String tag) {
        if (tag == null) return Locale.getDefault();
        String[] p = tag.split("[-_]");
        return p.length >= 2 ? new Locale(p[0], p[1]) : new Locale(p[0]);
    }
}
