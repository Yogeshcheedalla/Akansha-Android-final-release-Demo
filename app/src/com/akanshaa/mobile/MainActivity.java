package com.akanshaa.mobile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * One screen, built in code so there is no layout XML to get out of sync:
 * key setup, permission status, a chat log, and the switches.
 */
public class MainActivity extends Activity implements Assistant.Ui {

    private static final int REQ_PERMS = 71;

    private Assistant assistant;
    private Prefs prefs;

    private TextView stateLine;
    private TextView log;
    private EditText keyBox;
    private EditText modelBox;
    private EditText cityBox;
    private EditText askBox;
    private TextView permStatus;
    private CheckBox speakBox;
    private CheckBox whatsBox;
    private CheckBox orbBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        assistant = Assistant.get(this);
        prefs = assistant.prefs();
        setContentView(build());
        assistant.addUi(this);
        onState("ready");
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissions();
    }

    @Override
    protected void onDestroy() {
        assistant.removeUi(this);
        super.onDestroy();
    }

    // ---- layout ------------------------------------------------------------

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#070B18"));
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Akanshaa");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.parseColor("#35E0F2"));
        root.addView(title);

        stateLine = new TextView(this);
        stateLine.setTextSize(13);
        stateLine.setTextColor(Color.parseColor("#8FA3C0"));
        root.addView(stateLine);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams fill = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scroll, fill);
        scroll.addView(body);

        // --- Gemini key -----------------------------------------------------
        LinearLayout key = card(body, "Your Gemini key");
        keyBox = input(key, "paste your AI Studio key", false);
        keyBox.setText(prefs.apiKey());
        Button save = button(key, "Save key");
        save.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                prefs.setApiKey(keyBox.getText().toString());
                toast(prefs.hasKey() ? "Key saved." : "Key is empty - I can only run local commands.");
            }
        });
        Button test = button(key, "Test key and list models");
        test.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                prefs.setApiKey(keyBox.getText().toString());
                if (!prefs.hasKey()) { toast("Put a key in first."); return; }
                append("checking key...");
                assistant.gemini().listModels(new Gemini.Reply() {
                    @Override public void onResult(String text, String error) {
                        if (error != null) { append("KEY PROBLEM: " + error); return; }
                        append("key works. models:\n" + text);
                    }
                });
            }
        });

        // --- model ----------------------------------------------------------
        LinearLayout model = card(body, "Model");
        modelBox = input(model, prefs.DEFAULT_MODEL, false);
        modelBox.setText(prefs.model());
        Button saveModel = button(model, "Save model");
        saveModel.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                prefs.setModel(modelBox.getText().toString());
                toast("Using " + prefs.model());
            }
        });

        // --- talk -----------------------------------------------------------
        LinearLayout talk = card(body, "Talk");
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button mic = button(row, "Hold to talk");
        Button stop = button(row, "Stop");
        talk.addView(row);
        mic.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { assistant.listenOnce(); }
        });
        stop.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { assistant.stop(); }
        });

        askBox = input(talk, "or type to Akanshaa", false);
        Button send = button(talk, "Send");
        send.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                String t = askBox.getText().toString().trim();
                if (t.length() == 0) return;
                askBox.setText("");
                assistant.submit(t);
            }
        });

        // --- log ------------------------------------------------------------
        card(body, "Conversation");
        log = new TextView(this);
        log.setTextSize(14);
        log.setTextColor(Color.parseColor("#E6F1FF"));
        log.setLineSpacing(0, 1.15f);
        log.setText("Say: call Rahul  -  text hi to Priya  -  open whatsapp  -  weather today  -  what did Priya send");
        LinearLayout logBox = new LinearLayout(this);
        logBox.setOrientation(LinearLayout.VERTICAL);
        logBox.addView(log);
        body.addView(logBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // --- switches -------------------------------------------------------
        LinearLayout sw = card(body, "Switches");
        speakBox = check(sw, "Speak replies out loud", prefs.speakReplies());
        speakBox.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean v) {
                prefs.setSpeakReplies(v);
            }
        });
        whatsBox = check(sw, "Read WhatsApp and missed calls", prefs.readWhats());
        whatsBox.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean v) {
                prefs.setReadWhats(v);
            }
        });
        orbBox = check(sw, "Floating orb over other apps", prefs.orbEnabled());
        orbBox.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean v) {
                prefs.setOrbEnabled(v);
                if (!v) { OrbService.stop(MainActivity.this); return; }
                if (!OrbService.overlayAllowed(MainActivity.this)) {
                    toast("Allow the overlay permission first.");
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())));
                    b.setChecked(false);
                    prefs.setOrbEnabled(false);
                    return;
                }
                OrbService.start(MainActivity.this);
            }
        });

        // --- weather + language --------------------------------------------
        LinearLayout wx = card(body, "Weather and language");
        cityBox = input(wx, "city, e.g. Hyderabad", false);
        cityBox.setText(prefs.city());
        Button saveCity = button(wx, "Save city");
        saveCity.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                prefs.setCity(cityBox.getText().toString());
                prefs.setLatLon(0f, 0f);
                toast("Weather will use " + prefs.city());
            }
        });
        LinearLayout langs = new LinearLayout(this);
        langs.setOrientation(LinearLayout.HORIZONTAL);
        Button en = button(langs, "English (en-IN)");
        Button hi = button(langs, "Hindi (hi-IN)");
        wx.addView(langs);
        en.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { prefs.setLanguage("en-IN"); toast("en-IN"); }
        });
        hi.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { prefs.setLanguage("hi-IN"); toast("hi-IN"); }
        });

        // --- permissions ----------------------------------------------------
        LinearLayout perms = card(body, "Permissions");
        permStatus = new TextView(this);
        permStatus.setTextSize(13);
        permStatus.setTextColor(Color.parseColor("#8FA3C0"));
        perms.addView(permStatus);
        Button grant = button(perms, "Grant the obvious ones");
        grant.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { requestObvious(); }
        });
        Button openNotif = button(perms, "Open notification-access settings");
        openNotif.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                } catch (Exception e) {
                    toast("This Android version has no such screen.");
                }
            }
        });
        Button openOverlay = button(perms, "Allow drawing over other apps");
        openOverlay.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        Button clear = button(perms, "Forget conversation");
        clear.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                prefs.clearHistory();
                MessageStore.clear();
                log.setText("");
                toast("Cleared.");
            }
        });
        refreshPermissions();

        return root;
    }

    private void refreshPermissions() {
        permStatus.setText(
                row("Microphone", granted(android.Manifest.permission.RECORD_AUDIO)) + "\n"
              + row("Phone state", granted(android.Manifest.permission.CALL_PHONE)
                    ? "granted - calls go straight through" : "not granted - the dialler opens instead") + "\n"
              + row("SMS", granted(android.Manifest.permission.SEND_SMS)) + "\n"
              + row("Contacts", granted(android.Manifest.permission.READ_CONTACTS)) + "\n"
              + row("Location", granted(android.Manifest.permission.ACCESS_FINE_LOCATION)) + "\n"
              + row("Notifications shown", Build.VERSION.SDK_INT < 33
                    || granted("android.permission.POST_NOTIFICATIONS")) + "\n"
              + row("Draw over apps", OrbService.overlayAllowed(this) ? "granted" : "not granted") + "\n"
              + row("Notification access", notificationAccessOn() ? "granted" : "not granted"));
    }

    private static String row(String label, boolean on) {
        return label + ": " + (on ? "on" : "OFF");
    }

    private static String row(String label, String value) {
        return label + ": " + value;
    }

    private boolean notificationAccessOn() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        return enabled != null && enabled.contains(getPackageName());
    }

    private boolean granted(String permission) {
        return checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void requestObvious() {
        List<String> want = new ArrayList<String>();
        add(want, android.Manifest.permission.RECORD_AUDIO);
        add(want, android.Manifest.permission.CALL_PHONE);
        add(want, android.Manifest.permission.SEND_SMS);
        add(want, android.Manifest.permission.READ_CONTACTS);
        add(want, android.Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33) add(want, "android.permission.POST_NOTIFICATIONS");
        if (want.isEmpty()) { refreshPermissions(); return; }
        requestPermissions(want.toArray(new String[want.size()]), REQ_PERMS);
    }

    private void add(List<String> list, String permission) {
        if (!granted(permission)) list.add(permission);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        refreshPermissions();
    }

    // ---- Assistant.Ui ------------------------------------------------------

    @Override
    public void onState(String state) {
        if (stateLine == null) return;
        stateLine.setText("status: " + state);
    }

    @Override
    public void onUser(String text) { append("you: " + text); }

    @Override
    public void onAkanshaa(String text) { append("akanshaa: " + text); }

    @Override
    public void openSettings() { /* already on the only screen */ }

    private void append(String line) {
        if (log == null) return;
        log.append("\n\n" + line);
    }

    // ---- small builders ----------------------------------------------------

    private LinearLayout card(LinearLayout parent, String heading) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundColor(Color.parseColor("#101A33"));
        int p = dp(12);
        c.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        parent.addView(c, lp);

        TextView h = new TextView(this);
        h.setText(heading.toUpperCase());
        h.setTextSize(11);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setTextColor(Color.parseColor("#8B5CF6"));
        c.addView(h);
        return c;
    }

    private EditText input(LinearLayout parent, String hint, boolean password) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(15);
        e.setSingleLine(!password);
        e.setTextColor(Color.parseColor("#E6F1FF"));
        e.setHintTextColor(Color.parseColor("#5A6C8C"));
        if (password) e.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        parent.addView(e, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    private Button button(LinearLayout parent, String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.parseColor("#E6F1FF"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        parent.addView(b, lp);
        return b;
    }

    private CheckBox check(LinearLayout parent, String label, boolean value) {
        CheckBox c = new CheckBox(this);
        c.setText(label);
        c.setChecked(value);
        c.setTextColor(Color.parseColor("#E6F1FF"));
        parent.addView(c);
        return c;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
