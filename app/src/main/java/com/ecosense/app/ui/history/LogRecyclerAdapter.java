package com.ecosense.app.ui.history;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.ecosense.app.R;
import com.ecosense.app.data.local.EnvLog;
import com.ecosense.app.utils.LogFormatter;

/** RecyclerView adapter with DiffUtil; one view type per layout (list card / grid card). */
public class LogRecyclerAdapter extends ListAdapter<EnvLog, LogRecyclerAdapter.VH> {

    public interface OnLogClick {
        void onClick(EnvLog log);
    }

    private static final DiffUtil.ItemCallback<EnvLog> DIFF = new DiffUtil.ItemCallback<EnvLog>() {
        @Override
        public boolean areItemsTheSame(@NonNull EnvLog a, @NonNull EnvLog b) {
            return a.id == b.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull EnvLog a, @NonNull EnvLog b) {
            return a.timestamp == b.timestamp && a.temperature == b.temperature
                    && a.humidity == b.humidity && a.pm25 == b.pm25 && a.pm10 == b.pm10
                    && a.aqi == b.aqi && a.synced == b.synced;
        }
    };

    private final Context context;
    private final OnLogClick listener;
    private boolean grid;

    public LogRecyclerAdapter(Context context, OnLogClick listener) {
        super(DIFF);
        this.context = context;
        this.listener = listener;
    }

    public void setGrid(boolean grid) {
        if (this.grid != grid) {
            this.grid = grid;
            notifyDataSetChanged(); // view type changes for every row
        }
    }

    @Override
    public int getItemViewType(int position) {
        return grid ? 1 : 0;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(viewType == 1 ? R.layout.item_log_grid : R.layout.item_log, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final EnvLog l = getItem(position);
        LogFormatter.bind(context, l, h.date, h.status, h.temp, h.hum, h.pm25, h.pm10);
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(l);
        });
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView date, status, temp, hum, pm25, pm10;

        VH(@NonNull View v) {
            super(v);
            date = v.findViewById(R.id.tvDate);
            status = v.findViewById(R.id.tvStatus);
            temp = v.findViewById(R.id.tvTemp);
            hum = v.findViewById(R.id.tvHum);
            pm25 = v.findViewById(R.id.tvPm25);
            pm10 = v.findViewById(R.id.tvPm10);
        }
    }
}
