package com.antest1.kcanotify;

import android.content.Context;
import android.widget.ListView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import static com.antest1.kcanotify.KcaUtils.getId;

/**
 * Shared quest data binding logic used by QuestFragment (split-screen).
 * Reads from KcaQuestViewService static fields and DB.
 */
public class QuestDataManager {
    private static final int PAGE_SIZE = 5;

    private final Context context;
    private final KcaDBHelper dbHelper;
    private final KcaQuestTracker questTracker;
    private KcaQuestListAdpater adapter;
    private JsonArray currentQuestList = new JsonArray();
    private int currentFilterState = -1;

    final int[] pageIndexList = {R.id.quest_page_1, R.id.quest_page_2,
            R.id.quest_page_3, R.id.quest_page_4, R.id.quest_page_5};
    final int[] filterCategoryList = {2, 3, 4, 6, 7};

    /**
     * Callback interface for showing quest description popup.
     * Implemented by QuestFragment to display popup within the fragment view.
     */
    public interface QuestPopupCallback {
        void setAndShowPopup(JsonObject data);
    }

    public QuestDataManager(Context context, KcaDBHelper dbHelper, KcaQuestTracker questTracker) {
        this.context = context;
        this.dbHelper = dbHelper;
        this.questTracker = questTracker;
    }

    public KcaQuestListAdpater getOrCreateAdapter(QuestPopupCallback popupCallback) {
        if (adapter == null) {
            adapter = new KcaQuestListAdpater(context, questTracker, popupCallback);
        }
        return adapter;
    }

    public KcaQuestListAdpater getAdapter() {
        return adapter;
    }

    public int getCurrentFilterState() {
        return currentFilterState;
    }

    public void setCurrentFilterState(int state) {
        currentFilterState = state;
    }

    public JsonArray getCurrentQuestList() {
        return currentQuestList;
    }

    /**
     * Load quest data from API or DB, apply filter, and populate adapter.
     * Returns 0 on success, 1 on error.
     */
    public int loadAndSetView(ListView questListView, boolean isQuestMode, boolean checkValid,
                              int tabId, int filterId) {
        try {
            if (isQuestMode && KcaQuestViewService.api_data != null) {
                JsonObject apiData = KcaQuestViewService.api_data;
                if (apiData.has("api_list")) {
                    if (apiData.get("api_list").isJsonArray()) {
                        currentQuestList = apiData.getAsJsonArray("api_list");
                    } else {
                        currentQuestList = new JsonArray();
                    }
                }
            } else {
                currentQuestList = dbHelper.getCurrentQuestList();
            }

            if (checkValid) {
                questTracker.clearInvalidQuestTrack();
                dbHelper.checkValidQuest(currentQuestList, tabId);
            }

            int filter = -1;
            if (filterId > -1) filter = filterCategoryList[filterId];
            setQuestView(questListView, currentQuestList, filter);
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 1;
        }
    }

    /**
     * Populate the adapter and set it on the ListView.
     */
    public void setQuestView(ListView questListView, JsonArray questList, int filter) {
        if (adapter == null) return;
        adapter.setListViewItemList(questList, filter);
        adapter.notifyDataSetChanged();
        questListView.setAdapter(adapter);
        scrollListView(questListView, 0);
    }

    public void scrollListView(ListView listView, int pos) {
        listView.smoothScrollBy(0, 0);
        listView.smoothScrollToPosition(pos);
        listView.setSelection(pos);
    }

    /**
     * Update pagination navigation display.
     */
    public void setTopBottomNavigation(android.view.View questView, int centerPage, int totalItemSize) {
        int totalPage = (totalItemSize - 1) / PAGE_SIZE + 1;
        int startPage = centerPage - 2;

        if (totalPage <= 5) startPage = 1;
        else if (centerPage <= 3) startPage = 1;
        else if (centerPage > totalPage - 4) startPage = totalPage - 4;

        TextView pageTitle = questView.findViewById(R.id.quest_page);
        pageTitle.setText(KcaUtils.format(
                context.getString(R.string.questview_page), centerPage, totalPage));

        for (int i = 0; i < 5; i++) {
            questView.findViewById(pageIndexList[i])
                    .setVisibility(totalPage > i ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
            ((TextView) questView.findViewById(pageIndexList[i]))
                    .setText(String.valueOf(startPage + i));
            if (startPage + i == centerPage) {
                ((TextView) questView.findViewById(pageIndexList[i])).setTextColor(
                        ContextCompat.getColor(context, R.color.colorAccent));
            } else {
                ((TextView) questView.findViewById(pageIndexList[i])).setTextColor(
                        ContextCompat.getColor(context, R.color.white));
            }
        }
    }

    /**
     * Bind quest description popup data to views.
     */
    public void bindPopupData(android.view.View popupView, JsonObject data) {
        ((TextView) popupView.findViewById(R.id.view_qd_title))
                .setText(data.get("title").getAsString());
        ((TextView) popupView.findViewById(R.id.view_qd_text))
                .setText(data.get("detail").getAsString());

        String memo = data.get("memo").getAsString();
        TextView memoView = popupView.findViewById(R.id.view_qd_memo);
        if (!memo.isEmpty()) {
            memoView.setText(memo);
            memoView.setVisibility(android.view.View.VISIBLE);
        } else {
            memoView.setVisibility(android.view.View.GONE);
        }

        String rewards = data.get("rewards").getAsString();
        if (!rewards.isEmpty()) {
            ((TextView) popupView.findViewById(R.id.view_qd_rewards)).setText(rewards);
            popupView.findViewById(R.id.view_qd_rewards_layout).setVisibility(android.view.View.VISIBLE);
        } else {
            popupView.findViewById(R.id.view_qd_rewards_layout).setVisibility(android.view.View.GONE);
        }

        JsonArray materials = data.getAsJsonArray("materials");
        for (int i = 0; i < materials.size(); i++) {
            int value = materials.get(i).getAsInt();
            String viewName = "view_qd_materials_" + (i + 1);
            ((TextView) popupView.findViewById(getId(viewName, R.id.class)))
                    .setText(String.valueOf(value));
        }

        popupView.setVisibility(android.view.View.VISIBLE);
    }
}
