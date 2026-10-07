package com.akanshaa.mobile;

import android.content.Context;

/**
 * The single brain instance shared by the main screen and the floating orb, so both
 * drive the same conversation and neither can speak over the other.
 */
public final class Assistant {

    public interface Ui {
        void onState(String state);
        void onUser(String text);
        void onAkanshaa(String text);
        void openSettings();
    }

    private static Assistant instance;

    public static synchronized Assistant get(Context c) {
        if (instance == null) instance = new Assistant(c.getApplicationContext());
        return instance;
    }

    private final Prefs prefs;
    private final Voice voice;
    private final Gemini gemini;
    private final Actions actions;
    private final java.util.List<Ui> listeners = new java.util.ArrayList<Ui>();
    private boolean busy;

    private Assistant(Context ctx) {
        prefs = new Prefs(ctx);
        voice = new Voice(ctx, prefs);
        gemini = new Gemini(prefs);
        actions = new Actions(ctx, prefs);
    }

    public Prefs prefs() { return prefs; }
    public Voice voice() { return voice; }
    public Gemini gemini() { return gemini; }
    public boolean available() { return voice.available(); }

    public synchronized void addUi(Ui u) {
        if (u != null && !listeners.contains(u)) listeners.add(u);
    }

    public synchronized void removeUi(Ui u) { listeners.remove(u); }

    private interface Fan { void call(Ui u); }

    private void fanout(Fan f) {
        Ui[] snapshot;
        synchronized (this) { snapshot = listeners.toArray(new Ui[listeners.size()]); }
        for (Ui u : snapshot) { if (u != null) f.call(u); }
    }

    public void say(String text) {
        if (prefs.speakReplies()) voice.speak(text);
    }

    public void stop() {
        voice.stopSpeaking();
        voice.stopListening();
        busy = false;
        state("ready");
    }

    /** Record one utterance and act on it. */
    public void listenOnce() {
        if (busy) { state("already busy - one thing at a time"); return; }
        if (!prefs.hasKey()) {
            state("no Gemini key yet");
            speakAndShow("Add your Gemini key in settings first, then I can hear you.");
            fanout(new Fan() { @Override public void call(Ui u) { u.openSettings(); } });
            return;
        }
        voice.stopSpeaking();
        state("listening");
        busy = true;
        voice.listen(new Voice.Heard() {
            @Override public void onHeard(String text) { busy = false; handle(text); }
            @Override public void onMissed(String reason) {
                busy = false;
                state("ready");
                speakAndShow(reason);
            }
        });
    }

    /** Same pipeline, but the text came from the keyboard instead of the microphone. */
    public void submit(String text) { handle(text); }

    private void handle(String heard) {
        if (heard == null || heard.trim().length() == 0) { state("ready"); return; }
        final String text = heard.trim();
        fanout(new Fan() { @Override public void call(Ui u) { u.onUser(text); } });
        prefs.rememberTurn("user", text);
        state("thinking");

        Actions.Reply reply = new Actions.Reply() {
            @Override public void say(String t, boolean speak) {
                busy = false;
                state("ready");
                if (t == null || t.length() == 0) { voice.stopSpeaking(); return; }
                prefs.rememberTurn("akanshaa", t);
                speakAndShow(t, !speak);
            }
            @Override public void openSettings() {
                fanout(new Fan() { @Override public void call(Ui u) { u.openSettings(); } });
            }
        };

        if (actions.run(text, reply)) return;

        if (!prefs.hasKey()) {
            reply.say("I have no Gemini key, so I cannot think. Add one in settings.", true);
            return;
        }

        gemini.ask(text, new Gemini.Reply() {
            @Override public void onResult(String result, String error) {
                busy = false;
                state("ready");
                String outText = result != null ? result : "Error: " + error;
                prefs.rememberTurn("akanshaa", outText);
                speakAndShow(outText, false);
            }
        });
    }

    private void speakAndShow(final String t) { speakAndShow(t, false); }

    /** Shows the line on every registered surface, and speaks it unless asked not to. */
    private void speakAndShow(final String t, boolean silent) {
        fanout(new Fan() { @Override public void call(Ui u) { u.onAkanshaa(t); } });
        if (!silent && prefs.speakReplies()) voice.speak(t);
    }

    private void state(final String s) {
        fanout(new Fan() { @Override public void call(Ui u) { u.onState(s); } });
    }
}
