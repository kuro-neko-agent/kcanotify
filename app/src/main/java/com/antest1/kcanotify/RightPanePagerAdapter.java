package com.antest1.kcanotify;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class RightPanePagerAdapter extends FragmentStateAdapter {
    public static final int TAB_BATTLE = 0;
    public static final int TAB_QUEST = 1;
    public static final int TAB_EQUIP = 2;
    public static final int TAB_MENU = 3;
    private static final int TAB_COUNT = 4;

    public RightPanePagerAdapter(@NonNull FragmentActivity fa) {
        super(fa);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case TAB_BATTLE: return new BattleFragment();
            case TAB_QUEST:  return new QuestFragment();
            case TAB_EQUIP:  return new EquipFragment();
            case TAB_MENU:   return new MenuFragment();
            default: return new EquipFragment();
        }
    }

    @Override
    public int getItemCount() {
        return TAB_COUNT;
    }
}
