package com.akanshaa.mobile;

import java.util.ArrayList;
import java.util.List;

/**
 * Small in-memory cache of notifications the reader has seen, so you can ask
 * "what did Rahul send?" without re-reading the system tray. Cleared on process death.
 */
public final class MessageStore {

    public static final class Item {
        public final String app;
        public final String title;
        public final String text;
        public final long when;

        Item(String app, String title, String text, long when) {
            this.app = app; this.title = title; this.text = text; this.when = when;
        }

        public String describe() {
            StringBuilder sb = new StringBuilder();
            if (title != null && title.length() > 0) sb.append(title).append(": ");
            if (text != null) sb.append(text);
            return sb.toString().trim();
        }
    }

    private static final List<Item> ITEMS = new ArrayList<Item>();
    private static final int KEEP = 40;

    public static synchronized void add(String app, String title, String text) {
        ITEMS.add(0, new Item(app, title, text, System.currentTimeMillis()));
        while (ITEMS.size() > KEEP) ITEMS.remove(ITEMS.size() - 1);
    }

    public static synchronized List<Item> recent(String contains) {
        List<Item> out = new ArrayList<Item>();
        String needle = contains == null ? "" : contains.toLowerCase();
        for (Item i : ITEMS) {
            if (needle.length() == 0) { out.add(i); continue; }
            String hay = (String.valueOf(i.app) + " " + String.valueOf(i.title) + " "
                    + String.valueOf(i.text)).toLowerCase();
            if (hay.contains(needle)) out.add(i);
        }
        return out;
    }

    public static synchronized int count() { return ITEMS.size(); }

    public static synchronized void clear() { ITEMS.clear(); }
}
