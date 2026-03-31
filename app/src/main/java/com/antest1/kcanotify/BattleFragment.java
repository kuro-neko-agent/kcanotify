package com.antest1.kcanotify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import static com.antest1.kcanotify.KcaConstants.KCA_MSG_BATTLE_VIEW_REFRESH;

/**
 * Battle tab fragment for split-screen mode.
 * Inflates view_sortie_battle.xml, extracts the inner battleviewpanel,
 * and uses BattleDataManager to bind battle data.
 */
public class BattleFragment extends Fragment {
    private BattleDataManager battleDataManager;
    private BroadcastReceiver battleRefreshReceiver;
    private View battleContentView; // The extracted battleviewpanel
    private View noDataText;
    private FrameLayout contentContainer;
    private boolean battleViewInitialized = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_battle, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        noDataText = view.findViewById(R.id.battle_no_data_text);
        contentContainer = view.findViewById(R.id.battle_content_container);
        battleDataManager = new BattleDataManager(requireContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        battleRefreshReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshBattleData();
            }
        };
        LocalBroadcastManager.getInstance(requireContext())
                .registerReceiver(battleRefreshReceiver,
                        new IntentFilter(KCA_MSG_BATTLE_VIEW_REFRESH));
    }

    @Override
    public void onPause() {
        super.onPause();
        if (battleRefreshReceiver != null) {
            LocalBroadcastManager.getInstance(requireContext())
                    .unregisterReceiver(battleRefreshReceiver);
        }
    }

    /**
     * Initialize the battle view by inflating view_sortie_battle.xml
     * and extracting the battleviewpanel (same pattern as FleetPanelActivity).
     */
    private void initBattleView() {
        if (battleViewInitialized || contentContainer == null) return;

        Context ctx = new ContextThemeWrapper(requireContext(), R.style.AppTheme);
        View inflated = LayoutInflater.from(ctx).inflate(R.layout.view_sortie_battle, null);
        battleContentView = inflated.findViewById(R.id.battleviewpanel);

        if (battleContentView != null && battleContentView.getParent() != null) {
            ((ViewGroup) battleContentView.getParent()).removeView(battleContentView);
        }

        if (battleContentView != null) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            battleContentView.setLayoutParams(lp);
            contentContainer.addView(battleContentView);

        }

        battleViewInitialized = true;
    }

    /**
     * Refresh battle data display. Called on broadcast or explicit request.
     */
    public void refreshBattleData() {
        if (KcaBattleViewService.api_data == null || getView() == null) return;

        // Lazy-init the battle view
        if (!battleViewInitialized) {
            initBattleView();
        }

        if (battleContentView != null) {
            noDataText.setVisibility(View.GONE);
            contentContainer.setVisibility(View.VISIBLE);
            try {
                battleDataManager.bindBattleView(battleContentView);
            } catch (Exception e) {
                // Binding can fail if data is incomplete or views missing
                e.printStackTrace();
            }
        }
    }
}
