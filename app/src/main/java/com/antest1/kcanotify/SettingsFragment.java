package com.antest1.kcanotify;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import static com.antest1.kcanotify.NestedPreferenceFragment.NESTED_TAG;

/**
 * Settings tab fragment for split-screen mode.
 * Embeds MainPreferenceFragment directly in the right pane.
 * Forces dark theme so text is readable on the dark overlay background.
 */
public class SettingsFragment extends Fragment implements MainPreferenceFragment.Callback {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState == null) {
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.settings_container, new DarkPreferenceFragment())
                    .commit();
        }
    }

    @Override
    public void onNestedPreferenceSelected(int key) {
        Log.e("KCA", "SettingsFragment onNestedPreferenceSelected: " + key);
        getChildFragmentManager().beginTransaction()
                .replace(R.id.settings_container,
                        DarkNestedPreferenceFragment.newInstance(key), NESTED_TAG)
                .addToBackStack(NESTED_TAG)
                .commit();
    }

    /**
     * Wrapper that forces dark theme on MainPreferenceFragment.
     * Overriding getContext() ensures PreferenceFragmentCompat uses the dark theme
     * for its PreferenceManager and all inflated preference item views,
     * including recycled views in the RecyclerView.
     */
    public static class DarkPreferenceFragment extends MainPreferenceFragment {
        private ContextThemeWrapper darkContext;

        @Override
        public Context getContext() {
            Context base = super.getContext();
            if (base == null) return null;
            if (darkContext == null) {
                darkContext = new ContextThemeWrapper(base, R.style.AppThemeDark);
            }
            return darkContext;
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            View listView = getListView();
            if (listView != null) {
                listView.setBackgroundColor(0xFF1A1A2E);
            }
        }
    }

    /**
     * Dark-themed variant of NestedPreferenceFragment for use in split-screen settings.
     */
    public static class DarkNestedPreferenceFragment extends NestedPreferenceFragment {
        private ContextThemeWrapper darkContext;

        public static DarkNestedPreferenceFragment newInstance(int key) {
            DarkNestedPreferenceFragment fragment = new DarkNestedPreferenceFragment();
            Bundle args = new Bundle();
            args.putInt(NESTED_TAG, key);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public Context getContext() {
            Context base = super.getContext();
            if (base == null) return null;
            if (darkContext == null) {
                darkContext = new ContextThemeWrapper(base, R.style.AppThemeDark);
            }
            return darkContext;
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            View listView = getListView();
            if (listView != null) {
                listView.setBackgroundColor(0xFF1A1A2E);
            }
        }
    }
}
