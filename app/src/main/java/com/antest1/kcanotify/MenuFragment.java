package com.antest1.kcanotify;

import android.content.Intent;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

import static com.antest1.kcanotify.KcaUtils.getId;

/**
 * Menu tab fragment. Provides buttons to launch overlay popup services.
 */
public class MenuFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Force dark theme so button text is readable on dark background
        LayoutInflater darkInflater = inflater.cloneInContext(
                new ContextThemeWrapper(requireContext(), R.style.AppThemeDark));
        return darkInflater.inflate(R.layout.fragment_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        LinearLayout btnContainer = view.findViewById(R.id.menu_button_container);
        setupMenuButtons(btnContainer);
    }

    private void setupMenuButtons(LinearLayout container) {
        addMenuButton(container, getString(R.string.viewmenu_quest), v ->
                startPopupService(KcaQuestViewService.class,
                        KcaQuestViewService.SHOW_QUESTVIEW_ACTION_NEW));
        addMenuButton(container, getString(R.string.excheckview_title), v ->
                startPopupService(KcaExpeditionCheckViewService.class,
                        KcaExpeditionCheckViewService.SHOW_EXCHECKVIEW_ACTION + "/0"));
        addMenuButton(container, getString(R.string.viewmenu_develop), v ->
                startPopupService(KcaDevelopPopupService.class, null));
        addMenuButton(container, getString(R.string.viewmenu_construction), v ->
                startPopupService(KcaConstructPopupService.class,
                        KcaConstructPopupService.CONSTR_DATA_ACTION));
        addMenuButton(container, getString(R.string.viewmenu_docking), v ->
                startPopupService(KcaDockingPopupService.class,
                        KcaDockingPopupService.DOCKING_DATA_ACTION));
        addMenuButton(container, getString(R.string.viewmenu_maphp), v ->
                startPopupService(KcaMapHpPopupService.class,
                        KcaMapHpPopupService.MAPHP_SHOW_ACTION));
        addMenuButton(container, getString(R.string.viewmenu_fchk), v ->
                startPopupService(KcaFleetCheckPopupService.class,
                        KcaFleetCheckPopupService.FCHK_SHOW_ACTION));
        addMenuButton(container, getString(R.string.viewmenu_airbase_title), v ->
                startPopupService(KcaLandAirBasePopupService.class,
                        KcaLandAirBasePopupService.LAB_DATA_ACTION));
        addMenuButton(container, getString(R.string.viewmenu_akashi), v ->
                startPopupService(KcaAkashiViewService.class,
                        KcaAkashiViewService.SHOW_AKASHIVIEW_ACTION));
    }

    private void addMenuButton(LinearLayout container, String label, View.OnClickListener listener) {
        MaterialButton btn = new MaterialButton(
                new ContextThemeWrapper(requireContext(), R.style.AppThemeDark),
                null, R.attr.materialButtonOutlinedStyle);
        btn.setText(label);
        btn.setTextColor(0xFFFFFFFF);
        btn.setStrokeColorResource(R.color.white);
        btn.setTextSize(12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 4;
        btn.setLayoutParams(lp);
        btn.setOnClickListener(listener);
        container.addView(btn);
    }

    private void startPopupService(Class<?> target, String action) {
        if (getContext() == null) return;
        boolean checkGameData = false;
        switch (target.getSimpleName()) {
            case "KcaConstructPopupService":
            case "KcaDevelopPopupService":
            case "KcaDockingPopupService":
                checkGameData = true;
                break;
        }
        if (checkGameData && !KcaApiData.isGameDataLoaded()) return;
        Intent popupIntent = new Intent(getContext(), target);
        if (action != null) popupIntent.setAction(action);
        requireContext().startService(popupIntent);
    }
}
