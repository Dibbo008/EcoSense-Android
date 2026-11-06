package com.ecosense.app.utils;

import android.app.Activity;
import android.content.Intent;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;

import com.ecosense.app.R;
import com.ecosense.app.ui.dashboard.DashboardActivity;
import com.ecosense.app.ui.history.HistoryActivity;
import com.ecosense.app.ui.prediction.PredictionActivity;
import com.ecosense.app.ui.settings.SettingsActivity;

/** Wires the floating bottom navigation pill (view_bottom_nav.xml) on each main screen. */
public final class NavHelper {
    public static final int NONE = -1, HOME = 0, HISTORY = 1, PREDICT = 2, SETTINGS = 3;

    private static final int[] IDS = {R.id.navHome, R.id.navHistory, R.id.navPredict, R.id.navSettings};
    private static final Class<?>[] TARGETS = {
            DashboardActivity.class, HistoryActivity.class,
            PredictionActivity.class, SettingsActivity.class};

    private NavHelper() {}

    public static void setup(Activity activity, int current) {
        int active = ContextCompat.getColor(activity, R.color.eco_navy);
        int inactive = ContextCompat.getColor(activity, R.color.nav_inactive);
        for (int i = 0; i < IDS.length; i++) {
            ImageView v = activity.findViewById(IDS[i]);
            if (v == null) continue;
            boolean selected = i == current;
            v.setColorFilter(selected ? active : inactive);
            v.setBackgroundResource(selected ? R.drawable.bg_nav_active : 0);
            final int target = i;
            v.setOnClickListener(x -> {
                if (target != current) go(activity, current, TARGETS[target]);
            });
        }
    }

    @SuppressWarnings("deprecation")
    private static void go(Activity activity, int current, Class<?> target) {
        Intent intent = new Intent(activity, target);
        if (target == DashboardActivity.class) {
            // Home is the root: pop everything above it
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
        } else {
            activity.startActivity(intent);
            if (current != HOME) activity.finish(); // keep stack = [Home, current tab]
        }
        activity.overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }
}
