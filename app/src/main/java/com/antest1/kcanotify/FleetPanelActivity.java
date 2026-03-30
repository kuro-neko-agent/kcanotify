package com.antest1.kcanotify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.antest1.kcanotify.KcaConstants.DB_KEY_DECKPORT;
import static com.antest1.kcanotify.KcaConstants.KCANOTIFY_DB_VERSION;
import static com.antest1.kcanotify.KcaConstants.PREF_FV_MENU_ORDER;
import static com.antest1.kcanotify.KcaConstants.PREF_KCA_SEEK_CN;
import static com.antest1.kcanotify.KcaFleetViewService.DECKINFO_REQ_LIST;
import static com.antest1.kcanotify.KcaFleetViewService.KC_DECKINFO_REQ_LIST;
import static com.antest1.kcanotify.KcaFleetViewService.fleetview_menu_keys;
import static com.antest1.kcanotify.KcaService.BROADCAST_REFRESH_FLEETVIEW;
import static com.antest1.kcanotify.KcaUseStatConstant.FV_BTN_PRESS;
import static com.antest1.kcanotify.KcaUtils.getId;
import static com.antest1.kcanotify.KcaUtils.getStringPreferences;
import static com.antest1.kcanotify.KcaUtils.sendUserAnalytics;

import com.google.gson.JsonParser;

/**
 * Activity for displaying fleet information in split-screen mode.
 * Uses FleetDataManager for data binding (shared with KcaFleetViewService).
 */
public class FleetPanelActivity extends BaseActivity {
    private static final String TAG = "FleetPanelActivity";

    public static final String CLOSE_FLEET_PANEL_ACTION = "com.antest1.kcanotify.CLOSE_FLEET_PANEL";

    private static final String STATE_FLEET_INDEX = "selectedFleetIndex";
    private static final String STATE_SEEK_CN = "seekcn_internal";
    private static final String STATE_SWITCH = "switch_status";

    private FleetDataManager fleetDataManager;
    private KcaDBHelper dbHelper;
    private KcaDeckInfo deckInfoCalc;

    private View fleetContentView; // The inflated fleet view content
    private View itemPopupView;    // For equipment popup

    private Handler mHandler;
    private ScheduledExecutorService timeScheduler;

    private int selectedFleetIndex = 0;
    private int seekcn_internal = -1;
    private int switch_status = 1;

    private BroadcastReceiver refreshReceiver;
    private BroadcastReceiver closeReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fleet_panel);

        Context contextWithTheme = new ContextThemeWrapper(this, R.style.AppTheme);

        dbHelper = new KcaDBHelper(getApplicationContext(), null, KCANOTIFY_DB_VERSION);
        dbHelper.updateExpScore(0);
        KcaApiData.setDBHelper(dbHelper);

        deckInfoCalc = new KcaDeckInfo(contextWithTheme);
        JsonObject gunfitData = FleetDataManager.loadGunfitData(getAssets());

        fleetDataManager = new FleetDataManager(contextWithTheme, dbHelper, deckInfoCalc, gunfitData);

        mHandler = new Handler(Looper.getMainLooper());

        // Inflate the fleet list layout directly (per Q6: inflate view_fleet_list.xml directly)
        LayoutInflater inflater = LayoutInflater.from(contextWithTheme);
        View inflatedView = inflater.inflate(R.layout.view_fleet_list, null);

        // Extract the fleetviewpanel (inner LinearLayout) from the DraggableOverlayLayout
        fleetContentView = inflatedView.findViewById(R.id.fleetviewpanel);

        // Remove from its parent (the DraggableOverlayLayout)
        if (fleetContentView.getParent() != null) {
            ((ViewGroup) fleetContentView.getParent()).removeView(fleetContentView);
        }

        // Use match_parent (per Q5: skip resizeFullWidthView)
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        fleetContentView.setLayoutParams(lp);

        // Add to the activity's root
        FrameLayout root = findViewById(R.id.fleet_panel_root);
        root.addView(fleetContentView);

        // Make visible (overlay layout starts as GONE)
        fleetContentView.setVisibility(View.VISIBLE);

        // Restore state if available
        if (savedInstanceState != null) {
            selectedFleetIndex = savedInstanceState.getInt(STATE_FLEET_INDEX, 0);
            seekcn_internal = savedInstanceState.getInt(STATE_SEEK_CN, -1);
            switch_status = savedInstanceState.getInt(STATE_SWITCH, 1);
        }

        if (seekcn_internal == -1) {
            seekcn_internal = Integer.parseInt(getStringPreferences(getApplicationContext(), PREF_KCA_SEEK_CN));
        }

        setupClickListeners();
        setupMenuButtons();

        // Hide exit button (not needed in Activity) and head drag area
        View exitBtn = fleetContentView.findViewById(R.id.fleetview_exit);
        if (exitBtn != null) exitBtn.setVisibility(View.GONE);

        // Set seek type label
        fleetDataManager.setSeekCnInternal(seekcn_internal);
        TextView cnChangeBtn = fleetContentView.findViewById(R.id.fleetview_cn_change);
        cnChangeBtn.setText(fleetDataManager.getSeekTypeString());

        // Info line init
        TextView fleetInfoLine = fleetContentView.findViewById(R.id.fleetview_infoline);
        fleetInfoLine.setText(getString(R.string.kca_init_content));

        // Setup broadcast receivers
        refreshReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshFleetData();
            }
        };
        closeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                finish();
            }
        };
    }

    private void setupClickListeners() {
        // Fleet tab click handlers
        for (int i = 0; i < 5; i++) {
            int finalI = i;
            fleetContentView.findViewById(getId("fleet_".concat(String.valueOf(i + 1)), R.id.class))
                    .setOnClickListener(v -> {
                        selectedFleetIndex = finalI;
                        refreshFleetData();
                    });
        }

        // Head click = close
        fleetContentView.findViewById(R.id.fleetview_head).setOnClickListener(v -> finish());

        // CN change
        fleetContentView.findViewById(R.id.fleetview_cn_change).setOnClickListener(v -> {
            sendUserAnalytics(getApplicationContext(), FV_BTN_PRESS.concat("CnChange"), null);
            fleetDataManager.nextSeekCn();
            seekcn_internal = fleetDataManager.getSeekCnInternal();
            ((TextView) fleetContentView.findViewById(R.id.fleetview_cn_change))
                    .setText(fleetDataManager.getSeekTypeString());
            fleetDataManager.processDeckInfo(fleetContentView, selectedFleetIndex,
                    fleetDataManager.isCombined(selectedFleetIndex));
        });

        // Fleet switch (combined view toggle)
        fleetContentView.findViewById(R.id.fleetview_fleetswitch).setOnClickListener(v -> {
            sendUserAnalytics(getApplicationContext(), FV_BTN_PRESS.concat("FleetChange"), null);
            if (switch_status == 1) {
                switch_status = 2;
                fleetContentView.findViewById(R.id.fleet_list_main).setVisibility(View.GONE);
                fleetContentView.findViewById(R.id.fleet_list_combined).setVisibility(View.VISIBLE);
                ((TextView) fleetContentView.findViewById(R.id.fleetview_fleetswitch))
                        .setText(getString(R.string.fleetview_switch_2));
            } else {
                switch_status = 1;
                fleetContentView.findViewById(R.id.fleet_list_main).setVisibility(View.VISIBLE);
                fleetContentView.findViewById(R.id.fleet_list_combined).setVisibility(View.GONE);
                ((TextView) fleetContentView.findViewById(R.id.fleetview_fleetswitch))
                        .setText(getString(R.string.fleetview_switch_1));
            }
            fleetDataManager.setSwitchStatus(switch_status);
        });

        // HQ info cycle
        fleetContentView.findViewById(R.id.fleetview_hqinfo).setOnClickListener(v -> {
            fleetDataManager.advanceHqInfoState();
            sendUserAnalytics(getApplicationContext(), FV_BTN_PRESS.concat("HqInfo"), null);
            fleetDataManager.bindHqInfo(fleetContentView);
        });

        // Tools button
        fleetContentView.findViewById(R.id.fleetview_tool).setOnClickListener(v -> {
            sendUserAnalytics(getApplicationContext(), FV_BTN_PRESS.concat("Tools"), null);
            Intent toolIntent = new Intent(FleetPanelActivity.this, MainActivity.class);
            toolIntent.setAction(MainActivity.ACTION_OPEN_TOOL);
            toolIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(toolIntent);
        });
    }

    private void setupMenuButtons() {
        // Menu button ordering (same as KcaFleetViewService.setFleetMenu)
        ViewGroup fleetMenuArea = fleetContentView.findViewById(R.id.viewbutton_area);
        List<TextView> menuBtnList = new ArrayList<>();
        for (String key : fleetview_menu_keys) {
            TextView tv = fleetContentView.findViewById(getId(
                    KcaUtils.format("viewbutton_%s", key), R.id.class));
            tv.setText(getString(getId(
                    KcaUtils.format("viewmenu_%s", key), R.string.class)));
            menuBtnList.add(tv);
            ((ViewGroup) tv.getParent()).removeView(tv);
        }

        String order_data = getStringPreferences(getApplicationContext(), PREF_FV_MENU_ORDER);
        if (!order_data.isEmpty()) {
            JsonArray order = JsonParser.parseString(order_data).getAsJsonArray();
            for (int i = 0; i < order.size(); i++) {
                fleetMenuArea.addView(menuBtnList.get(order.get(i).getAsInt()));
            }
        } else {
            for (TextView tv : menuBtnList) {
                fleetMenuArea.addView(tv);
            }
        }

        // Menu button click handlers — launch overlay services (per Q3: keep as overlays for v1)
        fleetContentView.findViewById(R.id.viewbutton_quest).setOnClickListener(
                v -> startPopupService(KcaQuestViewService.class,
                        KcaQuestViewService.SHOW_QUESTVIEW_ACTION_NEW));
        fleetContentView.findViewById(R.id.viewbutton_excheck).setOnClickListener(v -> {
            String action = KcaExpeditionCheckViewService.SHOW_EXCHECKVIEW_ACTION
                    .concat("/").concat(String.valueOf(selectedFleetIndex));
            startPopupService(KcaExpeditionCheckViewService.class, action);
        });
        fleetContentView.findViewById(R.id.viewbutton_develop).setOnClickListener(
                v -> startPopupService(KcaDevelopPopupService.class, null));
        fleetContentView.findViewById(R.id.viewbutton_construction).setOnClickListener(
                v -> startPopupService(KcaConstructPopupService.class,
                        KcaConstructPopupService.CONSTR_DATA_ACTION));
        fleetContentView.findViewById(R.id.viewbutton_docking).setOnClickListener(
                v -> startPopupService(KcaDockingPopupService.class,
                        KcaDockingPopupService.DOCKING_DATA_ACTION));
        fleetContentView.findViewById(R.id.viewbutton_maphp).setOnClickListener(
                v -> startPopupService(KcaMapHpPopupService.class,
                        KcaMapHpPopupService.MAPHP_SHOW_ACTION));
        fleetContentView.findViewById(R.id.viewbutton_fchk).setOnClickListener(
                v -> startPopupService(KcaFleetCheckPopupService.class,
                        KcaFleetCheckPopupService.FCHK_SHOW_ACTION));
        fleetContentView.findViewById(R.id.viewbutton_labinfo).setOnClickListener(
                v -> startPopupService(KcaLandAirBasePopupService.class,
                        KcaLandAirBasePopupService.LAB_DATA_ACTION));
        fleetContentView.findViewById(R.id.viewbutton_akashi).setOnClickListener(
                v -> startPopupService(KcaAkashiViewService.class,
                        KcaAkashiViewService.SHOW_AKASHIVIEW_ACTION));
    }

    private void startPopupService(Class<?> target, String action) {
        boolean checkGameData = false;
        switch (target.getSimpleName()) {
            case "KcaConstructPopupService":
            case "KcaDevelopPopupService":
            case "KcaDockingPopupService":
                checkGameData = true;
                break;
        }
        if (checkGameData && !KcaApiData.isGameDataLoaded()) return;
        Intent popupIntent = new Intent(getBaseContext(), target);
        if (action != null) popupIntent.setAction(action);
        startService(popupIntent);
    }

    private void refreshFleetData() {
        fleetDataManager.setSelectedFleetIndex(selectedFleetIndex);
        fleetDataManager.setSwitchStatus(switch_status);
        fleetDataManager.setSeekCnInternal(seekcn_internal);
        fleetDataManager.bindFleetData(fleetContentView);
    }

    private void startTimer() {
        stopTimer();
        timeScheduler = Executors.newSingleThreadScheduledExecutor();
        timeScheduler.scheduleWithFixedDelay(() -> {
            mHandler.post(() -> {
                if (fleetContentView != null && fleetDataManager != null) {
                    fleetDataManager.formatFleetInfoLine(fleetContentView, -2);
                }
            });
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void stopTimer() {
        if (timeScheduler != null) {
            timeScheduler.shutdown();
            timeScheduler = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Register broadcast receivers
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(refreshReceiver, new IntentFilter(BROADCAST_REFRESH_FLEETVIEW));
        lbm.registerReceiver(closeReceiver, new IntentFilter(CLOSE_FLEET_PANEL_ACTION));

        // Refresh data from DB
        refreshFleetData();
        startTimer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTimer();
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.unregisterReceiver(refreshReceiver);
        lbm.unregisterReceiver(closeReceiver);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_FLEET_INDEX, selectedFleetIndex);
        outState.putInt(STATE_SEEK_CN, seekcn_internal);
        outState.putInt(STATE_SWITCH, switch_status);
    }

    @Override
    protected void onDestroy() {
        stopTimer();
        super.onDestroy();
    }
}
