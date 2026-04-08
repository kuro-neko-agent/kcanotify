package com.antest1.kcanotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import static com.antest1.kcanotify.KcaApiData.getShipTranslation;
import static com.antest1.kcanotify.KcaApiData.getUserShipDataById;
import static com.antest1.kcanotify.KcaConstants.DB_KEY_DECKPORT;
import static com.antest1.kcanotify.KcaConstants.DB_KEY_KDOCKDATA;
import static com.antest1.kcanotify.KcaConstants.DB_KEY_NDOCKDATA;

/**
 * Phase 9D: Dock/Construction/Expedition timer data.
 * Parses ndock, kdock, and api_deck_port into display-ready slot entries.
 */
public class KcaDockTimerData {

    /** Timer state for color coding (poi style). */
    public static final int STATE_EMPTY    = 0; // empty or locked — gray
    public static final int STATE_DONE     = 1; // <=0 remaining — green
    public static final int STATE_NEAR     = 2; // <=1 min remaining — yellow
    public static final int STATE_ACTIVE   = 3; // >1 min remaining — blue
    public static final int STATE_LSC      = 4; // LSC >10 min — red

    public static class DockSlot {
        public final String label;      // ship name / "???" / fleet+expedition label
        public final long completionMs; // epoch ms of completion (0 = empty/done)
        public final int state;         // STATE_* constant

        DockSlot(String label, long completionMs, int state) {
            this.label = label;
            this.completionMs = completionMs;
            this.state = state;
        }
    }

    /** 4 repair dock slots. */
    public static List<DockSlot> getRepairSlots(KcaDBHelper dbHelper) {
        List<DockSlot> slots = new ArrayList<>();
        JsonArray ndock = dbHelper.getJsonArrayValue(DB_KEY_NDOCKDATA);
        if (ndock == null) {
            for (int i = 0; i < 4; i++) slots.add(emptySlot());
            return slots;
        }
        for (int i = 0; i < 4; i++) {
            if (i >= ndock.size()) { slots.add(emptySlot()); continue; }
            JsonObject item = ndock.get(i).getAsJsonObject();
            int apiState = item.get("api_state").getAsInt();
            if (apiState == -1) {
                slots.add(lockedSlot());
                continue;
            }
            int shipId = item.get("api_ship_id").getAsInt();
            if (shipId <= 0) {
                // dock is open but empty
                slots.add(emptySlot());
                continue;
            }
            String name = resolveShipName(shipId);
            long completeMs = item.get("api_complete_time").getAsLong();
            long nowMs = System.currentTimeMillis();
            long remMs = completeMs - nowMs;
            int st;
            if (remMs <= 0)          st = STATE_DONE;
            else if (remMs <= 60000) st = STATE_NEAR;
            else                     st = STATE_ACTIVE;
            slots.add(new DockSlot(name, completeMs, st));
        }
        return slots;
    }

    /** 4 construction dock slots. Always shows ship name directly (no tap-to-reveal). */
    public static List<DockSlot> getConstructionSlots(KcaDBHelper dbHelper) {
        List<DockSlot> slots = new ArrayList<>();
        JsonArray kdock = dbHelper.getJsonArrayValue(DB_KEY_KDOCKDATA);
        if (kdock == null) {
            for (int i = 0; i < 4; i++) slots.add(emptySlot());
            return slots;
        }
        for (int i = 0; i < 4; i++) {
            if (i >= kdock.size()) { slots.add(emptySlot()); continue; }
            JsonObject item = kdock.get(i).getAsJsonObject();
            int apiState = item.get("api_state").getAsInt();
            if (apiState == -1) {
                slots.add(lockedSlot());
                continue;
            }
            int shipId = item.get("api_created_ship_id").getAsInt();
            if (shipId <= 0) {
                slots.add(emptySlot());
                continue;
            }
            // Determine if LSC: api_item1 >= 1000
            boolean isLsc = item.has("api_item1") && item.get("api_item1").getAsInt() >= 1000;
            String name = "—";
            JsonObject kcData = KcaApiData.getKcShipDataById(shipId, "name");
            if (kcData != null && kcData.has("name")) {
                name = KcaApiData.getShipTranslation(kcData.get("name").getAsString(), shipId, false);
            }
            long completeMs = item.get("api_complete_time").getAsLong();
            long nowMs = System.currentTimeMillis();
            long remMs = completeMs - nowMs;
            int st;
            if (remMs <= 0)            st = STATE_DONE;
            else if (remMs <= 60000)   st = STATE_NEAR;
            else if (isLsc && remMs > 600000) st = STATE_LSC;
            else                       st = STATE_ACTIVE;
            slots.add(new DockSlot(name, completeMs, st));
        }
        return slots;
    }

    /** 3 expedition slots (fleets 2-4). */
    public static List<DockSlot> getExpeditionSlots(KcaDBHelper dbHelper) {
        List<DockSlot> slots = new ArrayList<>();
        JsonArray deckport = dbHelper.getJsonArrayValue(DB_KEY_DECKPORT);
        if (deckport == null) {
            for (int i = 0; i < 3; i++) slots.add(emptySlot());
            return slots;
        }
        // Fleets 2-4 (index 1-3 in deckport)
        for (int i = 1; i <= 3; i++) {
            if (i >= deckport.size()) { slots.add(emptySlot()); continue; }
            JsonObject deck = deckport.get(i).getAsJsonObject();
            int fleetNo = i + 1; // display as Fleet 2, 3, 4
            JsonArray mission = deck.has("api_mission")
                    ? deck.getAsJsonArray("api_mission") : null;
            if (mission == null || mission.get(0).getAsInt() != 1) {
                // Not on expedition
                slots.add(new DockSlot("F" + fleetNo + " —", 0, STATE_EMPTY));
                continue;
            }
            int missionNo = mission.get(1).getAsInt();
            long arriveMs = mission.get(2).getAsLong();
            String expHead = KcaExpedition2.getExpeditionHeader(missionNo).trim();
            String label = "F" + fleetNo + " " + expHead;
            long nowMs = System.currentTimeMillis();
            long remMs = arriveMs - nowMs;
            int st;
            if (remMs <= 0)          st = STATE_DONE;
            else if (remMs <= 60000) st = STATE_NEAR;
            else                     st = STATE_ACTIVE;
            slots.add(new DockSlot(label, arriveMs, st));
        }
        return slots;
    }

    // ---- helpers ----

    private static DockSlot emptySlot() {
        return new DockSlot("—", 0, STATE_EMPTY);
    }

    private static DockSlot lockedSlot() {
        return new DockSlot("LOCKED", 0, STATE_EMPTY);
    }

    private static String resolveShipName(int userId) {
        JsonObject userData = getUserShipDataById(userId, "ship_id");
        if (userData == null) return "?";
        int kcId = userData.get("ship_id").getAsInt();
        JsonObject kcData = KcaApiData.getKcShipDataById(kcId, "name");
        if (kcData == null || !kcData.has("name")) return "?";
        return getShipTranslation(kcData.get("name").getAsString(), kcId, false);
    }

    /** Format remaining milliseconds as HH:MM:SS or Xd HH:MM:SS. */
    public static String formatTime(long completeMs) {
        long remMs = completeMs - System.currentTimeMillis();
        if (remMs <= 0) return "00:00:00";
        long totalSec = remMs / 1000;
        long days  = totalSec / 86400;
        long hours = (totalSec % 86400) / 3600;
        long mins  = (totalSec % 3600) / 60;
        long secs  = totalSec % 60;
        if (days > 0) {
            return KcaUtils.format("%dd %02d:%02d:%02d", days, hours, mins, secs);
        }
        return KcaUtils.format("%02d:%02d:%02d", hours, mins, secs);
    }
}
