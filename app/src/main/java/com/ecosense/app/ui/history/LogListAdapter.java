package com.ecosense.app.ui.history;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ecosense.app.R;
import com.ecosense.app.data.local.EnvLog;
import com.ecosense.app.utils.LogFormatter;

import java.util.ArrayList;

/** ListView adapter: ArrayAdapter + custom getView with convertView recycling and ViewHolder. */
public class LogListAdapter extends ArrayAdapter<EnvLog> {
    private final LayoutInflater inflater;

    private static class Holder {
        TextView date, status, temp, hum, pm25, pm10;
    }

    public LogListAdapter(@NonNull Context context) {
        super(context, 0, new ArrayList<EnvLog>());
        inflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        Holder h;
        View v = convertView;
        if (v == null) {
            v = inflater.inflate(R.layout.item_log, parent, false);
            h = new Holder();
            h.date = v.findViewById(R.id.tvDate);
            h.status = v.findViewById(R.id.tvStatus);
            h.temp = v.findViewById(R.id.tvTemp);
            h.hum = v.findViewById(R.id.tvHum);
            h.pm25 = v.findViewById(R.id.tvPm25);
            h.pm10 = v.findViewById(R.id.tvPm10);
            v.setTag(h);
        } else {
            h = (Holder) v.getTag();
        }
        EnvLog l = getItem(position);
        if (l != null) {
            LogFormatter.bind(getContext(), l, h.date, h.status, h.temp, h.hum, h.pm25, h.pm10);
        }
        return v;
    }
}
