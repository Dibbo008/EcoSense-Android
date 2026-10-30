package com.ecosense.app.utils;

import java.util.Calendar;

/** Daily eco-tip feed: one tip per day of the year, rotating. */
public final class EcoTips {
    private static final String[] TIPS = {
            "Switch off lights and chargers when not in use to cut household energy waste.",
            "Carry a reusable bottle: one bottle can replace hundreds of single-use plastics a year.",
            "Walk, cycle or use public transport for short trips to reduce PM2.5 emissions.",
            "Plant a tree or keep indoor plants: they help absorb CO\u2082 and improve air quality.",
            "Take shorter showers. Saving even 2 minutes conserves many litres of water.",
            "Compost kitchen scraps instead of sending them to landfill.",
            "Avoid burning waste. It releases harmful PM10 and PM2.5 particles.",
            "Wash clothes in cold water and air-dry them when you can.",
            "Buy local and seasonal food to cut transport emissions.",
            "On high-PM days, keep windows closed and avoid strenuous outdoor exercise.",
            "Unplug devices on standby: phantom loads can add up to 10% of home energy use.",
            "Repair before you replace. Fixing items keeps waste out of landfills.",
            "Use a cloth bag for shopping and say no to extra plastic bags.",
            "Set your AC a degree or two higher: it saves energy with little comfort loss.",
            "Share rides or carpool to reduce traffic pollution in your city."
    };

    private EcoTips() {}

    public static String today() {
        int day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        return TIPS[day % TIPS.length];
    }
}
