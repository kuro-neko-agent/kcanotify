package com.antest1.kcanotify;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import static com.antest1.kcanotify.KcaApiData.checkUserPortEnough;
import static com.antest1.kcanotify.KcaApiData.getAirForceResultString;
import static com.antest1.kcanotify.KcaApiData.getCurrentNodeAlphabet;
import static com.antest1.kcanotify.KcaApiData.getCurrentNodeSubExist;
import static com.antest1.kcanotify.KcaApiData.getEngagementString;
import static com.antest1.kcanotify.KcaApiData.getFormationString;
import static com.antest1.kcanotify.KcaApiData.getItemString;
import static com.antest1.kcanotify.KcaApiData.getKcShipDataById;
import static com.antest1.kcanotify.KcaApiData.getNodeColor;
import static com.antest1.kcanotify.KcaApiData.getNodeFullInfo;
import static com.antest1.kcanotify.KcaApiData.getShipTranslation;
import static com.antest1.kcanotify.KcaConstants.*;
import static com.antest1.kcanotify.KcaUtils.getId;

/**
 * Battle data binding for Fragment use.
 * Reads static data from KcaBattleViewService and KcaBattle,
 * binds to the provided view hierarchy (from view_sortie_battle.xml).
 * KcaBattleViewService itself is NOT modified.
 */
public class BattleDataManager {
    private final Context context;

    public BattleDataManager(Context context) {
        this.context = context;
    }

    private static String makeHpString(int currenthp, int maxhp) {
        return KcaUtils.format("HP %d/%d", currenthp, maxhp);
    }

    private static String makeHpString(int currenthp, int maxhp, boolean damecon_flag) {
        String data = KcaUtils.format(" %d/%d", currenthp, maxhp);
        if (damecon_flag) return data;
        else return "HP".concat(data);
    }

    private static String makeLvString(int level) {
        return KcaUtils.format("Lv %d", level);
    }

    private Drawable getProgressDrawable(float value) {
        if (value > 75) {
            return ContextCompat.getDrawable(context, R.drawable.progress_bar_normal);
        } else if (value > 50) {
            return ContextCompat.getDrawable(context, R.drawable.progress_bar_lightdmg);
        } else if (value > 25) {
            return ContextCompat.getDrawable(context, R.drawable.progress_bar_moderatedmg);
        } else {
            return ContextCompat.getDrawable(context, R.drawable.progress_bar_heavydmg);
        }
    }

    private boolean checkItemPairExist(JsonArray data, int key1, int key2) {
        if (data == null) return false;
        String key = KcaUtils.format("%d_%d", key1, key2);
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).getAsString().equals(key)) return true;
        }
        return false;
    }

    private boolean checkStart(String url) {
        if (url.equals(API_REQ_SORTIE_BATTLE_MIDNIGHT)) return false;
        if (url.equals(API_REQ_PRACTICE_MIDNIGHT_BATTLE)) return false;
        if (url.equals(API_REQ_COMBINED_BATTLE_MIDNIGHT)) return false;
        if (url.equals(API_REQ_COMBINED_BATTLE_MIDNIGHT_SP)) return false;
        if (url.equals(API_REQ_COMBINED_BATTLE_MIDNIGHT_EC)) return false;
        return true;
    }

    /**
     * Set the fleet layout visibility based on combined fleet flags.
     */
    public void setViewLayout(View battleview, boolean fc_flag, boolean ec_flag) {
        LinearLayout friend_main_fleet = battleview.findViewById(R.id.friend_main_fleet);
        LinearLayout friend_combined_fleet = battleview.findViewById(R.id.friend_combined_fleet);
        LinearLayout enemy_main_fleet = battleview.findViewById(R.id.enemy_main_fleet);
        LinearLayout enemy_combined_fleet = battleview.findViewById(R.id.enemy_combined_fleet);

        friend_combined_fleet.setVisibility(fc_flag ? View.VISIBLE : View.GONE);
        enemy_combined_fleet.setVisibility(ec_flag ? View.VISIBLE : View.GONE);

        if (fc_flag && ec_flag) {
            friend_main_fleet.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.25f));
            enemy_main_fleet.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.25f));
            friend_combined_fleet.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.25f));
            enemy_combined_fleet.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.25f));
        } else if (fc_flag) {
            enemy_combined_fleet.setVisibility(View.GONE);
            friend_main_fleet.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.325f));
            enemy_main_fleet.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.35f));
            friend_combined_fleet.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.325f));
        } else if (ec_flag) {
            friend_combined_fleet.setVisibility(View.GONE);
            friend_main_fleet.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.35f));
            enemy_main_fleet.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.325f));
            enemy_combined_fleet.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.325f));
        } else {
            friend_combined_fleet.setVisibility(View.GONE);
            enemy_combined_fleet.setVisibility(View.GONE);
            friend_main_fleet.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.5f));
            enemy_main_fleet.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.5f));
        }
    }

    /**
     * Bind battle data to the view hierarchy. Reads from KcaBattleViewService static fields.
     * This is a simplified version of KcaBattleViewService.setBattleView() - handles core display
     * elements (node info, formations, HP bars, rank). Edge cases delegated to overlay service.
     */
    public void bindBattleView(View battleview) {
        JsonObject api_data = KcaBattleViewService.api_data;
        if (api_data == null) return;

        int textsize_n_large = context.getResources().getDimensionPixelSize(R.dimen.battleview_text_n_large);
        int textsize_n_medium = context.getResources().getDimensionPixelSize(R.dimen.battleview_text_n_medium);
        int textsize_n_small = context.getResources().getDimensionPixelSize(R.dimen.battleview_text_n_small);
        int textsize_c_large = context.getResources().getDimensionPixelSize(R.dimen.battleview_text_c_large);
        int textsize_c_medium = context.getResources().getDimensionPixelSize(R.dimen.battleview_text_c_medium);
        int textsize_c_xsmall = context.getResources().getDimensionPixelSize(R.dimen.battleview_text_c_xsmall);

        boolean is_practice = api_data.has("api_practice_flag");
        boolean fc_flag = false;
        boolean ec_flag = false;

        // Node info (start/next)
        if (api_data.has("api_maparea_id")) {
            int api_maparea_id = api_data.get("api_maparea_id").getAsInt();
            int api_mapinfo_no = api_data.get("api_mapinfo_no").getAsInt();
            int api_no = api_data.get("api_no").getAsInt();
            String current_node = getCurrentNodeAlphabet(api_maparea_id, api_mapinfo_no, api_no);
            boolean sub_exist = getCurrentNodeSubExist(api_maparea_id, api_mapinfo_no, api_no);
            int api_event_id = api_data.get("api_event_id").getAsInt();
            int api_event_kind = api_data.get("api_event_kind").getAsInt();
            int api_color_no = api_data.get("api_color_no").getAsInt();

            String nodeInfo = getNodeFullInfo(context, current_node, api_event_id, api_event_kind, api_color_no, true);
            nodeInfo = nodeInfo.replaceAll("[()]", "");

            fc_flag = KcaBattle.isCombinedFleetInSortie();
            ec_flag = api_event_id != API_NODE_EVENT_ID_NOEVENT &&
                    (api_event_kind == API_NODE_EVENT_KIND_ECBATTLE || api_event_kind == API_NODE_EVENT_KIND_NIGHTDAYBATTLE_EC);
            setViewLayout(battleview, fc_flag, false);

            ((TextView) battleview.findViewById(R.id.battle_node)).setText(nodeInfo);
            ((TextView) battleview.findViewById(R.id.battle_result)).setText("");
            ((TextView) battleview.findViewById(R.id.enemy_fleet_name)).setText("");
            ((TextView) battleview.findViewById(R.id.enemy_combined_fleet_name)).setText("");

            battleview.findViewById(R.id.battle_node)
                    .setBackgroundColor(getNodeColor(context, api_event_id, api_event_kind, api_color_no));
            if (is_practice) battleview.findViewById(R.id.battle_node_ss).setVisibility(View.GONE);
            else battleview.findViewById(R.id.battle_node_ss).setVisibility(sub_exist ? View.VISIBLE : View.GONE);
        }

        // Friend fleet data (deck port)
        if (api_data.has("api_deck_port")) {
            boolean midnight_flag = api_data.get("api_url").getAsString().contains("midnight");
            if (is_practice && !midnight_flag) {
                ((TextView) battleview.findViewById(R.id.battle_node)).setText(context.getString(R.string.node_info_practice));
            }

            JsonObject deckportdata = api_data.getAsJsonObject("api_deck_port");
            if (deckportdata != null) {
                JsonArray deckData = deckportdata.getAsJsonArray("api_deck_data");
                JsonArray portData = deckportdata.getAsJsonArray("api_ship_data");

                for (int i = 0; i < 6; i++) {
                    battleview.findViewById(getId(KcaUtils.format("fm_%d", i + 1), R.id.class)).setVisibility(View.INVISIBLE);
                    battleview.findViewById(getId(KcaUtils.format("fs_%d", i + 1), R.id.class)).setVisibility(View.INVISIBLE);
                    battleview.findViewById(getId(KcaUtils.format("em_%d", i + 1), R.id.class)).setVisibility(View.INVISIBLE);
                    battleview.findViewById(getId(KcaUtils.format("es_%d", i + 1), R.id.class)).setVisibility(View.INVISIBLE);
                }
                battleview.findViewById(R.id.fm_7).setVisibility(View.INVISIBLE);

                for (int i = 0; i < deckData.size(); i++) {
                    JsonObject deckObj = deckData.get(i).getAsJsonObject();
                    JsonArray deck = deckObj.getAsJsonArray("api_ship");
                    JsonObject shipMap = new JsonObject();
                    for (int j = 0; j < portData.size(); j++) {
                        JsonObject d = portData.get(j).getAsJsonObject();
                        shipMap.add(String.valueOf(d.get("api_id").getAsInt()), d);
                    }

                    if (i == 0) {
                        ((TextView) battleview.findViewById(R.id.friend_fleet_name))
                                .setText(deckObj.get("api_name").getAsString());
                        bindFriendFleet(battleview, deck, shipMap, "fm", fc_flag || ec_flag,
                                textsize_n_large, textsize_n_medium, textsize_c_large, textsize_c_medium, textsize_c_xsmall);
                        if (deck.size() <= 6) battleview.findViewById(R.id.fm_7).setVisibility(View.GONE);
                        else if (deck.size() == 7 && deck.get(6).getAsInt() == -1) battleview.findViewById(R.id.fm_7).setVisibility(View.GONE);
                    } else if (i == 1) {
                        ((TextView) battleview.findViewById(R.id.friend_combined_fleet_name))
                                .setText(deckObj.get("api_name").getAsString());
                        bindFriendFleet(battleview, deck, shipMap, "fs", true,
                                textsize_n_large, textsize_n_medium, textsize_c_large, textsize_c_medium, textsize_c_xsmall);
                    }
                }
            }
        }

        // Enemy fleet data
        if (api_data.has("api_ship_ke")) {
            setViewLayout(battleview, fc_flag, ec_flag);
            boolean start_flag = checkStart(api_data.get("api_url").getAsString());

            if (start_flag) {
                JsonArray api_formation = api_data.getAsJsonArray("api_formation");
                ((TextView) battleview.findViewById(R.id.friend_fleet_formation))
                        .setText(getFormationString(context, api_formation.get(0).getAsInt(), fc_flag));
                ((TextView) battleview.findViewById(R.id.enemy_fleet_formation))
                        .setText(getFormationString(context, api_formation.get(1).getAsInt(), fc_flag));
                ((TextView) battleview.findViewById(R.id.battle_engagement))
                        .setText(getEngagementString(context, api_formation.get(2).getAsInt()));

                if (api_data.has("api_kouku") && !api_data.get("api_kouku").isJsonNull()) {
                    JsonObject api_kouku = api_data.getAsJsonObject("api_kouku");
                    if (api_kouku.has("api_stage1") && !api_kouku.get("api_stage1").isJsonNull()) {
                        int api_disp_seiku = api_kouku.getAsJsonObject("api_stage1").get("api_disp_seiku").getAsInt();
                        ((TextView) battleview.findViewById(R.id.battle_airpower))
                                .setText(getAirForceResultString(context, api_disp_seiku));
                    }
                }

                if (!KcaBattle.currentEnemyDeckName.isEmpty()) {
                    ((TextView) battleview.findViewById(R.id.enemy_fleet_name))
                            .setText(KcaBattle.currentEnemyDeckName);
                } else {
                    ((TextView) battleview.findViewById(R.id.enemy_fleet_name))
                            .setText(context.getString(R.string.enemy_fleet_name));
                }
            }

            // Enemy ships
            JsonArray api_ship_ke = api_data.getAsJsonArray("api_ship_ke");
            JsonArray api_ship_lv = api_data.getAsJsonArray("api_ship_lv");
            for (int i = 0; i < api_ship_ke.size(); i++) {
                if (api_ship_ke.get(i).getAsInt() == -1) {
                    battleview.findViewById(getId(KcaUtils.format("em_%d", i + 1), R.id.class)).setVisibility(View.INVISIBLE);
                } else {
                    int level = api_ship_lv.get(i).getAsInt();
                    int ship_ke_id = api_ship_ke.get(i).getAsInt();
                    JsonObject kcShipData = getKcShipDataById(ship_ke_id, "name,yomi");
                    if (kcShipData != null) {
                        String kcname = getShipTranslation(kcShipData.get("name").getAsString(), ship_ke_id, true);
                        ((TextView) battleview.findViewById(getId(KcaUtils.format("em_%d_name", i + 1), R.id.class))).setText(kcname);
                        ((TextView) battleview.findViewById(getId(KcaUtils.format("em_%d_lv", i + 1), R.id.class))).setText(makeLvString(level));
                    }
                    battleview.findViewById(getId(KcaUtils.format("em_%d", i + 1), R.id.class)).setVisibility(View.VISIBLE);
                }
            }

            // HP bars - friend
            if (api_data.has("api_f_afterhps")) {
                JsonArray api_f_maxhps = api_data.getAsJsonArray("api_f_maxhps");
                JsonArray api_f_afterhps = api_data.getAsJsonArray("api_f_afterhps");
                JsonArray api_dc_used = api_data.has("api_dc_used") ? api_data.getAsJsonArray("api_dc_used") : null;
                for (int i = 0; i < api_f_maxhps.size(); i++) {
                    int maxhp = api_f_maxhps.get(i).getAsInt();
                    if (maxhp == -1) continue;
                    int afterhp = api_f_afterhps.get(i).getAsInt();
                    float hpPercent = afterhp * VIEW_HP_MAX / (float) maxhp;
                    boolean damecon_flag = checkItemPairExist(api_dc_used, 0, i);
                    View dcflag = battleview.findViewById(getId(KcaUtils.format("fm_%d_dcflag", i + 1), R.id.class));
                    if (dcflag != null) dcflag.setVisibility(damecon_flag ? View.VISIBLE : View.GONE);
                    TextView hpTxt = battleview.findViewById(getId(KcaUtils.format("fm_%d_hp_txt", i + 1), R.id.class));
                    if (hpTxt != null && !hpTxt.getText().toString().contains(context.getString(R.string.battleview_text_retreated))) {
                        hpTxt.setText(makeHpString(afterhp, maxhp, damecon_flag));
                    }
                    ProgressBar hpBar = battleview.findViewById(getId(KcaUtils.format("fm_%d_hp_bar", i + 1), R.id.class));
                    if (hpBar != null) {
                        hpBar.setProgress(Math.round(hpPercent));
                        hpBar.setProgressDrawable(getProgressDrawable(hpPercent));
                    }
                }
            }

            // HP bars - enemy
            if (api_data.has("api_e_afterhps")) {
                JsonArray api_e_maxhps = api_data.getAsJsonArray("api_e_maxhps");
                JsonArray api_e_afterhps = api_data.getAsJsonArray("api_e_afterhps");
                for (int i = 0; i < api_e_maxhps.size(); i++) {
                    String hp_str = api_e_maxhps.get(i).getAsString();
                    float hpPercent = 0;
                    TextView hpTxt = battleview.findViewById(getId(KcaUtils.format("em_%d_hp_txt", i + 1), R.id.class));
                    if (hp_str.contains("N")) {
                        if (hpTxt != null) hpTxt.setText("N/A");
                    } else {
                        int maxhp = api_e_maxhps.get(i).getAsInt();
                        int afterhp = api_e_afterhps.get(i).getAsInt();
                        if (maxhp == -1) continue;
                        hpPercent = afterhp * VIEW_HP_MAX / (float) maxhp;
                        if (hpTxt != null) hpTxt.setText(makeHpString(afterhp, maxhp));
                    }
                    ProgressBar hpBar = battleview.findViewById(getId(KcaUtils.format("em_%d_hp_bar", i + 1), R.id.class));
                    if (hpBar != null) {
                        hpBar.setProgress(Math.round(hpPercent));
                        hpBar.setProgressDrawable(getProgressDrawable(hpPercent));
                    }
                }
            }

            // Combined fleet HP bars
            if (api_data.has("api_f_maxhps_combined")) {
                JsonArray api_f_maxhps_combined = api_data.getAsJsonArray("api_f_maxhps_combined");
                JsonArray api_f_afterhps_combined = api_data.getAsJsonArray("api_f_afterhps_combined");
                JsonArray api_dc_used = api_data.has("api_dc_used") ? api_data.getAsJsonArray("api_dc_used") : null;
                for (int i = 0; i < api_f_maxhps_combined.size(); i++) {
                    int maxhp = api_f_maxhps_combined.get(i).getAsInt();
                    if (maxhp == -1) continue;
                    int afterhp = api_f_afterhps_combined.get(i).getAsInt();
                    float hpPercent = afterhp * VIEW_HP_MAX / (float) maxhp;
                    boolean damecon_flag = checkItemPairExist(api_dc_used, 1, i);
                    View dcflag = battleview.findViewById(getId(KcaUtils.format("fs_%d_dcflag", i + 1), R.id.class));
                    if (dcflag != null) dcflag.setVisibility(damecon_flag ? View.VISIBLE : View.GONE);
                    TextView hpTxt = battleview.findViewById(getId(KcaUtils.format("fs_%d_hp_txt", i + 1), R.id.class));
                    if (hpTxt != null && !hpTxt.getText().toString().contains(context.getString(R.string.battleview_text_retreated))) {
                        hpTxt.setText(makeHpString(afterhp, maxhp, damecon_flag));
                    }
                    ProgressBar hpBar = battleview.findViewById(getId(KcaUtils.format("fs_%d_hp_bar", i + 1), R.id.class));
                    if (hpBar != null) {
                        hpBar.setProgress(Math.round(hpPercent));
                        hpBar.setProgressDrawable(getProgressDrawable(hpPercent));
                    }
                }
            }

            // Battle rank
            if (start_flag) {
                JsonArray api_f_nowhps = api_data.has("api_f_nowhps") ? api_data.getAsJsonArray("api_f_nowhps") : new JsonArray();
                JsonArray api_e_nowhps = api_data.has("api_e_nowhps") ? api_data.getAsJsonArray("api_e_nowhps") : new JsonArray();
                JsonArray api_f_afterhps = api_data.has("api_f_afterhps") ? api_data.getAsJsonArray("api_f_afterhps") : new JsonArray();
                JsonArray api_e_afterhps = api_data.has("api_e_afterhps") ? api_data.getAsJsonArray("api_e_afterhps") : new JsonArray();
                JsonArray api_f_nowhps_combined = api_data.has("api_f_nowhps_combined") ? api_data.getAsJsonArray("api_f_nowhps_combined") : new JsonArray();
                JsonArray api_e_nowhps_combined = api_data.has("api_e_nowhps_combined") ? api_data.getAsJsonArray("api_e_nowhps_combined") : new JsonArray();
                JsonArray api_f_afterhps_combined = api_data.has("api_f_afterhps_combined") ? api_data.getAsJsonArray("api_f_afterhps_combined") : new JsonArray();
                JsonArray api_e_afterhps_combined = api_data.has("api_e_afterhps_combined") ? api_data.getAsJsonArray("api_e_afterhps_combined") : new JsonArray();

                JsonObject fleetcheckdata = new JsonObject();
                fleetcheckdata.add("f_after", api_f_afterhps);
                fleetcheckdata.add("e_after", api_e_afterhps);
                fleetcheckdata.add("f_after_cb", api_f_afterhps_combined);
                fleetcheckdata.add("e_after_cb", api_e_afterhps_combined);
                fleetcheckdata.add("f_start", api_f_nowhps);
                fleetcheckdata.add("e_start", api_e_nowhps);
                fleetcheckdata.add("f_start_cb", api_f_nowhps_combined);
                fleetcheckdata.add("e_start_cb", api_e_nowhps_combined);

                String api_url = api_data.get("api_url").getAsString();
                JsonObject rankData;
                if (api_url.equals(API_REQ_SORTIE_LDAIRBATTLE) || api_url.equals(API_REQ_COMBINED_LDAIRBATTLE) || api_url.equals(API_REQ_SORTIE_LDSHOOTING)) {
                    rankData = KcaBattle.calculateLdaRank(fleetcheckdata);
                } else {
                    rankData = KcaBattle.calculateRank(fleetcheckdata);
                }

                bindRankData(battleview, rankData);
            }

            // Battle result (MVP, drop ship)
            if (api_data.has("api_win_rank")) {
                int mvp_idx = api_data.get("api_mvp").getAsInt();
                if (mvp_idx != -1) {
                    TextView mvpView = battleview.findViewById(getId(KcaUtils.format("fm_%d_name", mvp_idx), R.id.class));
                    if (mvpView != null) {
                        mvpView.setBackgroundColor(ContextCompat.getColor(context, R.color.transparent));
                        mvpView.setTextColor(ContextCompat.getColor(context, R.color.colorMVP));
                    }
                }
                if (api_data.has("api_get_ship")) {
                    int ship_id = api_data.getAsJsonObject("api_get_ship").get("api_ship_id").getAsInt();
                    String ship_name = api_data.getAsJsonObject("api_get_ship").get("api_ship_name").getAsString();
                    ((TextView) battleview.findViewById(R.id.battle_getship))
                            .setText(getShipTranslation(ship_name, ship_id, false));
                }
            }
        }
    }

    private void bindFriendFleet(View battleview, JsonArray deck, JsonObject shipMap, String prefix,
                                  boolean combined, int tsNL, int tsNM, int tsCL, int tsCM, int tsCXS) {
        for (int j = 0; j < deck.size(); j++) {
            if (deck.get(j).getAsInt() == -1) {
                battleview.findViewById(getId(KcaUtils.format("%s_%d", prefix, j + 1), R.id.class)).setVisibility(View.INVISIBLE);
                continue;
            }
            JsonObject data = shipMap.getAsJsonObject(String.valueOf(deck.get(j)));
            if (data == null) continue;
            int ship_id = data.get("api_ship_id").getAsInt();
            JsonObject kcdata = getKcShipDataById(ship_id, "name,maxeq");
            if (kcdata == null) continue;

            int maxhp = data.get("api_maxhp").getAsInt();
            int nowhp = data.get("api_nowhp").getAsInt();
            int level = data.get("api_lv").getAsInt();
            int condition = data.get("api_cond").getAsInt();

            String kcname = getShipTranslation(kcdata.get("name").getAsString(), ship_id, false);
            TextView nameView = battleview.findViewById(getId(KcaUtils.format("%s_%d_name", prefix, j + 1), R.id.class));
            if (nameView != null) {
                nameView.setText(kcname);
                nameView.setTextSize(TypedValue.COMPLEX_UNIT_PX, combined ? tsCL : tsNL);
                nameView.setTextColor(ContextCompat.getColor(context, R.color.white));
            }
            TextView condView = battleview.findViewById(getId(KcaUtils.format("%s_%d_cond", prefix, j + 1), R.id.class));
            if (condView != null) {
                condView.setText(String.valueOf(condition));
                int condColor;
                if (condition > 49) condColor = R.color.colorFleetShipKira;
                else if (condition / 10 >= 3) condColor = R.color.colorFleetShipNormal;
                else if (condition / 10 == 2) condColor = R.color.colorFleetShipFatigue1;
                else condColor = R.color.colorFleetShipFatigue2;
                condView.setTextColor(ContextCompat.getColor(context, condColor));
            }
            TextView lvView = battleview.findViewById(getId(KcaUtils.format("%s_%d_lv", prefix, j + 1), R.id.class));
            if (lvView != null) lvView.setText(makeLvString(level));

            TextView hpTxt = battleview.findViewById(getId(KcaUtils.format("%s_%d_hp_txt", prefix, j + 1), R.id.class));
            if (hpTxt != null && !hpTxt.getText().toString().contains(context.getString(R.string.battleview_text_retreated))) {
                hpTxt.setText(makeHpString(nowhp, maxhp));
            }
            float hpPercent = nowhp * VIEW_HP_MAX / (float) maxhp;
            ProgressBar hpBar = battleview.findViewById(getId(KcaUtils.format("%s_%d_hp_bar", prefix, j + 1), R.id.class));
            if (hpBar != null) {
                hpBar.setProgress(Math.round(hpPercent));
                hpBar.setProgressDrawable(getProgressDrawable(hpPercent));
            }
            battleview.findViewById(getId(KcaUtils.format("%s_%d", prefix, j + 1), R.id.class)).setVisibility(View.VISIBLE);
        }
    }

    private void bindRankData(View battleview, JsonObject rankData) {
        if (rankData.has("fnowhpsum")) {
            int fNow = rankData.get("fnowhpsum").getAsInt();
            int fAfter = rankData.get("fafterhpsum").getAsInt();
            int fRate = rankData.get("fdmgrate").getAsInt();
            ((TextView) battleview.findViewById(R.id.friend_fleet_damage))
                    .setText(KcaUtils.format("%d/%d (%d%%)", fAfter, fNow, fRate));
        }
        if (rankData.has("enowhpsum")) {
            int eNow = rankData.get("enowhpsum").getAsInt();
            int eAfter = rankData.get("eafterhpsum").getAsInt();
            int eRate = rankData.get("edmgrate").getAsInt();
            ((TextView) battleview.findViewById(R.id.enemy_fleet_damage))
                    .setText(KcaUtils.format("%d/%d (%d%%)", eAfter, eNow, eRate));
        }

        int rank = rankData.get("rank").getAsInt();
        int[] rankStrIds = {R.string.rank_e, R.string.rank_d, R.string.rank_c, R.string.rank_b,
                R.string.rank_a, R.string.rank_s, R.string.rank_ss};
        int[] rankColorIds = {R.color.colorRankE, R.color.colorRankD, R.color.colorRankC, R.color.colorRankB,
                R.color.colorRankA, R.color.colorRankS, R.color.colorRankS};
        if (rank >= 0 && rank < rankStrIds.length) {
            ((TextView) battleview.findViewById(R.id.battle_result))
                    .setText(context.getString(rankStrIds[rank]));
            ((TextView) battleview.findViewById(R.id.battle_result))
                    .setTextColor(ContextCompat.getColor(context, rankColorIds[rank]));
        }
    }
}
