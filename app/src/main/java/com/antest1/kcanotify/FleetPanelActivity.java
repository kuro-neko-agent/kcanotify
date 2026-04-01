package com.antest1.kcanotify;

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
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.DisplayMetrics;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
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

import android.content.SharedPreferences;
import static com.antest1.kcanotify.KcaConstants.PREF_KCA_SEEK_CN;
import static com.antest1.kcanotify.KcaFleetViewService.DECKINFO_REQ_LIST;
import static com.antest1.kcanotify.KcaFleetViewService.KC_DECKINFO_REQ_LIST;
import static com.antest1.kcanotify.KcaFleetViewService.fleetview_menu_keys;
import static com.antest1.kcanotify.KcaService.BROADCAST_REFRESH_FLEETVIEW;
import static com.antest1.kcanotify.KcaUseStatConstant.FV_BTN_PRESS;
import static com.antest1.kcanotify.KcaUtils.getId;
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

    private View fleetContentView; // The inflated fleet view content
    private View itemPopupView;    // For equipment popup
    private PopupWindow itemPopupWindow;

    private Handler mHandler;
    private ScheduledExecutorService timeScheduler;

    private int selectedFleetIndex = 0;
    private int seekcn_internal = -1;
    private int switch_status = 1;

    private View leftPaneView; // Left pane root (only in split pane mode)
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
                        dividerRef.setBackgroundColor(0x40FFFFFF);
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

    @SuppressLint("ClickableViewAccessibility")
    private final View.OnTouchListener fleetViewItemTouchListener = (v, event) -> {
        float x = event.getRawX();
        float y = event.getRawY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_DOWN:
                JsonArray deckportCheck = dbHelper.getJsonArrayValue(DB_KEY_DECKPORT);
                if (deckportCheck == null) break;

                for (int i = 0; i < 12; i++) {
                    if (isInsideView(fleetDataManager.getFleetViewItem(fleetContentView, i), x, y)) {
                        if (itemPopupSelected != i) {
                            // Load data for new selection
                            JsonArray data;
                            int shipIndex;
                            if (fleetDataManager.isCombined(selectedFleetIndex)) {
                                if (i < 6) {
                                    data = deckInfoCalc.getDeckListInfo(
                                            dbHelper.getJsonArrayValue(DB_KEY_DECKPORT),
                                            0, DECKINFO_REQ_LIST, KC_DECKINFO_REQ_LIST);
                                } else {
                                    data = deckInfoCalc.getDeckListInfo(
                                            dbHelper.getJsonArrayValue(DB_KEY_DECKPORT),
                                            1, DECKINFO_REQ_LIST, KC_DECKINFO_REQ_LIST);
                                }
                                shipIndex = i % 6;
                            } else {
                                data = deckInfoCalc.getDeckListInfo(
                                        dbHelper.getJsonArrayValue(DB_KEY_DECKPORT),
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

        // Resource section
        setupCollapsibleSection(leftPaneView, R.id.section_resource_header,
                R.id.section_resource_content, R.id.section_resource_arrow);

        // Fleet section — inject fleetContentView
        setupCollapsibleSection(leftPaneView, R.id.section_fleet_header,
                R.id.section_fleet_content, R.id.section_fleet_arrow);
        FrameLayout fleetContainer = leftPaneView.findViewById(R.id.section_fleet_content);
        fleetContainer.addView(fleetContentView);

        // Quest tracking section
        setupCollapsibleSection(leftPaneView, R.id.section_quest_track_header,
                R.id.section_quest_track_content, R.id.section_quest_track_arrow);
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
                    divider.setBackgroundColor(0x40FFFFFF); // restore
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

    /** Generic collapsible section toggle */
    private void setupCollapsibleSection(View parent, int headerId, int contentId, int arrowId) {
        View header = parent.findViewById(headerId);
        View content = parent.findViewById(contentId);
        ImageView arrow = (ImageView) parent.findViewById(arrowId);

        header.setOnClickListener(v -> {
            boolean isVisible = content.getVisibility() == View.VISIBLE;
            content.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            arrow.setImageResource(isVisible
                    ? R.drawable.ic_arrow_down
                    : R.drawable.ic_arrow_up);
        });
    }

    /** Rank names indexed by api_rank_no (1-10) */
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
            int rankNo = basicInfo.has("api_rank_no") ? basicInfo.get("api_rank_no").getAsInt() : 0;
            String rankName = "";
            if (rankNo >= 1 && rankNo <= 10) {
                rankName = getString(RANK_STRING_IDS[rankNo]);
            }
            line1.setText(String.format("Lv.%d %s [%s]", level, nickname, rankName));
        }

        // Line 2: Ship count/max  Equip count/max
        int maxShip = basicInfo.has("api_max_chara") ? basicInfo.get("api_max_chara").getAsInt() : 0;
        int maxEquip = basicInfo.has("api_max_slotitem") ? basicInfo.get("api_max_slotitem").getAsInt() : 0;
        int shipCount = dbHelper.getShipCount();
        int equipCount = dbHelper.getItemCount();

        TextView shipTv = leftPaneView.findViewById(R.id.hq_info_ship_count);
        if (shipTv != null) {
            shipTv.setText(String.format("\u2693 %d/%d", shipCount, maxShip));
            if (maxShip > 0) {
                float ratio = (float) shipCount / maxShip;
                if (ratio > 0.95f) {
                    shipTv.setTextColor(getResources().getColor(R.color.colorSlotDanger));
                } else if (ratio > 0.90f) {
                    shipTv.setTextColor(getResources().getColor(R.color.colorSlotWarning));
                } else {
                    shipTv.setTextColor(getResources().getColor(R.color.white));
                }
            }
        }

        TextView equipTv = leftPaneView.findViewById(R.id.hq_info_equip_count);
        if (equipTv != null) {
            equipTv.setText(String.format("\u2694 %d/%d", equipCount, maxEquip));
            if (maxEquip > 0) {
                float ratio = (float) equipCount / maxEquip;
                if (ratio > 0.95f) {
                    equipTv.setTextColor(getResources().getColor(R.color.colorSlotDanger));
                } else if (ratio > 0.90f) {
                    equipTv.setTextColor(getResources().getColor(R.color.colorSlotWarning));
                } else {
                    equipTv.setTextColor(getResources().getColor(R.color.white));
                }
            }
        }
    }

    /** Bind resource data (fuel/ammo/steel/bauxite + instant items) to left pane with icons */
    private void bindResourceData() {
        if (leftPaneView == null) return;
        JsonArray material = dbHelper.getJsonArrayValue(DB_KEY_MATERIALS);
        if (material == null) return;

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
                    tv.setTextColor(getResources().getColor(R.color.colorRegenActive));
                } else {
                    tv.setTextColor(getResources().getColor(R.color.white));
                }
            }
        }
    }

    /** Bind quest tracking data to left pane */
    private void bindQuestTrackData() {
        if (leftPaneView == null) return;
        LinearLayout container = leftPaneView.findViewById(R.id.section_quest_track_content);
        if (container == null) return;
        container.removeAllViews();

        JsonArray questList = dbHelper.getCurrentQuestList();
        if (questList == null || questList.size() == 0) {
            TextView emptyText = new TextView(this);
            emptyText.setText(getString(R.string.panel_no_tracked_quest));
            emptyText.setTextColor(getResources().getColor(R.color.white));
            emptyText.setTextSize(11);
            emptyText.setPadding(4, 4, 4, 4);
            container.addView(emptyText);
            return;
        }

        for (int i = 0; i < questList.size(); i++) {
            JsonObject quest = questList.get(i).getAsJsonObject();
            if (quest.has("api_no") && quest.has("api_title")) {
                TextView tv = new TextView(this);
                String title = quest.get("api_title").getAsString();
                tv.setText(title);
                tv.setTextColor(getResources().getColor(R.color.white));
                tv.setTextSize(11);
                tv.setSingleLine(true);
                tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tv.setPadding(4, 2, 4, 2);
                container.addView(tv);
            }
        }
    }

    private void refreshFleetData() {
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
        bindQuestTrackData();
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
