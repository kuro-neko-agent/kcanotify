package com.antest1.kcanotify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.chip.Chip;
import com.google.gson.JsonObject;

import static com.antest1.kcanotify.KcaConstants.KCA_MSG_QUEST_VIEW_LIST;
import static com.antest1.kcanotify.KcaConstants.KCANOTIFY_DB_VERSION;
import static com.antest1.kcanotify.KcaConstants.KCANOTIFY_QTDB_VERSION;
// Note: use KcaUtils.getId() explicitly to avoid clash with Fragment.getId()

/**
 * Quest tab fragment for split-screen mode.
 * Inflates view_quest_list_v2.xml, extracts the inner questviewpanel,
 * and uses QuestDataManager to bind quest data.
 */
public class QuestFragment extends Fragment implements QuestDataManager.QuestPopupCallback {
    private static final int PAGE_SIZE = 5;

    private QuestDataManager questDataManager;
    private KcaDBHelper dbHelper;
    private KcaQuestTracker questTracker;
    private BroadcastReceiver questRefreshReceiver;

    private View questContentView;
    private View questDescPopupView;
    private ListView questListView;
    private ImageView questMenuButton;
    private View noDataText;
    private FrameLayout contentContainer;

    private boolean questViewInitialized = false;
    private boolean isMenuVisible = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_quest, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        noDataText = view.findViewById(R.id.quest_no_data_text);
        contentContainer = view.findViewById(R.id.quest_content_container);

        dbHelper = new KcaDBHelper(requireContext(), null, KCANOTIFY_DB_VERSION);
        questTracker = new KcaQuestTracker(requireContext(), null, KCANOTIFY_QTDB_VERSION);
        questDataManager = new QuestDataManager(requireContext(), dbHelper, questTracker);
    }

    @Override
    public void onResume() {
        super.onResume();
        questRefreshReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshQuestData();
            }
        };
        LocalBroadcastManager.getInstance(requireContext())
                .registerReceiver(questRefreshReceiver,
                        new IntentFilter(KCA_MSG_QUEST_VIEW_LIST));
    }

    @Override
    public void onPause() {
        super.onPause();
        if (questRefreshReceiver != null) {
            LocalBroadcastManager.getInstance(requireContext())
                    .unregisterReceiver(questRefreshReceiver);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        questViewInitialized = false;
        if (dbHelper != null) { dbHelper.close(); dbHelper = null; }
        if (questTracker != null) { questTracker.close(); questTracker = null; }
    }

    private void initQuestView() {
        if (questViewInitialized || contentContainer == null) return;

        Context ctx = new ContextThemeWrapper(requireContext(), R.style.AppTheme);
        View inflated = LayoutInflater.from(ctx).inflate(R.layout.view_quest_list_v2, null);
        questContentView = inflated.findViewById(R.id.questviewpanel);

        if (questContentView != null && questContentView.getParent() != null) {
            ((ViewGroup) questContentView.getParent()).removeView(questContentView);
        }

        if (questContentView != null) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            questContentView.setLayoutParams(lp);
            contentContainer.addView(questContentView);

            questDescPopupView = inflated.findViewById(R.id.quest_desc_popup);
            if (questDescPopupView != null) {
                // Re-parent popup into contentContainer so it overlays the quest list
                if (questDescPopupView.getParent() != null) {
                    ((ViewGroup) questDescPopupView.getParent()).removeView(questDescPopupView);
                }
                contentContainer.addView(questDescPopupView);
                questDescPopupView.setVisibility(View.GONE);
            }
            questListView = questContentView.findViewById(R.id.quest_list);

            KcaQuestListAdpater adapter = questDataManager.getOrCreateAdapter(this);
            questListView.setAdapter(adapter);

            questListView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {
                    TextView pageTitle = questContentView.findViewById(R.id.quest_page);
                    pageTitle.setText(getString(R.string.questview_page).replace("%d/%d", "???"));
                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem,
                                     int visibleItemCount, int totalItemCount) {
                }
            });

            // Menu toggle
            isMenuVisible = false;
            questContentView.findViewById(R.id.quest_amenu).setVisibility(View.GONE);
            questMenuButton = questContentView.findViewById(R.id.quest_amenu_btn);
            questMenuButton.setOnClickListener(v -> {
                if (isMenuVisible) {
                    questContentView.findViewById(R.id.quest_amenu).setVisibility(View.GONE);
                    questMenuButton.setImageResource(R.drawable.ic_arrow_up);
                } else {
                    questContentView.findViewById(R.id.quest_amenu).setVisibility(View.VISIBLE);
                    questMenuButton.setImageResource(R.drawable.ic_arrow_down);
                }
                isMenuVisible = !isMenuVisible;
            });

            // Hide overlay-specific elements
            View questHead = questContentView.findViewById(R.id.quest_head);
            if (questHead != null) questHead.setVisibility(View.GONE);
            View exitBtn = questContentView.findViewById(R.id.quest_exit);
            if (exitBtn != null) exitBtn.setVisibility(View.GONE);

            // Clear button
            questContentView.findViewById(R.id.quest_clear).setOnClickListener(v ->
                    questTracker.clearQuestTrack());

            // Popup close
            questDescPopupView.findViewById(R.id.view_qd_head).setOnClickListener(v ->
                    questDescPopupView.setVisibility(View.GONE));
            ((TextView) questDescPopupView.findViewById(R.id.view_qd_rewards_hd))
                    .setText(getString(R.string.questview_reward));

            setupPagination();
            setupFilterChips();
        }

        questViewInitialized = true;
    }

    private void setupPagination() {
        int[] pageIds = questDataManager.pageIndexList;
        for (int i = 0; i < pageIds.length; i++) {
            TextView pageBtn = questContentView.findViewById(pageIds[i]);
            pageBtn.setText(String.valueOf(i + 1));
            pageBtn.setOnClickListener(v -> {
                int page = Integer.parseInt(((TextView) v).getText().toString());
                int pos = (page - 1) * PAGE_SIZE;
                questDataManager.scrollListView(questListView, pos);
                int totalSize = questDataManager.getAdapter().getCount();
                questDataManager.setTopBottomNavigation(questContentView, page, totalSize);
            });
        }

        questContentView.findViewById(R.id.quest_page_top).setOnClickListener(v -> {
            questDataManager.scrollListView(questListView, 0);
            int totalSize = questDataManager.getAdapter().getCount();
            questDataManager.setTopBottomNavigation(questContentView, 1, totalSize);
        });

        questContentView.findViewById(R.id.quest_page_bottom).setOnClickListener(v -> {
            int totalSize = questDataManager.getAdapter().getCount();
            int totalPage = (totalSize - 1) / PAGE_SIZE + 1;
            int lastIdx = Math.max(totalSize - PAGE_SIZE, 0);
            questDataManager.setTopBottomNavigation(questContentView, totalPage, totalSize);
            questDataManager.scrollListView(questListView, lastIdx);
        });
    }

    private void setupFilterChips() {
        int[] filterCategories = questDataManager.filterCategoryList;
        for (int i = 0; i < 5; i++) {
            final int filterIdx = i;
            View chipView = questContentView.findViewById(
                    KcaUtils.getId(KcaUtils.format("quest_class_%d", i + 1), R.id.class));
            ((TextView) chipView).setText(getString(
                    KcaUtils.getId(KcaUtils.format("quest_class_%d", i + 1), R.string.class)));
            chipView.setBackgroundColor(ContextCompat.getColor(requireContext(),
                    R.color.colorFleetInfoBtn));

            chipView.setOnClickListener(v -> {
                for (int j = 0; j < 5; j++) {
                    ((Chip) questContentView.findViewById(
                            KcaUtils.getId(KcaUtils.format("quest_class_%d", j + 1), R.id.class)))
                            .setChipBackgroundColor(ColorStateList.valueOf(Color.TRANSPARENT));
                }
                int currentFilter = questDataManager.getCurrentFilterState();
                if (currentFilter != filterIdx) {
                    ((Chip) v).setChipBackgroundColor(ColorStateList.valueOf(
                            ContextCompat.getColor(requireContext(),
                                    KcaUtils.getId(KcaUtils.format("colorQuestCategory%d",
                                            filterCategories[filterIdx]), R.color.class))));
                    questDataManager.setQuestView(questListView,
                            questDataManager.getCurrentQuestList(), filterCategories[filterIdx]);
                    questDataManager.setCurrentFilterState(filterIdx);
                } else {
                    questDataManager.setQuestView(questListView,
                            questDataManager.getCurrentQuestList(), -1);
                    questDataManager.setCurrentFilterState(-1);
                }
            });
        }
    }

    public void refreshQuestData() {
        if (getView() == null) return;

        if (!questViewInitialized) {
            initQuestView();
        }

        if (questContentView != null && questListView != null) {
            noDataText.setVisibility(View.GONE);
            contentContainer.setVisibility(View.VISIBLE);
            try {
                boolean isQuestMode = KcaQuestViewService.getQuestMode();
                int filterState = questDataManager.getCurrentFilterState();
                int result = questDataManager.loadAndSetView(
                        questListView, isQuestMode, true, 0, filterState);
                if (result == 0) {
                    if (questDescPopupView != null) questDescPopupView.setVisibility(View.GONE);
                    int totalSize = questDataManager.getAdapter() != null
                            ? questDataManager.getAdapter().getCount() : 0;
                    questDataManager.setTopBottomNavigation(questContentView, 1, totalSize);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void setAndShowPopup(JsonObject data) {
        if (questDescPopupView != null) {
            questDataManager.bindPopupData(questDescPopupView, data);
        }
    }
}
