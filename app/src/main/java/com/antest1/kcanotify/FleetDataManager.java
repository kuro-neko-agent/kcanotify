package com.antest1.kcanotify;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;
import com.google.common.io.ByteStreams;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.graphics.PorterDuff;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static com.antest1.kcanotify.KcaApiData.getSlotItemTranslation;
import static com.antest1.kcanotify.KcaApiData.getKcItemStatusById;
import static com.antest1.kcanotify.KcaApiData.getUseItemNameById;
import static com.antest1.kcanotify.KcaApiData.getUserItemStatusById;
import static com.antest1.kcanotify.KcaApiData.isItemAircraft;
import static com.antest1.kcanotify.KcaConstants.DB_KEY_DECKPORT;
import static com.antest1.kcanotify.KcaConstants.DB_KEY_USEITEMS;
import static com.antest1.kcanotify.KcaConstants.PREF_FV_ITEM_1;
import static com.antest1.kcanotify.KcaConstants.PREF_FV_ITEM_2;
import static com.antest1.kcanotify.KcaConstants.PREF_FV_ITEM_3;
import static com.antest1.kcanotify.KcaConstants.PREF_FV_ITEM_4;
import static com.antest1.kcanotify.KcaConstants.PREF_KCA_SEEK_CN;
import static com.antest1.kcanotify.KcaConstants.SEEK_PURE;
import static com.antest1.kcanotify.KcaFleetViewService.DECKINFO_REQ_LIST;
import static com.antest1.kcanotify.KcaFleetViewService.FLEET_COMBINED_ID;
import static com.antest1.kcanotify.KcaFleetViewService.KC_DECKINFO_REQ_LIST;
import static com.antest1.kcanotify.KcaUtils.format;
import static com.antest1.kcanotify.KcaUtils.getId;
import static com.antest1.kcanotify.KcaUtils.getStringPreferences;
import static com.antest1.kcanotify.KcaUtils.joinStr;

/**
 * Shared data calculation and view binding for fleet display.
 * Used by both KcaFleetViewService (overlay) and FleetPanelActivity (split-screen).
 */
public class FleetDataManager {
    private static final String TAG = "FleetDataManager";

    private static final int HQINFO_TOTAL = 6;
    private static final int HQINFO_EXPVIEW = 0;
    private static final int HQINFO_SECOUNT = 1;
    private static final int HQINFO_ITEMCNT1 = 2;
    private static final int HQINFO_ITEMCNT2 = 3;
    private static final int HQINFO_ITEMCNT3 = 4;
    private static final int HQINFO_ITEMCNT4 = 5;

    private final Context context;
    private final KcaDBHelper dbHelper;
    private final KcaDeckInfo deckInfoCalc;
    private JsonObject gunfitData;

    int selectedFleetIndex = 0;
    int seekcn_internal = -1;
    int switch_status = 1;
    String fleetCalcInfoText = "";
    int akashiAvailableCount = 0;

    private int[] hqinfoItems = {-1, -1, -1, -1};
    private int hqinfoState = 0;

    public FleetDataManager(Context context, KcaDBHelper dbHelper, KcaDeckInfo deckInfoCalc, JsonObject gunfitData) {
        this.context = context;
        this.dbHelper = dbHelper;
        this.deckInfoCalc = deckInfoCalc;
        this.gunfitData = gunfitData;
    }

    public int getSelectedFleetIndex() {
        return selectedFleetIndex;
    }

    public void setSelectedFleetIndex(int index) {
        this.selectedFleetIndex = index;
    }

    public int getSeekCnInternal() {
        return seekcn_internal;
    }

    public void setSeekCnInternal(int value) {
        this.seekcn_internal = value;
    }

    public int getSwitchStatus() {
        return switch_status;
    }

    public void setSwitchStatus(int status) {
        this.switch_status = status;
    }

    public int getHqInfoState() {
        return hqinfoState;
    }

    public void setHqInfoState(int state) {
        this.hqinfoState = state;
    }

    public String getFleetCalcInfoText() {
        return fleetCalcInfoText;
    }

    public int getAkashiAvailableCount() {
        return akashiAvailableCount;
    }

    public KcaDeckInfo getDeckInfoCalc() {
        return deckInfoCalc;
    }

    public KcaDBHelper getDbHelper() {
        return dbHelper;
    }

    // ==================== Data Binding Methods ====================

    /**
     * Main entry point: binds all fleet data to the given root view.
     * @return true on success, false on error
     */
    public boolean bindFleetData(View rootView) {
        try {
            // Check if fleet data is available before binding
            JsonArray deckportdata = dbHelper.getJsonArrayValue(DB_KEY_DECKPORT);
            if (!KcaFleetViewService.isReady || deckportdata == null || deckportdata.size() == 0) {
                showEmptyState(rootView);
                return false;
            }

            bindHqInfo(rootView);
            View titleView = rootView.findViewById(R.id.fleetview_title);
            if (titleView != null) titleView.setVisibility(VISIBLE);
            updateFleetTabs(rootView, selectedFleetIndex);
            processDeckInfo(rootView, selectedFleetIndex, isCombined(selectedFleetIndex));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void showEmptyState(View rootView) {
        TextView fleetInfoLine = rootView.findViewById(R.id.fleetview_infoline);
        fleetInfoLine.setBackgroundColor(ContextCompat.getColor(context, R.color.colorFleetInfoNoShip));
        fleetInfoLine.setText(context.getString(R.string.kca_init_content));
        rootView.findViewById(R.id.fleet_list_main).setVisibility(INVISIBLE);
        rootView.findViewById(R.id.fleet_list_combined).setVisibility(GONE);
        rootView.findViewById(R.id.fleetview_fleetswitch).setVisibility(GONE);
    }

    public void advanceHqInfoState() {
        hqinfoState = (hqinfoState + 1) % HQINFO_TOTAL;
        if (hqinfoState >= HQINFO_ITEMCNT1 && hqinfoItems[hqinfoState - HQINFO_ITEMCNT1] == -1) {
            advanceHqInfoState();
        }
    }

    private void updateHqInfoVisibility(View hqInfoView) {
        if (hqinfoState == HQINFO_EXPVIEW) {
            hqInfoView.findViewById(R.id.fleetview_exp).setVisibility(VISIBLE);
            hqInfoView.findViewById(R.id.fleetview_cnt).setVisibility(GONE);
            hqInfoView.findViewById(R.id.fleetview_item_cnt).setVisibility(GONE);
        } else if (hqinfoState == HQINFO_SECOUNT) {
            hqInfoView.findViewById(R.id.fleetview_exp).setVisibility(GONE);
            hqInfoView.findViewById(R.id.fleetview_cnt).setVisibility(VISIBLE);
            hqInfoView.findViewById(R.id.fleetview_item_cnt).setVisibility(GONE);
        } else {
            hqInfoView.findViewById(R.id.fleetview_exp).setVisibility(GONE);
            hqInfoView.findViewById(R.id.fleetview_cnt).setVisibility(GONE);
            hqInfoView.findViewById(R.id.fleetview_item_cnt).setVisibility(VISIBLE);
        }
    }

    public void bindHqInfo(View rootView) {
        View hqInfoView = rootView.findViewById(R.id.fleetview_hqinfo);
        String[] pref_items = {PREF_FV_ITEM_1, PREF_FV_ITEM_2, PREF_FV_ITEM_3, PREF_FV_ITEM_4};
        for (int i = 0; i < pref_items.length; i++) {
            hqinfoItems[i] = Integer.parseInt(getStringPreferences(context, pref_items[i]));
        }
        updateHqInfoVisibility(hqInfoView);
        switch (hqinfoState) {
            case HQINFO_EXPVIEW:
                TextView expview = hqInfoView.findViewById(R.id.fleetview_exp);
                float[] exp_score = dbHelper.getExpScore();
                expview.setText(format(
                        context.getString(R.string.fleetview_expview),
                        exp_score[0], exp_score[1]));
                break;
            case HQINFO_SECOUNT:
                TextView shipcntview = hqInfoView.findViewById(R.id.fleetview_cnt1);
                TextView equipcntview = hqInfoView.findViewById(R.id.fleetview_cnt2);
                ImageView shipcntviewicon = hqInfoView.findViewById(R.id.fleetview_cnt1_icon);
                ImageView equipcntviewicon = hqInfoView.findViewById(R.id.fleetview_cnt2_icon);

                if (!KcaApiData.isGameDataLoaded()) {
                    shipcntview.setText("--/--");
                    equipcntview.setText("--/--");
                    break;
                }

                shipcntview.setText(format("%d/%d", KcaApiData.getShipSize(), KcaApiData.getUserMaxShipCount()));
                if (KcaApiData.checkEventUserShip()) {
                    shipcntview.setTextColor(ContextCompat.getColor(context, R.color.colorHqCheckEventCondFailed));
                    shipcntviewicon.setColorFilter(ContextCompat.getColor(context,
                            R.color.colorHqCheckEventCondFailed), PorterDuff.Mode.MULTIPLY);
                } else {
                    shipcntview.setTextColor(ContextCompat.getColor(context, R.color.white));
                    shipcntviewicon.setColorFilter(ContextCompat.getColor(context,
                            R.color.white), PorterDuff.Mode.MULTIPLY);
                }

                equipcntview.setText(format("%d/%d", KcaApiData.getItemSize(), KcaApiData.getUserMaxItemCount()));
                if (KcaApiData.checkEventUserItem()) {
                    equipcntview.setTextColor(ContextCompat.getColor(context, R.color.colorHqCheckEventCondFailed));
                    equipcntviewicon.setColorFilter(ContextCompat.getColor(context,
                            R.color.colorHqCheckEventCondFailed), PorterDuff.Mode.MULTIPLY);
                } else {
                    equipcntview.setTextColor(ContextCompat.getColor(context, R.color.white));
                    equipcntviewicon.setColorFilter(ContextCompat.getColor(context,
                            R.color.white), PorterDuff.Mode.MULTIPLY);
                }
                break;
            case HQINFO_ITEMCNT1:
            case HQINFO_ITEMCNT2:
            case HQINFO_ITEMCNT3:
            case HQINFO_ITEMCNT4:
                int item_id = hqinfoItems[hqinfoState - 2];
                int item_count = 0;
                String item_name = getUseItemNameById(item_id);

                ImageView item_icon = hqInfoView.findViewById(R.id.fleetview_item_cnt_icon);
                if (item_id == 68 || item_id == 93) {
                    item_icon.setImageResource(R.drawable.ic_saury);
                } else {
                    item_icon.setImageResource(R.drawable.ic_gift);
                }
                item_icon.setColorFilter(ContextCompat.getColor(context,
                        getId("colorFleetViewItem" + (hqinfoState - 1), R.color.class)), PorterDuff.Mode.SRC_ATOP);

                TextView itemcntview = hqInfoView.findViewById(R.id.fleetview_item_cnt_value);
                itemcntview.setTextColor(ContextCompat.getColor(context,
                        getId("colorFleetViewItem" + (hqinfoState - 1), R.color.class)));
                itemcntview.setText(format("[%s] %02d", item_name, item_count));

                JsonArray useitem_data = dbHelper.getJsonArrayValue(DB_KEY_USEITEMS);
                if (useitem_data != null) {
                    for (int i = 0; i < useitem_data.size(); i++) {
                        JsonObject item = useitem_data.get(i).getAsJsonObject();
                        if (item.get("api_id").getAsInt() == item_id) {
                            item_count = item.get("api_count").getAsInt();
                        }
                    }
                    itemcntview.setText(format("[%s] %02d", item_name, item_count));
                }
                break;
            default:
                break;
        }
    }

    public void processDeckInfo(View rootView, int idx, boolean isCombined) {
        boolean is_combined = idx == FLEET_COMBINED_ID;
        boolean is_landscape = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        JsonArray deckportdata = dbHelper.getJsonArrayValue(DB_KEY_DECKPORT);
        int deck_count = (deckportdata != null) ? deckportdata.size() : 0;

        TextView fleetInfoLine = rootView.findViewById(R.id.fleetview_infoline);
        TextView fleetSwitchBtn = rootView.findViewById(R.id.fleetview_fleetswitch);

        if (!KcaFleetViewService.isReady) {
            fleetCalcInfoText = "";
            fleetInfoLine.setBackgroundColor(ContextCompat.getColor(context, R.color.colorFleetInfoNoShip));
            fleetInfoLine.setText(context.getString(R.string.kca_init_content));
            rootView.findViewById(R.id.fleet_list_main).setVisibility(INVISIBLE);
            rootView.findViewById(R.id.fleet_list_combined).setVisibility(is_landscape ? INVISIBLE : GONE);
            fleetSwitchBtn.setVisibility(GONE);
            return;
        }

        boolean not_opened_flag = idx == FLEET_COMBINED_ID ? deck_count < 2 : idx >= deck_count;
        if (not_opened_flag) {
            fleetCalcInfoText = "Not Opened";
            fleetInfoLine.setBackgroundColor(ContextCompat.getColor(context, R.color.colorFleetInfoNoShip));
            fleetInfoLine.setText(fleetCalcInfoText);
            rootView.findViewById(R.id.fleet_list_main).setVisibility(INVISIBLE);
            rootView.findViewById(R.id.fleet_list_combined).setVisibility(is_landscape ? INVISIBLE : GONE);
            fleetSwitchBtn.setVisibility(GONE);
            return;
        }

        if (is_landscape) {
            fleetSwitchBtn.setVisibility(GONE);
            rootView.findViewById(R.id.fleet_list_main).setVisibility(VISIBLE);
            rootView.findViewById(R.id.fleet_list_combined).setVisibility(is_combined ? VISIBLE : INVISIBLE);
        } else {
            boolean switch_is_one = switch_status == 1;
            fleetSwitchBtn.setVisibility(is_combined ? VISIBLE : GONE);
            rootView.findViewById(R.id.fleet_list_main).setVisibility((!is_combined || switch_is_one) ? VISIBLE : GONE);
            rootView.findViewById(R.id.fleet_list_combined).setVisibility((is_combined && !switch_is_one) ? VISIBLE : GONE);
        }

        int cn = seekcn_internal;
        List<String> infoList = new ArrayList<>();

        String airPowerValue;
        if (isCombined) {
            airPowerValue = deckInfoCalc.getAirPowerRangeString(deckportdata, 0, KcaBattle.getEscapeFlag());
        } else {
            airPowerValue = deckInfoCalc.getAirPowerRangeString(deckportdata, idx, null);
        }
        if (!airPowerValue.isEmpty()) {
            infoList.add(airPowerValue);
        }

        double seekValue;
        String seekStringValue;
        if (isCombined) {
            seekValue = deckInfoCalc.getSeekValue(deckportdata, "0,1", cn, KcaBattle.getEscapeFlag());
        } else {
            seekValue = deckInfoCalc.getSeekValue(deckportdata, String.valueOf(idx), cn, null);
        }
        if (cn == SEEK_PURE) {
            seekStringValue = format(context.getString(R.string.fleetview_seekvalue_d), (int) seekValue);
        } else {
            seekStringValue = format(context.getString(R.string.fleetview_seekvalue_f), seekValue);
        }
        infoList.add(seekStringValue);

        String speedStringValue;
        if (isCombined) {
            speedStringValue = deckInfoCalc.getSpeedString(deckportdata, "0,1", KcaBattle.getEscapeFlag());
        } else {
            speedStringValue = deckInfoCalc.getSpeedString(deckportdata, String.valueOf(idx), null);
        }
        infoList.add(speedStringValue);

        String tpValue;
        if (isCombined) {
            tpValue = deckInfoCalc.getTPString(deckportdata, "0,1", KcaBattle.getEscapeFlag());
        } else {
            tpValue = deckInfoCalc.getTPString(deckportdata, String.valueOf(idx), null);
        }
        infoList.add(tpValue);

        for (int i = 0; i < 12; i++) {
            getFleetViewItem(rootView, i).setVisibility(INVISIBLE);
        }

        int sum_level = 0;
        if (isCombined) {
            sum_level += bindFleetShips(rootView, deckInfoCalc.getDeckList(deckportdata, 0), 0);
            sum_level += bindFleetShips(rootView, deckInfoCalc.getDeckList(deckportdata, 1), 1);
        } else {
            int[] ship_id_list = deckInfoCalc.getDeckList(deckportdata, idx);
            if (ship_id_list.length > 6) {
                rootView.findViewById(R.id.fleet_list_combined).setVisibility(VISIBLE);
            }
            sum_level += bindFleetShips(rootView, ship_id_list, 0);
        }

        akashiAvailableCount = KcaAkashiRepairInfo.getAkashiAvailableCount(idx);

        infoList.add("LV ".concat(String.valueOf(sum_level)));
        fleetCalcInfoText = joinStr(infoList, " / ");
        long moraleCompleteTime;
        if (selectedFleetIndex == FLEET_COMBINED_ID) {
            moraleCompleteTime = Math.max(KcaMoraleInfo.getMoraleCompleteTime(0),
                    KcaMoraleInfo.getMoraleCompleteTime(1));
        } else {
            moraleCompleteTime = KcaMoraleInfo.getMoraleCompleteTime(selectedFleetIndex);
        }
        if (selectedFleetIndex < 4 && KcaExpedition2.isInExpedition(selectedFleetIndex)) {
            fleetInfoLine.setBackgroundColor(ContextCompat.getColor(context, R.color.colorFleetInfoExpedition));
        } else if (moraleCompleteTime > 0) {
            fleetInfoLine.setBackgroundColor(ContextCompat.getColor(context, R.color.colorFleetInfoNotGoodStatus));
        } else {
            fleetInfoLine.setBackgroundColor(ContextCompat.getColor(context, R.color.colorFleetInfoNormal));
        }
        formatFleetInfoLine(rootView, moraleCompleteTime);
    }

    /**
     * @param list member ship id list
     * @param base_view_id 0 for 1st-4th fleet and 1st combined fleet, 1 for 2nd combined fleet
     * @return sum of level
     */
    public int bindFleetShips(View rootView, int[] list, int base_view_id) {
        int sum_level = 0;
        int ships_in_fleet = list.length;
        for (int i = 0; i < Math.max(6, ships_in_fleet); i++) {
            int view_id = base_view_id * 6 + i;
            KcaFleetViewListItem ship = getFleetViewItem(rootView, view_id);

            if (i >= ships_in_fleet) {
                getFleetViewItem(rootView, view_id).setVisibility(INVISIBLE);
            } else {
                getFleetViewItem(rootView, view_id).setContent(list[i]);
                sum_level += ship.getShipInfo().lv;
            }
        }
        return sum_level;
    }

    /**
     * @param index 0 <= index < 12
     * @return fleetview_item at index
     */
    public KcaFleetViewListItem getFleetViewItem(View rootView, int index) {
        if (index < 6) {
            ViewGroup main = rootView.findViewById(R.id.fleet_list_main);
            return (KcaFleetViewListItem) main.getChildAt(index);
        } else {
            ViewGroup combined = rootView.findViewById(R.id.fleet_list_combined);
            return (KcaFleetViewListItem) combined.getChildAt(index - 6);
        }
    }

    public void formatFleetInfoLine(View rootView, long moraleCompleteTime) {
        TextView fleetInfoLine = rootView.findViewById(R.id.fleetview_infoline);
        TextView fleetAkashiTimerBtn = rootView.findViewById(R.id.fleetview_akashi_timer);

        final String displayText;
        if (KcaService.isPortAccessed) {
            if (moraleCompleteTime < -1) {
                if (selectedFleetIndex == FLEET_COMBINED_ID) {
                    moraleCompleteTime = Math.max(KcaMoraleInfo.getMoraleCompleteTime(0),
                            KcaMoraleInfo.getMoraleCompleteTime(1));
                } else {
                    moraleCompleteTime = KcaMoraleInfo.getMoraleCompleteTime(selectedFleetIndex);
                }
            }
            if (moraleCompleteTime > 0) {
                int diff = Math.max(0, (int) (moraleCompleteTime - System.currentTimeMillis()) / 1000);
                String moraleTimeText = KcaUtils.getTimeStr(diff);
                displayText = moraleTimeText.concat(" | ").concat(fleetCalcInfoText);
            } else {
                displayText = fleetCalcInfoText;
            }
        } else {
            displayText = "";
        }

        final String akashi_timer_text = KcaUtils.getTimeStr(KcaAkashiRepairInfo.getAkashiElapsedTimeInSecond());

        if (KcaFleetViewService.isReady && !displayText.isEmpty()) {
            if (!displayText.contentEquals(fleetInfoLine.getText())) {
                fleetInfoLine.setText(displayText);
            }

            long akashiTimerValue = KcaAkashiRepairInfo.getAkashiTimerValue();
            boolean isAkashiActive = akashiTimerValue >= 0 && akashiAvailableCount > 0;
            for (int i = 0; i < 6; i++) {
                if (i < akashiAvailableCount) {
                    getFleetViewItem(rootView, i).setAkashiTimer(isAkashiActive);
                } else {
                    getFleetViewItem(rootView, i).setAkashiTimer(false);
                }
            }

            if (akashiTimerValue < 0) {
                fleetAkashiTimerBtn.setVisibility(GONE);
            } else {
                if (akashiAvailableCount > 0) {
                    fleetAkashiTimerBtn.setBackgroundColor(ContextCompat.getColor(context, R.color.colorFleetAkashiTimerBtnActive));
                } else {
                    fleetAkashiTimerBtn.setBackgroundColor(ContextCompat.getColor(context, R.color.colorFleetAkashiTimerBtnDeactive));
                }
                if (!akashi_timer_text.contentEquals(fleetAkashiTimerBtn.getText())) {
                    fleetAkashiTimerBtn.setText(akashi_timer_text);
                }
                fleetAkashiTimerBtn.setVisibility(VISIBLE);
            }
        } else {
            if (!context.getString(R.string.kca_init_content).contentEquals(fleetInfoLine.getText())) {
                fleetInfoLine.setText(context.getString(R.string.kca_init_content));
            }
            fleetAkashiTimerBtn.setVisibility(GONE);
        }
    }

    public void updateFleetTabs(View rootView, int selected) {
        for (int i = 0; i < 5; i++) {
            int view_id = getId("fleet_".concat(String.valueOf(i + 1)), R.id.class);
            Chip chip = rootView.findViewById(view_id);
            chip.setChecked(selected == i);
            if (selected == i) {
                chip.setChipStrokeColorResource(R.color.colorAccent);
            } else {
                if (i < 4 && KcaExpedition2.isInExpedition(i)) {
                    chip.setChipStrokeColorResource(R.color.colorFleetInfoExpeditionBtn);
                } else if (i < 4 && KcaMoraleInfo.getMoraleCompleteTime(i) > 0) {
                    chip.setChipStrokeColorResource(R.color.colorFleetInfoNotGoodStatusBtn);
                } else if (i == FLEET_COMBINED_ID &&
                        (KcaMoraleInfo.getMoraleCompleteTime(0) > 0 || KcaMoraleInfo.getMoraleCompleteTime(1) > 0)) {
                    chip.setChipStrokeColorResource(R.color.colorFleetInfoNotGoodStatusBtn);
                } else {
                    chip.setChipStrokeColorResource(R.color.colorFleetInfoNormalBtn);
                }
            }
        }
    }

    // ==================== Item Popup ====================

    public void bindItemPopupView(View itemView, JsonObject data, String ship_id, int married) {
        JsonObject item_fit = gunfitData.getAsJsonObject("e_idx");
        JsonObject ship_fit = gunfitData.getAsJsonObject("s_idx");
        JsonObject fit_data = gunfitData.getAsJsonObject("f_idx");

        JsonArray slot = data.getAsJsonArray("api_slot");
        JsonArray onslot = null;
        JsonArray maxslot = null;
        if (data.has("api_onslot")) {
            onslot = data.getAsJsonArray("api_onslot");
            maxslot = data.getAsJsonArray("api_maxslot");
        }
        int slot_count = 0;
        int onslot_count = 0;
        int slot_ex = 0;
        if (data.has("api_slot_ex")) {
            slot_ex = data.get("api_slot_ex").getAsInt();
        }
        for (int i = 0; i < slot.size(); i++) {
            int item_id = slot.get(i).getAsInt();
            if (item_id == -1) {
                itemView.findViewById(getId("item".concat(String.valueOf(i + 1)), R.id.class)).setVisibility(GONE);
                itemView.findViewById(getId(format("item%d_slot", i + 1), R.id.class)).setVisibility(GONE);
            } else {
                slot_count += 1;
                JsonObject kcItemData;
                int lv = 0;
                int alv = -1;
                if (onslot != null) {
                    kcItemData = getUserItemStatusById(item_id, "level,alv", "id,type,name");
                    if (kcItemData == null) continue;
                    lv = kcItemData.get("level").getAsInt();
                    if (kcItemData.has("alv")) {
                        alv = kcItemData.get("alv").getAsInt();
                    }

                    if (lv > 0) {
                        ((TextView) itemView.findViewById(getId(format("item%d_level", i + 1), R.id.class)))
                                .setText(context.getString(R.string.lv_star).concat(String.valueOf(lv)));
                        itemView.findViewById(getId(format("item%d_level", i + 1), R.id.class)).setVisibility(VISIBLE);
                    } else {
                        itemView.findViewById(getId(format("item%d_level", i + 1), R.id.class)).setVisibility(GONE);
                    }

                    if (alv > 0) {
                        ((TextView) itemView.findViewById(getId(format("item%d_alv", i + 1), R.id.class)))
                                .setText(context.getString(getId(format("alv_%d", alv), R.string.class)));
                        int alvColorId = (alv <= 3) ? 1 : 2;
                        ((TextView) itemView.findViewById(getId(format("item%d_alv", i + 1), R.id.class)))
                                .setTextColor(ContextCompat.getColor(context, getId(format("itemalv%d", alvColorId), R.color.class)));
                        itemView.findViewById(getId(format("item%d_alv", i + 1), R.id.class)).setVisibility(VISIBLE);
                    } else {
                        itemView.findViewById(getId(format("item%d_alv", i + 1), R.id.class)).setVisibility(GONE);
                    }

                    int itemtype = kcItemData.getAsJsonArray("type").get(2).getAsInt();
                    if (isItemAircraft(itemtype)) {
                        onslot_count += 1;
                        int nowSlotValue = onslot.get(i).getAsInt();
                        int maxSlotValue = maxslot.get(i).getAsInt();
                        ((TextView) itemView.findViewById(getId(format("item%d_slot", i + 1), R.id.class)))
                                .setText(format("[%02d/%02d]", nowSlotValue, maxSlotValue));
                        itemView.findViewById(getId(format("item%d_slot", i + 1), R.id.class)).setVisibility(VISIBLE);
                    } else {
                        itemView.findViewById(getId(format("item%d_slot", i + 1), R.id.class)).setVisibility(INVISIBLE);
                    }
                } else {
                    kcItemData = getKcItemStatusById(item_id, "id,type,name");
                    itemView.findViewById(getId(format("item%d_level", i + 1), R.id.class)).setVisibility(GONE);
                    itemView.findViewById(getId(format("item%d_alv", i + 1), R.id.class)).setVisibility(GONE);
                    itemView.findViewById(getId(format("item%d_slot", i + 1), R.id.class)).setVisibility(GONE);
                }

                String kcItemId = kcItemData.get("id").getAsString();
                String kcItemName = getSlotItemTranslation(kcItemData.get("name").getAsString());

                int fit_state = -1;
                if (item_fit.has(kcItemId) && ship_fit.has(ship_id)) {
                    int ship_idx = ship_fit.get(ship_id).getAsInt();
                    int item_idx = item_fit.get(kcItemId).getAsInt();
                    String key = format("%d_%d", item_idx, ship_idx);
                    if (fit_data.has(key)) {
                        fit_state = fit_data.getAsJsonArray(key).get(married).getAsInt() + 2;
                    }
                }

                int type = kcItemData.getAsJsonArray("type").get(3).getAsInt();
                int typeres;
                try {
                    typeres = getId(format("item_%d", type), R.mipmap.class);
                } catch (Exception e) {
                    typeres = R.mipmap.item_0;
                }
                ((TextView) itemView.findViewById(getId(format("item%d_name", i + 1), R.id.class))).setText(kcItemName);
                if (fit_state == -1) {
                    ((TextView) itemView.findViewById(getId(format("item%d_name", i + 1), R.id.class)))
                            .setTextColor(ContextCompat.getColor(context, R.color.white));
                } else {
                    ((TextView) itemView.findViewById(getId(format("item%d_name", i + 1), R.id.class)))
                            .setTextColor(ContextCompat.getColor(context, getId(format("colorGunfit%d", fit_state), R.color.class)));
                }

                ((ImageView) itemView.findViewById(getId(format("item%d_icon", i + 1), R.id.class))).setImageResource(typeres);
                itemView.findViewById(getId("item".concat(String.valueOf(i + 1)), R.id.class)).setVisibility(VISIBLE);
            }
        }

        if (slot_ex != 0) {
            if (slot_ex > 0) {
                slot_count += 1;
                JsonObject kcItemData = getUserItemStatusById(slot_ex, "level", "type,name");
                if (kcItemData != null) {
                    String kcItemName = getSlotItemTranslation(kcItemData.get("name").getAsString());
                    int type = kcItemData.getAsJsonArray("type").get(3).getAsInt();
                    int lv = kcItemData.get("level").getAsInt();
                    int typeres;
                    try {
                        typeres = getId(format("item_%d", type), R.mipmap.class);
                    } catch (Exception e) {
                        typeres = R.mipmap.item_0;
                    }
                    ((TextView) itemView.findViewById(R.id.item_ex_name)).setText(kcItemName);
                    ((ImageView) itemView.findViewById(R.id.item_ex_icon)).setImageResource(typeres);
                    itemView.findViewById(R.id.item_ex_icon).setVisibility(VISIBLE);
                    if (lv > 0) {
                        ((TextView) itemView.findViewById(R.id.item_ex_level))
                                .setText(context.getString(R.string.lv_star).concat(String.valueOf(lv)));
                        itemView.findViewById(R.id.item_ex_level).setVisibility(VISIBLE);
                    } else {
                        itemView.findViewById(R.id.item_ex_level).setVisibility(GONE);
                    }
                } else {
                    ((TextView) itemView.findViewById(R.id.item_ex_name)).setText("???");
                    itemView.findViewById(R.id.view_slot_ex).setVisibility(INVISIBLE);
                }
            } else {
                ((TextView) itemView.findViewById(R.id.item_ex_name)).setText(context.getString(R.string.slot_empty));
                ((ImageView) itemView.findViewById(R.id.item_ex_icon)).setImageResource(R.mipmap.item_0);
                itemView.findViewById(R.id.item_ex_level).setVisibility(GONE);
                itemView.findViewById(R.id.item_ex_slot_list).setVisibility(INVISIBLE);
            }
            itemView.findViewById(R.id.view_slot_ex).setVisibility(VISIBLE);
        } else {
            itemView.findViewById(R.id.view_slot_ex).setVisibility(GONE);
        }

        if (onslot_count == 0) {
            for (int i = 0; i < slot.size(); i++) {
                itemView.findViewById(getId(format("item%d_slot", i + 1), R.id.class)).setVisibility(GONE);
            }
            itemView.findViewById(R.id.item_slot_list).setVisibility(GONE);
            itemView.findViewById(R.id.item_ex_slot_list).setVisibility(GONE);
        } else {
            itemView.findViewById(R.id.item_slot_list).setVisibility(VISIBLE);
        }

        if (slot_count == 0) {
            ((TextView) itemView.findViewById(R.id.item1_name)).setText(context.getString(R.string.slot_empty));
            ((TextView) itemView.findViewById(R.id.item1_name)).setTextColor(ContextCompat.getColor(context, R.color.white));
            ((ImageView) itemView.findViewById(R.id.item1_icon)).setImageResource(R.mipmap.item_0);
            itemView.findViewById(R.id.item1_level).setVisibility(GONE);
            itemView.findViewById(R.id.item1_alv).setVisibility(GONE);
            itemView.findViewById(R.id.item1_slot).setVisibility(GONE);
            itemView.findViewById(R.id.item1).setVisibility(VISIBLE);
        }
        itemView.setVisibility(VISIBLE);
    }

    // ==================== Seek Type ====================

    public int getSeekCn() {
        return Integer.parseInt(getStringPreferences(context, PREF_KCA_SEEK_CN));
    }

    public String getSeekTypeString() {
        String seekType;
        switch (seekcn_internal) {
            case 1:
                seekType = context.getString(R.string.seek_type_1);
                break;
            case 2:
                seekType = context.getString(R.string.seek_type_2);
                break;
            case 3:
                seekType = context.getString(R.string.seek_type_3);
                break;
            case 4:
                seekType = context.getString(R.string.seek_type_4);
                break;
            default:
                seekType = context.getString(R.string.seek_type_0);
                break;
        }
        return seekType;
    }

    public void nextSeekCn() {
        seekcn_internal += 1;
        seekcn_internal %= 5;
    }

    public boolean isCombined(int idx) {
        return idx == FLEET_COMBINED_ID;
    }

    // ==================== Static helpers ====================

    public static JsonObject loadGunfitData(AssetManager am) {
        try {
            AssetManager.AssetInputStream ais = (AssetManager.AssetInputStream) am.open("gunfit.json");
            byte[] bytes = ByteStreams.toByteArray(ais);
            return JsonParser.parseString(new String(bytes)).getAsJsonObject();
        } catch (IOException e) {
            return new JsonObject();
        }
    }
}
