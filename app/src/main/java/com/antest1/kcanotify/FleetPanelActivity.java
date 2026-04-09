package com.antest1.kcanotify;

import android.graphics.drawable.Drawable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.transition.TransitionManager;
import android.transition.AutoTransition;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.DisplayMetrics;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.antest1.kcanotify.KcaConstants.DB_KEY_BASICIFNO;
import static com.antest1.kcanotify.KcaConstants.DB_KEY_DECKPORT;
import static com.antest1.kcanotify.KcaConstants.DB_KEY_MATERIALS;
import static com.antest1.kcanotify.KcaConstants.DB_KEY_STARTDATA;
import static com.antest1.kcanotify.KcaConstants.DISPLAY_MODE_SPLIT;
import static com.antest1.kcanotify.KcaConstants.KCANOTIFY_DB_VERSION;
import static com.antest1.kcanotify.KcaConstants.KCANOTIFY_QTDB_VERSION;
import static com.antest1.kcanotify.KcaConstants.PREF_DISPLAY_MODE;
import static com.antest1.kcanotify.KcaConstants.PREF_FV_MENU_ORDER;
import static com.antest1.kcanotify.KcaConstants.PREF_PANEL_LAST_FLEET_INDEX;
import static com.antest1.kcanotify.KcaConstants.PREF_PANEL_LAST_SEEK_CN;
import static com.antest1.kcanotify.KcaConstants.PREF_PANEL_LAST_SWITCH_STATUS;
import static com.antest1.kcanotify.KcaConstants.PREF_PANEL_PENDING_REOPEN;
import static com.antest1.kcanotify.KcaConstants.PREF_SPLIT_PANE_ENABLED;
import static com.antest1.kcanotify.KcaConstants.BROADCAST_SHOW_BATTLE_FRAGMENT;
import static com.antest1.kcanotify.KcaConstants.BROADCAST_SHOW_QUEST_FRAGMENT;
import static com.antest1.kcanotify.KcaConstants.BROADCAST_TAB_SWITCH;
import static com.antest1.kcanotify.KcaConstants.EXTRA_TAB_INDEX;
import static com.antest1.kcanotify.KcaConstants.KCA_MSG_QUEST_VIEW_LIST;
import static com.antest1.kcanotify.KcaConstants.KCA_MSG_QUEST_COMPLETE;
import static com.antest1.kcanotify.KcaConstants.PREF_RESIZABLE_PANE;
import static com.antest1.kcanotify.KcaConstants.PREF_RESET_SHOW_DAILY;
import static com.antest1.kcanotify.KcaConstants.PREF_RESET_SHOW_EO;
import static com.antest1.kcanotify.KcaConstants.PREF_RESET_SHOW_MONTHLY;
import static com.antest1.kcanotify.KcaConstants.PREF_RESET_SHOW_PRACTICE;
import static com.antest1.kcanotify.KcaConstants.PREF_RESET_SHOW_QUARTERLY;
import static com.antest1.kcanotify.KcaConstants.PREF_RESET_SHOW_SENKA;
import static com.antest1.kcanotify.KcaConstants.PREF_RESET_SHOW_WEEKLY;

import android.content.SharedPreferences;
import static com.antest1.kcanotify.KcaConstants.PREF_KCA_SEEK_CN;
import static com.antest1.kcanotify.KcaFleetViewService.DECKINFO_REQ_LIST;
import static com.antest1.kcanotify.KcaFleetViewService.FLEET_COMBINED_ID;
import static com.antest1.kcanotify.KcaFleetViewService.KC_DECKINFO_REQ_LIST;
import static com.antest1.kcanotify.KcaFleetViewService.fleetview_menu_keys;
import static com.antest1.kcanotify.KcaConstants.SEEK_PURE;
import static com.antest1.kcanotify.KcaService.BROADCAST_REFRESH_FLEETVIEW;
import static com.antest1.kcanotify.KcaUseStatConstant.FV_BTN_PRESS;
import static com.antest1.kcanotify.KcaApiData.getQuestTrackInfo;
import static com.antest1.kcanotify.KcaUtils.getId;
import static com.antest1.kcanotify.KcaUtils.getIdWithFallback;
import static com.antest1.kcanotify.KcaUtils.getBooleanPreferences;
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

    // Static flag for KcaViewButtonService to check if panel is already open
    public static volatile boolean isFleetPanelOpen = false;

    private static final String STATE_FLEET_INDEX = "selectedFleetIndex";
    private static final String STATE_SEEK_CN = "seekcn_internal";
    private static final String STATE_SWITCH = "switch_status";

    private FleetDataManager fleetDataManager;
    private KcaDBHelper dbHelper;
    private KcaDeckInfo deckInfoCalc;
    private KcaQuestTracker questTracker;

    private View fleetContentView; // The inflated fleet view content
    private View itemPopupView;    // For equipment popup
    private PopupWindow itemPopupWindow;
    private View expandedQuestRow = null;  // currently expanded quest row, or null
    private View expandedShipRow = null;   // currently expanded compact ship row, or null

    private Handler mHandler;
    private ScheduledExecutorService timeScheduler;

    private int selectedFleetIndex = 0;
    private int seekcn_internal = -1;
    private int switch_status = 1;

    private View leftPaneView; // Left pane root (only in split pane mode)
    // Dock grid cells — created once, updated each tick (4 rows × 4 cols, then 3 rows × 2 cols)
    private TextView[] mDockCombinedCells; // 16 cells: row*4+col
    private TextView[] mDockExpdCells;     // 6 cells: row*2+col
    private RightPanePagerAdapter pagerAdapter;
    private ViewPager2 viewPager;
    private int hqLevel = 1; // cached HQ level for resource regen cap calculation

    private BroadcastReceiver refreshReceiver;
    private BroadcastReceiver closeReceiver;
    private BroadcastReceiver showBattleReceiver;
    private BroadcastReceiver showQuestReceiver;
    private BroadcastReceiver tabSwitchReceiver;
    private BroadcastReceiver questUpdateReceiver;
    private boolean closedByBroadcast = false;
    private boolean splitPaneEnabled = false;
    private boolean lastResizablePaneState = false;
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fleet_panel);

        Context contextWithTheme = new ContextThemeWrapper(this, R.style.AppTheme);

        dbHelper = new KcaDBHelper(getApplicationContext(), null, KCANOTIFY_DB_VERSION);
        questTracker = new KcaQuestTracker(getApplicationContext(), null, KCANOTIFY_QTDB_VERSION);
        dbHelper.updateExpScore(0);
        KcaApiData.setDBHelper(dbHelper);

        // Restore static game data after process death
        if (!KcaApiData.isGameDataLoaded()) {
            JsonObject kcDataObj = dbHelper.getJsonObjectValue(DB_KEY_STARTDATA);
            if (kcDataObj != null && kcDataObj.has("api_data")) {
                KcaApiData.getKcGameData(kcDataObj.getAsJsonObject("api_data"));
                KcaApiData.loadTranslationData(getApplicationContext());
            }
        }

        deckInfoCalc = new KcaDeckInfo(contextWithTheme);
        JsonObject gunfitData = FleetDataManager.loadGunfitData(getAssets());

        fleetDataManager = new FleetDataManager(contextWithTheme, dbHelper, deckInfoCalc, gunfitData);

        mHandler = new Handler(Looper.getMainLooper());

        // Inflate the fleet list layout directly (per Q6: inflate view_fleet_list.xml directly)
        LayoutInflater inflater = LayoutInflater.from(contextWithTheme);
        View inflatedView = inflater.inflate(R.layout.view_fleet_list, null);

        // Extract the fleetviewpanel (inner LinearLayout) from the DraggableOverlayLayout
        fleetContentView = inflatedView.findViewById(R.id.fleetviewpanel);
        if (fleetContentView == null) {
            Log.e(TAG, "fleetviewpanel not found in inflated layout");
            finish();
            return;
        }

        // Remove from its parent (the DraggableOverlayLayout)
        if (fleetContentView.getParent() != null) {
            ((ViewGroup) fleetContentView.getParent()).removeView(fleetContentView);
        }

        // Use match_parent (per Q5: skip resizeFullWidthView)
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        fleetContentView.setLayoutParams(lp);

        // Fix child layout params for Activity mode.
        // The XML uses match_parent + weight patterns that work when parent is wrap_content (overlay),
        // but break when parent is match_parent (Activity). Use standard 0dp+weight pattern.
        if (fleetContentView instanceof LinearLayout) {
            LinearLayout panel = (LinearLayout) fleetContentView;
            int childCount = panel.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = panel.getChildAt(i);
                ViewGroup.LayoutParams lp2 = child.getLayoutParams();
                if (lp2 instanceof LinearLayout.LayoutParams) {
                    LinearLayout.LayoutParams llp = (LinearLayout.LayoutParams) lp2;
                    if (i == childCount - 1) {
                        // Last child (ship area + menu buttons): fill remaining space
                        llp.height = 0;
                        llp.weight = 1.0f;
                    } else {
                        // All other children: natural height, no weight
                        if (llp.height == ViewGroup.LayoutParams.MATCH_PARENT) {
                            llp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                        }
                        llp.weight = 0;
                    }
                    child.setLayoutParams(llp);
                }
            }
        }

        // Add fleetContentView to the appropriate container
        splitPaneEnabled = getBooleanPreferences(getApplicationContext(), PREF_SPLIT_PANE_ENABLED);
        lastResizablePaneState = getBooleanPreferences(getApplicationContext(), PREF_RESIZABLE_PANE);

        if (splitPaneEnabled) {
            // New path: resizable split pane
            LinearLayout splitPane = findViewById(R.id.split_pane_layout);
            splitPane.setVisibility(View.VISIBLE);
            FrameLayout leftPaneContainer = findViewById(R.id.left_pane);
            setupLeftPane(leftPaneContainer, contextWithTheme);
            FrameLayout rightPaneContainer = findViewById(R.id.right_pane);
            setupRightPane(rightPaneContainer, contextWithTheme);
            View divider = findViewById(R.id.pane_divider);
            if (getBooleanPreferences(getApplicationContext(), PREF_RESIZABLE_PANE)) {
                restoreLeftPaneWidth(leftPaneContainer);
                setupDividerDrag(divider, leftPaneContainer);
            } else {
                divider.setVisibility(View.GONE);
            }

            // Listen for resizable pane toggle from embedded settings tab
            final FrameLayout leftPaneRef = leftPaneContainer;
            final View dividerRef = divider;
            prefListener = (prefs, key) -> {
                if (PREF_RESIZABLE_PANE.equals(key)) {
                    boolean enabled = prefs.getBoolean(key, false);
                    if (enabled) {
                        dividerRef.setVisibility(View.VISIBLE);
                        dividerRef.setBackgroundColor(ContextCompat.getColor(FleetPanelActivity.this, R.color.colorDividerLight));
                        restoreLeftPaneWidth(leftPaneRef);
                        setupDividerDrag(dividerRef, leftPaneRef);
                    } else {
                        dividerRef.setVisibility(View.GONE);
                        dividerRef.setOnTouchListener(null);
                        // Keep current width — don't reset
                    }
                }
            };
            getSharedPreferences("pref", MODE_PRIVATE)
                    .registerOnSharedPreferenceChangeListener(prefListener);
        } else {
            // Legacy path: single pane (preserves existing behavior)
            FrameLayout singlePane = findViewById(R.id.single_pane_container);
            singlePane.setVisibility(View.VISIBLE);
            singlePane.addView(fleetContentView);
        }

        // Make visible (overlay layout starts as GONE)
        fleetContentView.setVisibility(View.VISIBLE);

        // Restore state if available
        if (savedInstanceState != null) {
            selectedFleetIndex = savedInstanceState.getInt(STATE_FLEET_INDEX, 0);
            seekcn_internal = savedInstanceState.getInt(STATE_SEEK_CN, -1);
            switch_status = savedInstanceState.getInt(STATE_SWITCH, 1);
        } else {
            // Not a config change rebuild — try restoring from SharedPreferences
            // (battle/quest end reopen scenario)
            restorePanelStateFromPrefs();
        }

        if (seekcn_internal == -1) {
            try {
                seekcn_internal = Integer.parseInt(getStringPreferences(getApplicationContext(), PREF_KCA_SEEK_CN));
            } catch (NumberFormatException e) {
                seekcn_internal = 0;
            }
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

        // Item popup view (Q2: use PopupWindow for equipment detail)
        itemPopupView = inflater.inflate(R.layout.view_battleview_items, null);
        itemPopupWindow = new PopupWindow(itemPopupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                false);
        itemPopupWindow.setTouchable(false);
        itemPopupWindow.setOutsideTouchable(false);

        // Setup ship item touch listeners for equipment popup
        setupItemTouchListeners();

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
                if (splitPaneEnabled) {
                    // In split-pane mode, don't close — switch to battle tab instead
                    switchToTab(RightPanePagerAdapter.TAB_BATTLE);
                    return;
                }
                // Save current state for restoration after battle/quest ends
                savePanelStateToPrefs();

                // Mark: panel was force-closed by battle/quest, needs reopen when done
                SharedPreferences prefs = getSharedPreferences("pref", MODE_PRIVATE);
                prefs.edit().putBoolean(PREF_PANEL_PENDING_REOPEN, true).apply();

                closedByBroadcast = true;
                finish();
            }
        };

        // Split-pane broadcast receivers for tab switching
        if (splitPaneEnabled) {
            showBattleReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    switchToTab(RightPanePagerAdapter.TAB_BATTLE);
                    // Tell BattleFragment to refresh
                    androidx.fragment.app.Fragment f = getSupportFragmentManager()
                            .findFragmentByTag("f" + RightPanePagerAdapter.TAB_BATTLE);
                    if (f instanceof BattleFragment) {
                        ((BattleFragment) f).refreshBattleData();
                    }
                }
            };
            showQuestReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    switchToTab(RightPanePagerAdapter.TAB_QUEST);
                }
            };
            tabSwitchReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    int tabIndex = intent.getIntExtra(EXTRA_TAB_INDEX, -1);
                    if (tabIndex >= 0) {
                        switchToTab(tabIndex);
                    }
                }
            };
            questUpdateReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    bindQuestTrackData();
                }
            };
        }
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
        // In split pane mode, hide menu buttons — right pane has Menu tab
        ViewGroup fleetMenuArea = fleetContentView.findViewById(R.id.viewbutton_area);
        if (splitPaneEnabled && fleetMenuArea != null) {
            fleetMenuArea.setVisibility(View.GONE);
            return;
        }
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
        boolean orderApplied = false;
        if (!order_data.isEmpty()) {
            try {
                JsonArray order = JsonParser.parseString(order_data).getAsJsonArray();
                for (int i = 0; i < order.size(); i++) {
                    int idx = order.get(i).getAsInt();
                    if (idx >= 0 && idx < menuBtnList.size()) {
                        fleetMenuArea.addView(menuBtnList.get(idx));
                    }
                }
                orderApplied = true;
            } catch (Exception e) {
                // Malformed JSON — fall through to default order
                fleetMenuArea.removeAllViews();
            }
        }
        if (!orderApplied) {
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

    @SuppressLint("ClickableViewAccessibility")
    private void setupItemTouchListeners() {
        for (int i = 0; i < 12; i++) {
            fleetDataManager.getFleetViewItem(fleetContentView, i)
                    .setOnTouchListener(fleetViewItemTouchListener);
        }
    }

    private boolean isInsideView(View view, float x, float y) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        float viewLeft = location[0];
        float viewTop = location[1];
        float viewRight = viewLeft + view.getWidth();
        float viewBottom = viewTop + view.getHeight();
        return (x >= viewLeft && x <= viewRight && y >= viewTop && y <= viewBottom);
    }

    private int itemPopupSelected = -1;
    private JsonArray cachedDeckportForPopup = null; // C3: cached to avoid DB queries on ACTION_MOVE

    @SuppressLint("ClickableViewAccessibility")
    private final View.OnTouchListener fleetViewItemTouchListener = (v, event) -> {
        float x = event.getRawX();
        float y = event.getRawY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_DOWN:
                // C3: Only query DB on ACTION_DOWN, reuse cached data on ACTION_MOVE
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    cachedDeckportForPopup = dbHelper.getJsonArrayValue(DB_KEY_DECKPORT);
                }
                if (cachedDeckportForPopup == null) break;

                for (int i = 0; i < 12; i++) {
                    if (isInsideView(fleetDataManager.getFleetViewItem(fleetContentView, i), x, y)) {
                        if (itemPopupSelected != i) {
                            // Load data for new selection using cached deckport
                            JsonArray data;
                            int shipIndex;
                            if (fleetDataManager.isCombined(selectedFleetIndex)) {
                                if (i < 6) {
                                    data = deckInfoCalc.getDeckListInfo(
                                            cachedDeckportForPopup,
                                            0, DECKINFO_REQ_LIST, KC_DECKINFO_REQ_LIST);
                                } else {
                                    data = deckInfoCalc.getDeckListInfo(
                                            cachedDeckportForPopup,
                                            1, DECKINFO_REQ_LIST, KC_DECKINFO_REQ_LIST);
                                }
                                shipIndex = i % 6;
                            } else {
                                data = deckInfoCalc.getDeckListInfo(
                                        cachedDeckportForPopup,
                                        selectedFleetIndex, DECKINFO_REQ_LIST, KC_DECKINFO_REQ_LIST);
                                shipIndex = i;
                            }
                            if (shipIndex < data.size()) {
                                JsonObject udata = data.get(shipIndex).getAsJsonObject().getAsJsonObject("user");
                                JsonObject kcdata = data.get(shipIndex).getAsJsonObject().getAsJsonObject("kc");

                                String ship_id = udata.get("ship_id").getAsString();
                                int ship_married = udata.get("lv").getAsInt() >= 100 ? 1 : 0;
                                JsonObject itemData = new JsonObject();
                                itemData.add("api_slot", udata.get("slot"));
                                itemData.add("api_slot_ex", udata.get("slot_ex"));
                                itemData.add("api_onslot", udata.get("onslot"));
                                itemData.add("api_maxslot", kcdata.get("maxeq"));
                                fleetDataManager.bindItemPopupView(itemPopupView, itemData, ship_id, ship_married);
                            }
                        }

                        // Show/update popup position
                        int xmargin = (int) getResources().getDimension(R.dimen.item_popup_xmargin);
                        int ymargin = (int) getResources().getDimension(R.dimen.item_popup_ymargin);

                        // Measure popup to get dimensions
                        itemPopupView.measure(
                                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                        int popupWidth = itemPopupView.getMeasuredWidth();
                        int popupHeight = itemPopupView.getMeasuredHeight();

                        int[] rootLocation = new int[2];
                        fleetContentView.getLocationOnScreen(rootLocation);

                        int popupX = (int) (x - rootLocation[0] + xmargin);
                        int popupY = (int) (y - rootLocation[1] - ymargin - popupHeight);

                        // Adjust if going off-screen right
                        int screenWidth = getResources().getDisplayMetrics().widthPixels;
                        if (x + popupWidth > screenWidth) {
                            popupX = (int) (x - rootLocation[0] - xmargin - popupWidth);
                        }

                        if (itemPopupWindow.isShowing()) {
                            itemPopupWindow.update(popupX, popupY, -1, -1);
                        } else {
                            itemPopupWindow.showAtLocation(fleetContentView, Gravity.NO_GRAVITY,
                                    popupX + rootLocation[0], popupY + rootLocation[1]);
                        }
                        itemPopupSelected = i;
                        return true;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (itemPopupWindow.isShowing()) {
                    itemPopupWindow.dismiss();
                }
                itemPopupSelected = -1;
                cachedDeckportForPopup = null; // C3: release cached data
                break;
        }
        return false;
    };

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

    /** Initialize the left pane: inflate layout, setup collapsible sections, inject fleetContentView */
    private void setupLeftPane(FrameLayout leftPaneContainer, Context ctx) {
        leftPaneView = LayoutInflater.from(ctx)
                .inflate(R.layout.panel_left_pane, leftPaneContainer, true);

        // Fleet section — compact layout with inline fleet tabs
        setupCompactFleetSection();

        // Reset timer section — collapsible, default collapsed
        setupResetTimerCollapse();

        // Dock timer grids — create TextViews once; ticks only call setText/setTextColor
        initDockGrids();
    }

    /** Setup collapsible reset timer section (default collapsed). */
    private void setupResetTimerCollapse() {
        View header = leftPaneView.findViewById(R.id.reset_timer_header);
        final View content = leftPaneView.findViewById(R.id.reset_timer_content);
        final TextView icon = leftPaneView.findViewById(R.id.reset_timer_expand_icon);
        if (header == null || content == null) return;
        // Default: collapsed
        content.setVisibility(View.GONE);
        if (icon != null) icon.setText("▸");
        header.setOnClickListener(v -> {
            if (content.getVisibility() == View.VISIBLE) {
                content.setVisibility(View.GONE);
                if (icon != null) icon.setText("▸");
            } else {
                content.setVisibility(View.VISIBLE);
                if (icon != null) icon.setText("▾");
            }
        });
    }

    /** Setup the compact fleet section: fleet tabs + ship list (no collapsing) */
    private void setupCompactFleetSection() {
        // Fleet tab click listeners
        int[] tabIds = {R.id.compact_tab_1, R.id.compact_tab_2, R.id.compact_tab_3,
                R.id.compact_tab_4, R.id.compact_tab_combined};
        for (int i = 0; i < tabIds.length; i++) {
            int fleetIdx = i;
            leftPaneView.findViewById(tabIds[i]).setOnClickListener(v -> {
                selectedFleetIndex = fleetIdx;
                bindCompactFleetData();
            });
        }
    }

    /** Update compact fleet tab highlighting */
    private void updateCompactFleetTabs() {
        if (leftPaneView == null) return;
        int[] tabIds = {R.id.compact_tab_1, R.id.compact_tab_2, R.id.compact_tab_3,
                R.id.compact_tab_4, R.id.compact_tab_combined};
        for (int i = 0; i < tabIds.length; i++) {
            TextView tab = leftPaneView.findViewById(tabIds[i]);
            if (tab == null) continue;
            if (i == selectedFleetIndex) {
                tab.setBackgroundColor(ContextCompat.getColor(this,R.color.colorAccent));
            } else {
                if (i < 4 && KcaExpedition2.isInExpedition(i)) {
                    tab.setBackgroundColor(ContextCompat.getColor(this,R.color.colorFleetInfoExpedition));
                } else if (i < 4 && KcaMoraleInfo.getMoraleCompleteTime(i) > 0) {
                    tab.setBackgroundColor(ContextCompat.getColor(this,R.color.colorFleetInfoNotGoodStatus));
                } else if (i == FLEET_COMBINED_ID &&
                        (KcaMoraleInfo.getMoraleCompleteTime(0) > 0 || KcaMoraleInfo.getMoraleCompleteTime(1) > 0)) {
                    tab.setBackgroundColor(ContextCompat.getColor(this,R.color.colorFleetInfoNotGoodStatus));
                } else {
                    tab.setBackgroundColor(0x00000000); // transparent
                }
            }
        }
    }

    /** Bind compact fleet data to the left pane compact fleet section */
    private void bindCompactFleetData() {
        if (leftPaneView == null) return;

        LinearLayout shipList = leftPaneView.findViewById(R.id.compact_fleet_list);
        if (shipList == null) return;
        shipList.removeAllViews();
        expandedShipRow = null;

        JsonArray deckportdata = dbHelper.getJsonArrayValue(DB_KEY_DECKPORT);
        if (!KcaFleetViewService.isReady || deckportdata == null || deckportdata.size() == 0) {
            TextView infoLine = leftPaneView.findViewById(R.id.compact_fleet_info);
            if (infoLine != null) {
                infoLine.setBackgroundColor(ContextCompat.getColor(this,R.color.colorFleetInfoNoShip));
                infoLine.setText(getString(R.string.kca_init_content));
            }
            return;
        }

        int idx = selectedFleetIndex;
        boolean isCombined = idx == FLEET_COMBINED_ID;

        // Bind ships
        LayoutInflater inflater = LayoutInflater.from(this);
        if (isCombined) {
            bindCompactShipList(inflater, shipList, deckportdata, 0);
            bindCompactShipList(inflater, shipList, deckportdata, 1);
        } else if (idx < deckportdata.size()) {
            bindCompactShipList(inflater, shipList, deckportdata, idx);
        }

        // Update fleet tabs
        updateCompactFleetTabs();

        // Build info line
        bindCompactFleetInfoLine(deckportdata, idx, isCombined);
    }

    private void bindCompactShipList(LayoutInflater inflater, LinearLayout shipList,
                                      JsonArray deckportdata, int deckIdx) {
        int[] shipIdList = deckInfoCalc.getDeckList(deckportdata, deckIdx);
        for (int shipId : shipIdList) {
            if (shipId <= 0) continue;

            View row = inflater.inflate(R.layout.view_fleet_compact_item, shipList, false);

            // Single combined query for all ship data
            JsonObject userData = KcaApiData.getUserShipDataById(shipId,
                    "ship_id,lv,nowhp,maxhp,cond,slot,slot_ex,onslot,maxslot");
            if (userData == null) continue;

            int mstShipId = userData.has("ship_id") ? userData.get("ship_id").getAsInt() : 0;

            // Single combined master data query
            JsonObject kcData = KcaApiData.getKcShipDataById(mstShipId, "name,slot_num");
            String shipName = (kcData != null && kcData.has("name"))
                    ? KcaApiData.getShipTranslation(kcData.get("name").getAsString(), mstShipId, false)
                    : "?";
            int slotNum = (kcData != null && kcData.has("slot_num"))
                    ? kcData.get("slot_num").getAsInt() : 5;

            int lv = userData.has("lv") ? userData.get("lv").getAsInt() : 0;
            int nowHp = userData.has("nowhp") ? userData.get("nowhp").getAsInt() : 0;
            int maxHp = userData.has("maxhp") ? userData.get("maxhp").getAsInt() : 1;
            int cond = userData.has("cond") ? userData.get("cond").getAsInt() : 0;

            // Name + Lv
            TextView nameView = row.findViewById(R.id.compact_name);
            nameView.setText(shipName + " Lv" + lv);

            // HP text + color
            TextView hpView = row.findViewById(R.id.compact_hp);
            hpView.setText(nowHp + "/" + maxHp);
            if (nowHp * 4 <= maxHp) {
                hpView.setTextColor(ContextCompat.getColor(this,R.color.colorHeavyDmgState));
            } else if (nowHp * 2 <= maxHp) {
                hpView.setTextColor(ContextCompat.getColor(this,R.color.colorModerateDmgState));
            } else if (nowHp * 4 <= maxHp * 3) {
                hpView.setTextColor(ContextCompat.getColor(this,R.color.colorLightDmgState));
            } else if (nowHp < maxHp) {
                hpView.setTextColor(ContextCompat.getColor(this,R.color.colorNormalState));
            } else {
                hpView.setTextColor(ContextCompat.getColor(this,R.color.colorFullState));
            }

            // HP bar
            ProgressBar hpBar = row.findViewById(R.id.compact_hp_bar);
            hpBar.setMax(maxHp);
            hpBar.setProgress(nowHp);

            // Heavy damage background warning on the row
            if (nowHp * 4 <= maxHp) {
                View mainRow = row.findViewById(R.id.compact_main_row);
                mainRow.setBackgroundColor(ContextCompat.getColor(this,R.color.colorFleetWarning));
            }

            // Cond badge
            TextView condView = row.findViewById(R.id.compact_cond);
            condView.setText(String.valueOf(cond));
            if (cond > 49) {
                condView.setBackgroundColor(ContextCompat.getColor(this,R.color.colorFleetShipKira));
                condView.setTextColor(ContextCompat.getColor(this,R.color.colorPrimaryDark));
            } else if (cond >= 40) {
                condView.setBackgroundColor(ContextCompat.getColor(this,R.color.colorFleetInfoBtn));
                condView.setTextColor(ContextCompat.getColor(this,R.color.white));
            } else if (cond >= 30) {
                condView.setBackgroundColor(ContextCompat.getColor(this,R.color.colorFleetInfoBtn));
                condView.setTextColor(ContextCompat.getColor(this,R.color.colorFleetShipFatigue1));
            } else if (cond >= 20) {
                condView.setBackgroundColor(ContextCompat.getColor(this,R.color.colorFleetShipFatigue1));
                condView.setTextColor(ContextCompat.getColor(this,R.color.white));
            } else {
                condView.setBackgroundColor(ContextCompat.getColor(this,R.color.colorFleetShipFatigue2));
                condView.setTextColor(ContextCompat.getColor(this,R.color.white));
            }

            // Equipment icons in compact row (from already-loaded userData)
            LinearLayout equipIcons = row.findViewById(R.id.compact_equip_icons);
            if (userData.has("slot")) {
                JsonArray slot = userData.getAsJsonArray("slot");
                int slotEx = userData.has("slot_ex") ? userData.get("slot_ex").getAsInt() : 0;
                float density = getResources().getDisplayMetrics().density;
                int iconSizePx = (int) (14 * density);
                int gapPx = (int) (1 * density);

                for (int i = 0; i < slotNum && i < slot.size(); i++) {
                    int itemId = slot.get(i).getAsInt();
                    if (itemId == -1) continue;
                    addCompactEquipIcon(equipIcons, itemId, iconSizePx, gapPx);
                }
                if (slotEx > 0) {
                    addCompactEquipIcon(equipIcons, slotEx, iconSizePx, gapPx);
                }
            }

            // Lazy-populate detail: store shipId + slotNum as tags, populate on first expand
            row.setTag(R.id.compact_equip_detail, shipId);
            row.setTag(R.id.compact_expand_icon, slotNum);

            // Click to expand/collapse
            row.setOnClickListener(v -> toggleShipExpand(v));

            shipList.addView(row);
        }
    }

    private void addCompactEquipIcon(LinearLayout container, int itemId, int sizePx, int gapPx) {
        try {
            JsonObject itemData = KcaApiData.getUserItemStatusById(itemId, "level", "type");
            if (itemData == null) return;
            JsonArray typeArr = itemData.getAsJsonArray("type");
            if (typeArr == null || typeArr.size() < 4) return;
            int iconType = typeArr.get(3).getAsInt();

            ImageView icon = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
            if (container.getChildCount() > 0) lp.setMarginStart(gapPx);
            icon.setLayoutParams(lp);
            try {
                icon.setImageResource(getId(KcaUtils.format("item_%d", iconType), R.mipmap.class));
            } catch (Exception e) {
                icon.setImageResource(R.mipmap.item_0);
            }
            container.addView(icon);
        } catch (Exception e) {
            // skip this icon on error
        }
    }

    private void populateCompactEquipDetail(LinearLayout detailContainer,
                                             JsonArray slot, int slotEx, int slotNum,
                                             JsonObject slotData) {
        JsonArray onslot = slotData.has("onslot") ? slotData.getAsJsonArray("onslot") : null;
        JsonArray maxslot = slotData.has("maxslot") ? slotData.getAsJsonArray("maxslot") : null;
        float density = getResources().getDisplayMetrics().density;
        int iconSizePx = (int) (18 * density);

        // Main slots — only show up to slot_num (actual slot count for this ship)
        for (int i = 0; i < slotNum && i < slot.size(); i++) {
            int itemId = slot.get(i).getAsInt();
            if (itemId == -1) {
                addEmptySlotRow(detailContainer);
            } else {
                int nowSlot = (onslot != null && i < onslot.size()) ? onslot.get(i).getAsInt() : -1;
                int maxSlotVal = (maxslot != null && i < maxslot.size()) ? maxslot.get(i).getAsInt() : -1;
                addEquipDetailRow(detailContainer, itemId, iconSizePx, density, nowSlot, maxSlotVal);
            }
        }

        // Extra slot section (補強増設)
        // slotEx == 0: not unlocked → don't show at all
        // slotEx == -1: unlocked but empty → show divider + "空"
        // slotEx > 0: equipped → show divider + equipment
        if (slotEx != 0) {
            // Divider line between main slots and extra slot
            View divider = new View(this);
            divider.setBackgroundColor(ContextCompat.getColor(this, R.color.colorDividerLight));
            LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int) (1 * density));
            divLp.setMargins(0, (int) (2 * density), 0, (int) (2 * density));
            divider.setLayoutParams(divLp);
            detailContainer.addView(divider);

            if (slotEx > 0) {
                addEquipDetailRow(detailContainer, slotEx, iconSizePx, density, -1, -1);
            } else {
                // slotEx == -1: unlocked but empty
                addEmptySlotRow(detailContainer);
            }
        }
    }

    private void addEmptySlotRow(LinearLayout container) {
        TextView emptyRow = new TextView(this);
        emptyRow.setText(getString(R.string.panel_slot_empty));
        emptyRow.setTextSize(9);
        emptyRow.setTextColor(0x80FFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins((int) (2 * getResources().getDisplayMetrics().density), 0, 0, 0);
        emptyRow.setLayoutParams(lp);
        container.addView(emptyRow);
    }

    private void addEquipDetailRow(LinearLayout container, int itemId, int iconSizePx,
                                    float density, int nowSlot, int maxSlotVal) {
        try {
            JsonObject itemData = KcaApiData.getUserItemStatusById(itemId, "level,alv", "id,type,name");
            if (itemData == null) return;

            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLayout.setLayoutParams(rowLp);

            // Icon
            JsonArray typeArr = itemData.getAsJsonArray("type");
            int iconType = (typeArr != null && typeArr.size() > 3) ? typeArr.get(3).getAsInt() : 0;
            ImageView icon = new ImageView(this);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSizePx, iconSizePx);
            icon.setLayoutParams(iconLp);
            try {
                icon.setImageResource(getId(KcaUtils.format("item_%d", iconType), R.mipmap.class));
            } catch (Exception e) {
                icon.setImageResource(R.mipmap.item_0);
            }
            rowLayout.addView(icon);

            // Build text: "Name ★3 >>7 [04/18]"
            StringBuilder sb = new StringBuilder();
            String itemName = itemData.has("name")
                    ? KcaApiData.getSlotItemTranslation(itemData.get("name").getAsString())
                    : "?";
            sb.append(" ").append(itemName);

            int lv = itemData.has("level") ? itemData.get("level").getAsInt() : 0;
            if (lv > 0) {
                sb.append(" ").append(getString(R.string.lv_star)).append(lv);
            }

            int alv = itemData.has("alv") ? itemData.get("alv").getAsInt() : 0;
            int alvColorId = 0;
            if (alv > 0) {
                String alvStr = getString(getId(KcaUtils.format("alv_%d", alv), R.string.class));
                sb.append(" ").append(alvStr);
                alvColorId = (alv <= 3) ? 1 : 2;
            }

            // Aircraft slot count
            int itemType2 = (typeArr != null && typeArr.size() > 2) ? typeArr.get(2).getAsInt() : 0;
            if (KcaApiData.isItemAircraft(itemType2) && nowSlot >= 0 && maxSlotVal >= 0) {
                sb.append(KcaUtils.format(" [%02d/%02d]", nowSlot, maxSlotVal));
            }

            TextView textView = new TextView(this);
            textView.setText(sb.toString());
            textView.setTextSize(9);
            textView.setTextColor(ContextCompat.getColor(this,R.color.white));
            textView.setMaxLines(1);
            textView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            textLp.setMarginStart((int) (2 * density));
            textView.setLayoutParams(textLp);
            rowLayout.addView(textView);

            container.addView(rowLayout);
        } catch (Exception e) {
            // skip on error
        }
    }

    private void toggleShipExpand(View row) {
        LinearLayout detail = row.findViewById(R.id.compact_equip_detail);
        TextView expandIcon = row.findViewById(R.id.compact_expand_icon);
        if (detail == null) return;

        boolean isExpanding = detail.getVisibility() == View.GONE;

        // Lazy-load: populate detail on first expand
        if (isExpanding && detail.getChildCount() == 0) {
            Object shipIdTag = row.getTag(R.id.compact_equip_detail);
            Object slotNumTag = row.getTag(R.id.compact_expand_icon);
            if (shipIdTag instanceof Integer) {
                int shipId = (int) shipIdTag;
                int slotNum = (slotNumTag instanceof Integer) ? (int) slotNumTag : 5;
                JsonObject slotData = KcaApiData.getUserShipDataById(shipId,
                        "slot,slot_ex,onslot,maxslot");
                if (slotData != null && slotData.has("slot")) {
                    JsonArray slot = slotData.getAsJsonArray("slot");
                    int slotEx = slotData.has("slot_ex") ? slotData.get("slot_ex").getAsInt() : 0;
                    populateCompactEquipDetail(detail, slot, slotEx, slotNum, slotData);
                }
            }
            // If still empty after loading, nothing to show
            if (detail.getChildCount() == 0) return;
        }

        // Collapse previously expanded row (if different)
        if (expandedShipRow != null && expandedShipRow != row) {
            collapseShipRow(expandedShipRow);
        }

        ViewGroup parent = (ViewGroup) row.getParent();
        AutoTransition transition = new AutoTransition();
        transition.setDuration(200);
        TransitionManager.beginDelayedTransition(parent, transition);

        if (isExpanding) {
            detail.setVisibility(View.VISIBLE);
            if (expandIcon != null) expandIcon.setText("▾");
            expandedShipRow = row;
        } else {
            detail.setVisibility(View.GONE);
            if (expandIcon != null) expandIcon.setText("▸");
            expandedShipRow = null;
        }
    }

    private void collapseShipRow(View row) {
        LinearLayout detail = row.findViewById(R.id.compact_equip_detail);
        TextView expandIcon = row.findViewById(R.id.compact_expand_icon);
        if (detail != null) detail.setVisibility(View.GONE);
        if (expandIcon != null) expandIcon.setText("▸");
    }

    private void bindCompactFleetInfoLine(JsonArray deckportdata, int idx, boolean isCombined) {
        TextView infoLine = leftPaneView.findViewById(R.id.compact_fleet_info);
        if (infoLine == null) return;

        List<String> infoList = new ArrayList<>();

        // Air power
        String airPower;
        if (isCombined) {
            airPower = deckInfoCalc.getAirPowerRangeString(deckportdata, 0, KcaBattle.getEscapeFlag());
        } else {
            airPower = deckInfoCalc.getAirPowerRangeString(deckportdata, idx, null);
        }
        if (!airPower.isEmpty()) infoList.add(airPower);

        // Seek value
        double seekValue;
        if (isCombined) {
            seekValue = deckInfoCalc.getSeekValue(deckportdata, "0,1", seekcn_internal, KcaBattle.getEscapeFlag());
        } else {
            seekValue = deckInfoCalc.getSeekValue(deckportdata, String.valueOf(idx), seekcn_internal, null);
        }
        if (seekcn_internal == SEEK_PURE) {
            infoList.add(KcaUtils.format(getString(R.string.fleetview_seekvalue_d), (int) seekValue));
        } else {
            infoList.add(KcaUtils.format(getString(R.string.fleetview_seekvalue_f), seekValue));
        }

        // Speed
        String speed;
        if (isCombined) {
            speed = deckInfoCalc.getSpeedString(deckportdata, "0,1", KcaBattle.getEscapeFlag());
        } else {
            speed = deckInfoCalc.getSpeedString(deckportdata, String.valueOf(idx), null);
        }
        infoList.add(speed);

        // TP
        String tp;
        if (isCombined) {
            tp = deckInfoCalc.getTPString(deckportdata, "0,1", KcaBattle.getEscapeFlag());
        } else {
            tp = deckInfoCalc.getTPString(deckportdata, String.valueOf(idx), null);
        }
        infoList.add(tp);

        String infoText = KcaUtils.joinStr(infoList, " / ");

        // Info line background color based on fleet state
        if (idx < 4 && KcaExpedition2.isInExpedition(idx)) {
            infoLine.setBackgroundColor(ContextCompat.getColor(this,R.color.colorFleetInfoExpedition));
        } else {
            long moraleTime;
            if (isCombined) {
                moraleTime = Math.max(KcaMoraleInfo.getMoraleCompleteTime(0),
                        KcaMoraleInfo.getMoraleCompleteTime(1));
            } else {
                moraleTime = KcaMoraleInfo.getMoraleCompleteTime(idx);
            }
            if (moraleTime > 0) {
                infoLine.setBackgroundColor(ContextCompat.getColor(this,R.color.colorFleetInfoNotGoodStatus));
            } else {
                infoLine.setBackgroundColor(ContextCompat.getColor(this,R.color.colorFleetInfoNormal));
            }
        }

        infoLine.setText(infoText);
    }

    /** Initialize the right pane: ViewPager2 + TabLayout with 4 tabs */
    private void setupRightPane(FrameLayout rightPaneContainer, Context ctx) {
        View rightPane = LayoutInflater.from(ctx)
                .inflate(R.layout.panel_right_pane, rightPaneContainer, true);

        viewPager = rightPane.findViewById(R.id.right_pane_viewpager);
        TabLayout tabLayout = rightPane.findViewById(R.id.right_pane_tabs);

        pagerAdapter = new RightPanePagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        String[] tabTitles = {
            getString(R.string.panel_tab_battle),
            getString(R.string.panel_tab_quest),
            getString(R.string.panel_tab_menu),
            getString(R.string.action_settings)
        };
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position < tabTitles.length) {
                tab.setText(tabTitles[position]);
            }
        }).attach();
    }

    private static final String PREF_LEFT_PANE_WIDTH_PX = "left_pane_width_px";
    private static final int MIN_LEFT_PANE_DP = 180;
    private static final int MAX_LEFT_PANE_DP = 400;

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    /** Restore saved left pane width from SharedPreferences */
    private void restoreLeftPaneWidth(FrameLayout leftPane) {
        int savedWidth = getSharedPreferences("fleet_panel", MODE_PRIVATE)
                .getInt(PREF_LEFT_PANE_WIDTH_PX, 0);
        if (savedWidth > 0) {
            int minPx = dpToPx(MIN_LEFT_PANE_DP);
            int maxPx = dpToPx(MAX_LEFT_PANE_DP);
            savedWidth = Math.max(minPx, Math.min(maxPx, savedWidth));
            ViewGroup.LayoutParams lp = leftPane.getLayoutParams();
            lp.width = savedWidth;
            leftPane.setLayoutParams(lp);
        }
    }

    /** Setup drag-to-resize on the divider view */
    @SuppressLint("ClickableViewAccessibility")
    private void setupDividerDrag(View divider, FrameLayout leftPane) {
        final int minPx = dpToPx(MIN_LEFT_PANE_DP);
        final int maxPx = dpToPx(MAX_LEFT_PANE_DP);

        divider.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    divider.setBackgroundColor(0x80FFFFFF); // highlight on touch
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    // event.getRawX() gives absolute screen X
                    int[] loc = new int[2];
                    leftPane.getLocationOnScreen(loc);
                    int newWidth = (int) event.getRawX() - loc[0];
                    newWidth = Math.max(minPx, Math.min(maxPx, newWidth));
                    ViewGroup.LayoutParams lp = leftPane.getLayoutParams();
                    lp.width = newWidth;
                    leftPane.setLayoutParams(lp);
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    divider.setBackgroundColor(ContextCompat.getColor(FleetPanelActivity.this, R.color.colorDividerLight)); // restore
                    // Save the width
                    ViewGroup.LayoutParams finalLp = leftPane.getLayoutParams();
                    getSharedPreferences("fleet_panel", MODE_PRIVATE)
                            .edit()
                            .putInt(PREF_LEFT_PANE_WIDTH_PX, finalLp.width)
                            .apply();
                    return true;
            }
            return false;
        });
    }

    /** Switch to a specific tab in the right pane ViewPager */
    public void switchToTab(int tabIndex) {
        if (viewPager != null) {
            viewPager.setCurrentItem(tabIndex, true);
        }
    }

    /** Rank names indexed by api_rank (1-10) */
    private static final int[] RANK_STRING_IDS = {
        0, // index 0 unused
        R.string.hq_rank_1, R.string.hq_rank_2, R.string.hq_rank_3,
        R.string.hq_rank_4, R.string.hq_rank_5, R.string.hq_rank_6,
        R.string.hq_rank_7, R.string.hq_rank_8, R.string.hq_rank_9,
        R.string.hq_rank_10
    };

    /** Bind HQ info (admiral level, name, rank, ship/equip counts) to left pane */
    private void bindHQInfoData() {
        if (leftPaneView == null) return;
        JsonObject basicInfo = dbHelper.getJsonObjectValue(DB_KEY_BASICIFNO);
        if (basicInfo == null) return;

        // Line 1: Lv.XXX Nickname [Rank]
        TextView line1 = leftPaneView.findViewById(R.id.hq_info_line1);
        if (line1 != null) {
            int level = basicInfo.has("api_level") ? basicInfo.get("api_level").getAsInt() : 0;
            hqLevel = level;
            String nickname = basicInfo.has("api_nickname") ? basicInfo.get("api_nickname").getAsString() : "";
            int rankNo = basicInfo.has("api_rank") ? basicInfo.get("api_rank").getAsInt() : 0;
            String rankName = "";
            if (rankNo >= 1 && rankNo <= 10) {
                rankName = getString(RANK_STRING_IDS[rankNo]);
            }
            if (rankName.isEmpty()) {
                line1.setText(String.format("Lv.%d %s", level, nickname));
            } else {
                line1.setText(String.format("Lv.%d %s [%s]", level, nickname, rankName));
            }
        }

        // Line 2: Ship count/max  Equip count/max
        int maxShip = basicInfo.has("api_max_chara") ? basicInfo.get("api_max_chara").getAsInt() : 0;
        int maxEquip = basicInfo.has("api_max_slotitem") ? basicInfo.get("api_max_slotitem").getAsInt() : 0;
        int shipCount = dbHelper.getShipCount();
        int equipCount = dbHelper.getItemCount();

        TextView shipTv = leftPaneView.findViewById(R.id.hq_info_ship_count);
        if (shipTv != null) {
            shipTv.setText(String.format("%s: %d/%d", getString(R.string.hq_label_ships), shipCount, maxShip));
            // poi-style warning: based on remaining slots (ship < 4 remaining)
            int shipRemaining = maxShip - shipCount;
            if (shipRemaining <= 0) {
                shipTv.setTextColor(ContextCompat.getColor(this,R.color.colorSlotDanger));
            } else if (shipRemaining < 4) {
                shipTv.setTextColor(ContextCompat.getColor(this,R.color.colorSlotWarning));
            } else {
                shipTv.setTextColor(ContextCompat.getColor(this,R.color.white));
            }
        }

        TextView equipTv = leftPaneView.findViewById(R.id.hq_info_equip_count);
        if (equipTv != null) {
            equipTv.setText(String.format("%s: %d/%d", getString(R.string.hq_label_equip), equipCount, maxEquip));
            // poi-style warning: based on remaining slots (equip < 10 remaining)
            int equipRemaining = maxEquip - equipCount;
            if (equipRemaining <= 0) {
                equipTv.setTextColor(ContextCompat.getColor(this,R.color.colorSlotDanger));
            } else if (equipRemaining < 10) {
                equipTv.setTextColor(ContextCompat.getColor(this,R.color.colorSlotWarning));
            } else {
                equipTv.setTextColor(ContextCompat.getColor(this,R.color.white));
            }
        }
    }

    /** Tint compound drawables of a TextView and resize them to 14dp */
    private void tintCompoundDrawables(TextView tv, int color) {
        Drawable[] drawables = tv.getCompoundDrawablesRelative();
        int sizePx = (int) (14 * getResources().getDisplayMetrics().density);
        for (Drawable d : drawables) {
            if (d != null) {
                d.mutate();
                d.setTint(color);
                d.setBounds(0, 0, sizePx, sizePx);
            }
        }
        tv.setCompoundDrawablesRelative(drawables[0], drawables[1], drawables[2], drawables[3]);
    }

    /** Bind resource data (fuel/ammo/steel/bauxite + instant items) to left pane with icons */
    private void bindResourceData() {
        if (leftPaneView == null) return;
        JsonArray material = dbHelper.getJsonArrayValue(DB_KEY_MATERIALS);
        if (material == null) return;

        // Dynamic column count based on pane width
        GridLayout resGrid = leftPaneView.findViewById(R.id.section_resource_content);
        if (resGrid != null) {
            resGrid.post(() -> {
                int widthPx = resGrid.getWidth();
                float density = getResources().getDisplayMetrics().density;
                int widthDp = (int) (widthPx / density);
                int cols = widthDp >= 220 ? 4 : widthDp >= 150 ? 3 : 2;
                if (resGrid.getColumnCount() != cols) {
                    resGrid.setColumnCount(cols);
                    // Update each child's column spec to auto-flow
                    for (int c = 0; c < resGrid.getChildCount(); c++) {
                        View child = resGrid.getChildAt(c);
                        GridLayout.LayoutParams glp = (GridLayout.LayoutParams) child.getLayoutParams();
                        glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
                        child.setLayoutParams(glp);
                    }
                }
            });
        }

        // Resource IDs now point to TextViews inside icon+number grid cells
        int[] resIds = {
            R.id.res_fuel, R.id.res_ammo, R.id.res_steel, R.id.res_bauxite,
            R.id.res_instant_build, R.id.res_instant_repair, R.id.res_dev_material, R.id.res_screw
        };

        // Natural regen cap applies to first 4 resources only
        int regenCap = 750 + hqLevel * 250;

        for (int i = 0; i < Math.min(material.size(), resIds.length); i++) {
            TextView tv = leftPaneView.findViewById(resIds[i]);
            if (tv != null) {
                int value;
                if (material.get(i).isJsonObject()) {
                    value = material.get(i).getAsJsonObject().get("api_value").getAsInt();
                } else {
                    value = material.get(i).getAsInt();
                }
                tv.setText(String.valueOf(value));

                // Green tint for main resources still regenerating (below cap)
                if (i < 4 && value < regenCap) {
                    tv.setTextColor(ContextCompat.getColor(this,R.color.colorRegenActive));
                } else {
                    tv.setTextColor(ContextCompat.getColor(this,R.color.white));
                }
            }
        }
    }

    // ---- Phase 9E: Server Reset Countdown ----

    /** Label string resource IDs for each reset type, indexed by KcaResetTimer.TYPE_* */
    private static final int[] RESET_TYPE_LABEL_RES = {
        R.string.reset_type_practice,
        R.string.reset_type_daily,
        R.string.reset_type_weekly,
        R.string.reset_type_quarterly,
        R.string.reset_type_monthly,
        R.string.reset_type_senka,
        R.string.reset_type_eo,
    };

    /**
     * Determine which reset types are enabled by prefs (defaults: practice/daily/weekly/quarterly).
     * Returns an int[] of enabled KcaResetTimer.TYPE_* constants.
     */
    private int[] getEnabledResetTypes() {
        SharedPreferences prefs = getSharedPreferences("pref", MODE_PRIVATE);
        boolean[] show = {
            prefs.getBoolean(PREF_RESET_SHOW_PRACTICE,  true),
            prefs.getBoolean(PREF_RESET_SHOW_DAILY,     true),
            prefs.getBoolean(PREF_RESET_SHOW_WEEKLY,    true),
            prefs.getBoolean(PREF_RESET_SHOW_QUARTERLY, true),
            prefs.getBoolean(PREF_RESET_SHOW_MONTHLY,   false),
            prefs.getBoolean(PREF_RESET_SHOW_SENKA,     false),
            prefs.getBoolean(PREF_RESET_SHOW_EO,        false),
        };
        int count = 0;
        for (boolean b : show) if (b) count++;
        int[] enabled = new int[count];
        int idx = 0;
        for (int i = 0; i < show.length; i++) {
            if (show[i]) enabled[idx++] = KcaResetTimer.ALL_TYPES[i];
        }
        return enabled;
    }

    /** Bind reset timer data as a compact inline text row: "演習 05:23 | 週間 4d | ..." */
    private void bindResetTimerData() {
        if (leftPaneView == null) return;
        TextView inlineTv = leftPaneView.findViewById(R.id.reset_timer_inline);
        if (inlineTv == null) return;

        int[] types = getEnabledResetTypes();
        if (types.length == 0) {
            leftPaneView.findViewById(R.id.section_reset_timer).setVisibility(View.GONE);
            return;
        }
        leftPaneView.findViewById(R.id.section_reset_timer).setVisibility(View.VISIBLE);

        java.util.List<KcaResetTimer.ResetEntry> entries = KcaResetTimer.getResetEntries(types);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            KcaResetTimer.ResetEntry entry = entries.get(i);
            String label = (entry.type < RESET_TYPE_LABEL_RES.length)
                    ? getString(RESET_TYPE_LABEL_RES[entry.type]) : "?";
            String time = formatResetShort(entry.msUntilReset);
            if (i > 0) sb.append(" | ");
            sb.append(label).append(' ').append(time);
        }
        inlineTv.setText(sb.toString());
    }

    /**
     * Short format for reset countdowns: <1 day → "HH:MM", >=1 day → "Xd HH:MM".
     */
    private static String formatResetShort(long msUntil) {
        if (msUntil <= 0) return "00:00";
        long totalSec = msUntil / 1000;
        long days = totalSec / 86400;
        long hours = (totalSec % 86400) / 3600;
        long mins  = (totalSec % 3600) / 60;
        if (days >= 1) return days + "d " + KcaUtils.format("%02d:%02d", hours, mins);
        return KcaUtils.format("%02d:%02d", hours, mins);
    }

    // ---- Phase 9D: Dock/Construction/Expedition Timers ----

    /**
     * Create all dock grid TextViews once at setup time.
     * Combined grid: 4 rows × 4 cols (repair_name, repair_time, constr_name, constr_time).
     * Expd grid: 3 rows × 2 cols (name, time).
     * Stored in mDockCombinedCells[row*4+col] and mDockExpdCells[row*2+col].
     */
    private void initDockGrids() {
        GridLayout combinedGrid = leftPaneView.findViewById(R.id.docks_combined_grid);
        GridLayout expdGrid = leftPaneView.findViewById(R.id.docks_expd_grid);
        if (combinedGrid == null || expdGrid == null) return;

        float density = getResources().getDisplayMetrics().density;

        // Combined grid: 4 rows × 4 cols
        mDockCombinedCells = new TextView[16];
        float[] colWeights = {0.30f, 0.20f, 0.30f, 0.20f};
        boolean[] isName   = {true,  false, true,  false};
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                TextView tv = new TextView(this);
                tv.setTextSize(9);
                tv.setTextColor(getDockTextColor(KcaDockTimerData.STATE_EMPTY));
                tv.setMaxLines(1);
                tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                if (!isName[col]) tv.setTypeface(android.graphics.Typeface.MONOSPACE);
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                        GridLayout.spec(row, 1),
                        GridLayout.spec(col, 1, colWeights[col]));
                lp.width = 0;
                lp.setMargins((int)(2 * density), 0, 0, 0);
                tv.setLayoutParams(lp);
                combinedGrid.addView(tv);
                mDockCombinedCells[row * 4 + col] = tv;
            }
        }

        // Expd grid: 3 rows × 2 cols
        mDockExpdCells = new TextView[6];
        for (int row = 0; row < 3; row++) {
            // Name cell (col 0)
            TextView nameTv = new TextView(this);
            nameTv.setTextSize(9);
            nameTv.setTextColor(getDockTextColor(KcaDockTimerData.STATE_EMPTY));
            nameTv.setMaxLines(1);
            nameTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            GridLayout.LayoutParams nameLp = new GridLayout.LayoutParams(
                    GridLayout.spec(row, 1),
                    GridLayout.spec(0, 1, 0.55f));
            nameLp.width = 0;
            nameLp.setMargins((int)(2*density), 0, (int)(2*density), 0);
            nameTv.setLayoutParams(nameLp);
            expdGrid.addView(nameTv);
            mDockExpdCells[row * 2] = nameTv;

            // Time cell (col 1)
            TextView timeTv = new TextView(this);
            timeTv.setTextSize(9);
            timeTv.setTypeface(android.graphics.Typeface.MONOSPACE);
            timeTv.setTextColor(getDockTextColor(KcaDockTimerData.STATE_EMPTY));
            GridLayout.LayoutParams timeLp = new GridLayout.LayoutParams(
                    GridLayout.spec(row, 1),
                    GridLayout.spec(1, 1, 0.45f));
            timeLp.width = 0;
            timeLp.setMargins(0, 0, (int)(2*density), 0);
            timeTv.setLayoutParams(timeLp);
            expdGrid.addView(timeTv);
            mDockExpdCells[row * 2 + 1] = timeTv;
        }
    }

    /** Bind repair, construction, and expedition timer rows into the docks section. */
    private void bindDockTimerData() {
        if (leftPaneView == null) return;
        View sectionView = leftPaneView.findViewById(R.id.section_docks);
        if (sectionView == null) return;

        bindRepairConstrGrid(KcaDockTimerData.getRepairSlots(dbHelper),
                KcaDockTimerData.getConstructionSlots(dbHelper));
        bindDockGrid(KcaDockTimerData.getExpeditionSlots(dbHelper));
    }

    /**
     * Update repair and construction cells in the already-inflated combined grid.
     * Columns: repair_name | repair_time | constr_name | constr_time
     */
    private void bindRepairConstrGrid(
            java.util.List<KcaDockTimerData.DockSlot> repair,
            java.util.List<KcaDockTimerData.DockSlot> constr) {
        if (mDockCombinedCells == null) return;
        int rows = Math.max(repair.size(), constr.size());
        for (int i = 0; i < 4; i++) {
            KcaDockTimerData.DockSlot r = i < repair.size() ? repair.get(i) : null;
            KcaDockTimerData.DockSlot c = i < constr.size() ? constr.get(i) : null;
            int state0 = r != null ? r.state : KcaDockTimerData.STATE_EMPTY;
            int state2 = c != null ? c.state : KcaDockTimerData.STATE_EMPTY;
            String repairTime = (r != null && r.completionMs > 0)
                    ? KcaDockTimerData.formatTime(r.completionMs) : "";
            String constrTime = (c != null && c.completionMs > 0)
                    ? KcaDockTimerData.formatTime(c.completionMs) : "";

            mDockCombinedCells[i * 4 + 0].setText(r != null ? r.label : "—");
            mDockCombinedCells[i * 4 + 0].setTextColor(getDockTextColor(state0));
            mDockCombinedCells[i * 4 + 1].setText(repairTime);
            mDockCombinedCells[i * 4 + 1].setTextColor(getDockTextColor(state0));
            mDockCombinedCells[i * 4 + 2].setText(c != null ? c.label : "—");
            mDockCombinedCells[i * 4 + 2].setTextColor(getDockTextColor(state2));
            mDockCombinedCells[i * 4 + 3].setText(constrTime);
            mDockCombinedCells[i * 4 + 3].setTextColor(getDockTextColor(state2));
        }
    }

    private void bindDockGrid(java.util.List<KcaDockTimerData.DockSlot> slots) {
        if (mDockExpdCells == null) return;
        for (int i = 0; i < 3; i++) {
            KcaDockTimerData.DockSlot slot = i < slots.size() ? slots.get(i) : null;
            int state = slot != null ? slot.state : KcaDockTimerData.STATE_EMPTY;
            String timeStr = (slot != null && slot.completionMs > 0)
                    ? KcaDockTimerData.formatTime(slot.completionMs) : "";
            mDockExpdCells[i * 2].setText(slot != null ? slot.label : "—");
            mDockExpdCells[i * 2].setTextColor(getDockTextColor(state));
            mDockExpdCells[i * 2 + 1].setText(timeStr);
            mDockExpdCells[i * 2 + 1].setTextColor(getDockTextColor(state));
        }
    }

    private int getDockTextColor(int state) {
        switch (state) {
            case KcaDockTimerData.STATE_DONE:   return 0xFF4CAF50; // green
            case KcaDockTimerData.STATE_NEAR:   return ContextCompat.getColor(this, R.color.colorFleetShipFatigue1); // yellow
            case KcaDockTimerData.STATE_ACTIVE: return 0xFF64B5F6; // blue
            case KcaDockTimerData.STATE_LSC:    return ContextCompat.getColor(this, R.color.colorFleetShipFatigue2); // red
            case KcaDockTimerData.STATE_EMPTY:
            default:                            return 0xFF888888; // gray
        }
    }

    // ---- Quest tracking ----


    /** Bind quest tracking data to left pane */
    private void bindQuestTrackData() {
        if (leftPaneView == null) return;
        LinearLayout container = leftPaneView.findViewById(R.id.section_quest_track_content);
        if (container == null) return;
        container.removeAllViews();
        expandedQuestRow = null;

        JsonArray questList = dbHelper.getCurrentQuestList();
        if (questList == null || questList.size() == 0) {
            TextView emptyText = new TextView(this);
            emptyText.setText(getString(R.string.panel_no_tracked_quest));
            emptyText.setTextColor(ContextCompat.getColor(this,R.color.white));
            emptyText.setTextSize(11);
            emptyText.setPadding(4, 4, 4, 4);
            container.addView(emptyText);
            return;
        }

        // Get tracker data for progress info
        JsonArray trackerData = questTracker.getQuestTrackerData();
        Map<String, JsonObject> trackerMap = new HashMap<>();
        if (trackerData != null) {
            for (int i = 0; i < trackerData.size(); i++) {
                JsonObject t = trackerData.get(i).getAsJsonObject();
                trackerMap.put(t.get("id").getAsString(), t);
            }
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < questList.size(); i++) {
            JsonObject quest = questList.get(i).getAsJsonObject();
            if (!quest.has("api_no") || !quest.has("api_title")) continue;

            View row = inflater.inflate(R.layout.item_quest_track, container, false);

            // Category color bar (using existing colorQuestCategory resources)
            View catBar = row.findViewById(R.id.quest_track_category_bar);
            int category = quest.has("api_category") ? quest.get("api_category").getAsInt() : 0;
            int catColorId = getIdWithFallback(
                    KcaUtils.format("colorQuestCategory%d", category),
                    "colorQuestCategory0", R.color.class);
            catBar.setBackgroundColor(ContextCompat.getColor(this,catColorId));

            // Type badge (using localized quest_label_type strings and colorQuestLabel colors)
            TextView typeBadge = row.findViewById(R.id.quest_track_type_badge);
            int labelType = quest.has("api_label_type") ? quest.get("api_label_type").getAsInt() : 0;
            int labelStringId = getIdWithFallback(
                    KcaUtils.format("quest_label_type_%d", labelType),
                    "quest_label_type_0", R.string.class);
            typeBadge.setText(getString(labelStringId));
            int labelColorId = getIdWithFallback(
                    KcaUtils.format("colorQuestLabel%d", labelType > 100 ? 100 : labelType),
                    "colorQuestLabel0", R.color.class);
            typeBadge.setBackgroundColor(ContextCompat.getColor(this,labelColorId));

            // Title
            TextView title = row.findViewById(R.id.quest_track_title);
            title.setText(quest.get("api_title").getAsString());

            // Progress
            TextView progress = row.findViewById(R.id.quest_track_progress);
            String questId = String.valueOf(quest.get("api_no").getAsInt());
            JsonObject tracker = trackerMap.get(questId);

            if (tracker != null && tracker.has("cond")) {
                JsonArray cond = tracker.getAsJsonArray("cond");
                JsonObject trackInfo = KcaApiData.getQuestTrackInfo(questId);
                if (trackInfo != null && trackInfo.has("cond")) {
                    JsonArray condMax = trackInfo.getAsJsonArray("cond");
                    int initialOffset = KcaQuestTracker.getInitialCondValue(questId);
                    int totalCount = 0, totalRequired = 0;
                    StringBuilder subgoalText = new StringBuilder();
                    for (int j = 0; j < cond.size(); j++) {
                        int count = cond.get(j).getAsInt();
                        int required = j < condMax.size() ? condMax.get(j).getAsInt() - initialOffset : 0;
                        count = Math.min(count, required);
                        totalCount += count;
                        totalRequired += required;
                        if (cond.size() > 1 && required > 0) {
                            if (subgoalText.length() > 0) subgoalText.append(" | ");
                            subgoalText.append(count).append("/").append(required);
                        }
                    }
                    progress.setText(totalCount + "/" + totalRequired);
                    progress.setTextColor(getProgressColor(totalCount, totalRequired));

                    if (cond.size() > 1 && subgoalText.length() > 0) {
                        TextView subgoals = row.findViewById(R.id.quest_track_subgoals);
                        subgoals.setText(subgoalText.toString());
                        subgoals.setVisibility(View.VISIBLE);
                    }
                } else {
                    setProgressFromFlag(progress, quest);
                }
            } else {
                setProgressFromFlag(progress, quest);
            }

            // Populate detail section (hidden until clicked)
            LinearLayout detailSection = row.findViewById(R.id.quest_track_detail);
            populateQuestDetail(detailSection, quest, tracker, labelType);

            // Click to expand/collapse
            row.setOnClickListener(v -> toggleQuestExpand(v));

            container.addView(row);
        }
    }

    private int getProgressColor(int count, int required) {
        if (required <= 0) return 0xFFFFFFFF;
        float pct = (float) count / required;
        if (pct >= 1.0f) return 0xFF4CAF50;  // Green - complete
        if (pct >= 0.8f) return 0xFF42A5F5;  // Blue - 80%
        if (pct >= 0.5f) return 0xFFFFEB3B;  // Yellow - 50%
        return 0xFFFFFFFF;  // White - in progress
    }

    private void setProgressFromFlag(TextView progress, JsonObject quest) {
        int state = quest.has("api_state") ? quest.get("api_state").getAsInt() : 0;
        int flag = quest.has("api_progress_flag") ? quest.get("api_progress_flag").getAsInt() : 0;
        if (state == 3) {
            progress.setText("✓");
            progress.setTextColor(0xFF4CAF50);
        } else if (flag == 2) {
            progress.setText("80%");
            progress.setTextColor(0xFF42A5F5);
        } else if (flag == 1) {
            progress.setText("50%");
            progress.setTextColor(0xFFFFEB3B);
        } else {
            progress.setText("");
        }
    }

    private void populateQuestDetail(LinearLayout detail, JsonObject quest, JsonObject tracker, int labelType) {
        // Description
        TextView desc = detail.findViewById(R.id.quest_track_description);
        if (quest.has("api_detail")) {
            String text = quest.get("api_detail").getAsString().replaceAll("<br\\s*/?>", "\n");
            desc.setText(text);
            desc.setVisibility(View.VISIBLE);
        } else {
            desc.setVisibility(View.GONE);
        }

        // Per-condition breakdown
        LinearLayout condList = detail.findViewById(R.id.quest_track_cond_list);
        condList.removeAllViews();
        if (tracker != null && tracker.has("cond")) {
            String questId = tracker.get("id").getAsString();
            JsonObject trackInfo = KcaApiData.getQuestTrackInfo(questId);
            if (trackInfo != null && trackInfo.has("cond")) {
                JsonArray cond = tracker.getAsJsonArray("cond");
                JsonArray condMax = trackInfo.getAsJsonArray("cond");
                int initialOffset = KcaQuestTracker.getInitialCondValue(questId);
                for (int j = 0; j < cond.size(); j++) {
                    int count = cond.get(j).getAsInt();
                    int required = j < condMax.size() ? condMax.get(j).getAsInt() - initialOffset : 0;
                    count = Math.min(count, required);
                    if (required > 0) {
                        TextView condView = new TextView(this);
                        condView.setText("  ● " + count + " / " + required);
                        condView.setTextColor(getProgressColor(count, required));
                        condView.setTextSize(10);
                        condList.addView(condView);
                    }
                }
            }
        }

        // Type label
        TextView typeLabel = detail.findViewById(R.id.quest_track_type_label);
        int labelStringId = getIdWithFallback(
                KcaUtils.format("quest_label_type_%d", labelType),
                "quest_label_type_0", R.string.class);
        typeLabel.setText("[" + getString(labelStringId) + "]");
    }

    private void toggleQuestExpand(View row) {
        LinearLayout detail = row.findViewById(R.id.quest_track_detail);
        TextView expandIcon = row.findViewById(R.id.quest_track_expand_icon);
        boolean isExpanding = detail.getVisibility() == View.GONE;

        // Collapse previously expanded row (if different)
        if (expandedQuestRow != null && expandedQuestRow != row) {
            collapseQuestRow(expandedQuestRow);
        }

        // Use TransitionManager for smooth animation
        ViewGroup parent = (ViewGroup) row.getParent();
        AutoTransition transition = new AutoTransition();
        transition.setDuration(200);
        TransitionManager.beginDelayedTransition(parent, transition);

        if (isExpanding) {
            detail.setVisibility(View.VISIBLE);
            if (expandIcon != null) expandIcon.setText("▾");
            expandedQuestRow = row;
        } else {
            detail.setVisibility(View.GONE);
            if (expandIcon != null) expandIcon.setText("▸");
            expandedQuestRow = null;
        }
    }

    private void collapseQuestRow(View row) {
        LinearLayout detail = row.findViewById(R.id.quest_track_detail);
        TextView expandIcon = row.findViewById(R.id.quest_track_expand_icon);
        if (detail != null) detail.setVisibility(View.GONE);
        if (expandIcon != null) expandIcon.setText("▸");
    }

    private void refreshFleetData() {
        // W5: DB queries run on main thread here. Known issue — future refactor to background thread.
        // W6: Layout inflation in bindCompactFleetData creates views without recycling. Future RecyclerView migration.
        // Restore isReady flag after process death if DB has deckport data
        if (!KcaFleetViewService.isReady) {
            JsonArray deckport = dbHelper.getJsonArrayValue(DB_KEY_DECKPORT);
            if (deckport != null && deckport.size() > 0) {
                KcaFleetViewService.setReadyFlag(true);
            }
        }

        fleetDataManager.setSelectedFleetIndex(selectedFleetIndex);
        fleetDataManager.setSwitchStatus(switch_status);
        fleetDataManager.setSeekCnInternal(seekcn_internal);
        fleetDataManager.bindFleetData(fleetContentView);

        // Refresh left pane data (split pane mode only)
        bindHQInfoData();
        bindResourceData();
        bindResetTimerData();
        bindDockTimerData();
        bindQuestTrackData();
        if (splitPaneEnabled) {
            bindCompactFleetData();
        }
    }

    private void startTimer() {
        stopTimer();
        timeScheduler = Executors.newSingleThreadScheduledExecutor();
        timeScheduler.scheduleWithFixedDelay(() -> {
            mHandler.post(() -> {
                if (isDestroyed()) return;
                if (fleetContentView != null && fleetDataManager != null) {
                    fleetDataManager.formatFleetInfoLine(fleetContentView, -2);
                }
                // Tick reset timers and dock timers every second
                bindResetTimerData();
                bindDockTimerData();
            });
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void stopTimer() {
        if (timeScheduler != null) {
            timeScheduler.shutdownNow();
            timeScheduler = null;
        }
        mHandler.removeCallbacksAndMessages(null);
    }

    /**
     * Save current panel state to SharedPreferences.
     * Called before finish() when panel is closed by broadcast (battle/quest activation).
     */
    private void savePanelStateToPrefs() {
        SharedPreferences prefs = getSharedPreferences("pref", MODE_PRIVATE);
        prefs.edit()
            .putInt(PREF_PANEL_LAST_FLEET_INDEX, selectedFleetIndex)
            .putInt(PREF_PANEL_LAST_SEEK_CN, seekcn_internal)
            .putInt(PREF_PANEL_LAST_SWITCH_STATUS, switch_status)
            .apply();
    }

    /**
     * Restore panel state from SharedPreferences.
     * Used on fresh start (not config change rebuild) to restore state after battle/quest reopen.
     */
    private void restorePanelStateFromPrefs() {
        SharedPreferences prefs = getSharedPreferences("pref", MODE_PRIVATE);
        selectedFleetIndex = prefs.getInt(PREF_PANEL_LAST_FLEET_INDEX, 0);
        seekcn_internal = prefs.getInt(PREF_PANEL_LAST_SEEK_CN, -1);
        switch_status = prefs.getInt(PREF_PANEL_LAST_SWITCH_STATUS, 1);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // singleTask re-entry: refresh data
        refreshFleetData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isFleetPanelOpen = true;

        // If user switched back to Overlay mode in settings, close this Activity
        if (!DISPLAY_MODE_SPLIT.equals(
                getStringPreferences(getApplicationContext(), PREF_DISPLAY_MODE))) {
            finish();
            return;
        }



        // Register broadcast receivers
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(refreshReceiver, new IntentFilter(BROADCAST_REFRESH_FLEETVIEW));
        lbm.registerReceiver(closeReceiver, new IntentFilter(CLOSE_FLEET_PANEL_ACTION));
        if (showBattleReceiver != null) {
            lbm.registerReceiver(showBattleReceiver, new IntentFilter(BROADCAST_SHOW_BATTLE_FRAGMENT));
        }
        if (showQuestReceiver != null) {
            lbm.registerReceiver(showQuestReceiver, new IntentFilter(BROADCAST_SHOW_QUEST_FRAGMENT));
        }
        if (tabSwitchReceiver != null) {
            lbm.registerReceiver(tabSwitchReceiver, new IntentFilter(BROADCAST_TAB_SWITCH));
        }
        if (questUpdateReceiver != null) {
            IntentFilter questFilter = new IntentFilter(KCA_MSG_QUEST_VIEW_LIST);
            questFilter.addAction(KCA_MSG_QUEST_COMPLETE);
            lbm.registerReceiver(questUpdateReceiver, questFilter);
        }

        // Refresh data from DB
        refreshFleetData();
        startTimer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTimer();
        if (itemPopupWindow != null && itemPopupWindow.isShowing()) {
            itemPopupWindow.dismiss();
        }
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.unregisterReceiver(refreshReceiver);
        lbm.unregisterReceiver(closeReceiver);
        if (showBattleReceiver != null) {
            lbm.unregisterReceiver(showBattleReceiver);
        }
        if (showQuestReceiver != null) {
            lbm.unregisterReceiver(showQuestReceiver);
        }
        if (tabSwitchReceiver != null) {
            lbm.unregisterReceiver(tabSwitchReceiver);
        }
        if (questUpdateReceiver != null) {
            lbm.unregisterReceiver(questUpdateReceiver);
        }
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
        isFleetPanelOpen = false;
        stopTimer();
        if (itemPopupWindow != null) {
            itemPopupWindow.dismiss();
            itemPopupWindow = null;
        }

        // closedByBroadcast flag safety depends on AndroidManifest.xml declaring
        // configChanges="smallestScreenSize|screenLayout|screenSize" for FleetPanelActivity.
        // This ensures fold/unfold and rotation do not trigger onDestroy.
        // If configChanges is modified in the future, this flag's safety must be re-evaluated.

        // User manually closed (head click / back button) → clear reopen flag
        if (!closedByBroadcast) {
            SharedPreferences prefs = getSharedPreferences("pref", MODE_PRIVATE);
            prefs.edit().putBoolean(PREF_PANEL_PENDING_REOPEN, false).apply();
        }

        if (prefListener != null) {
            getSharedPreferences("pref", MODE_PRIVATE)
                    .unregisterOnSharedPreferenceChangeListener(prefListener);
        }

        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Dismiss popup on configuration change to avoid stale positioning
        if (itemPopupWindow != null && itemPopupWindow.isShowing()) {
            itemPopupWindow.dismiss();
        }
        itemPopupSelected = -1;
        // Re-bind fleet data to update layout for new orientation
        refreshFleetData();
    }
}
