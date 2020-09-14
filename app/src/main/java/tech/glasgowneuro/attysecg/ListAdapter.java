package tech.glasgowneuro.attysecg;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public class ListAdapter extends BaseAdapter {
    private Context context;
    private final List<String> heartRate;
    private final List<String> dateTimes;
    private final List<Bitmap> plots;

    ListAdapter(Context context, List<String> heartRate, List<String> dateTimes, List<Bitmap> plots){
        //super(context, R.layout.single_list_app_item, utilsArrayList);
        this.context = context;
        this.heartRate = heartRate;
        this.dateTimes = dateTimes;
        this.plots = plots;
    }

    @Override
    public int getCount() {
        return heartRate.size();
    }

    @Override
    public Object getItem(int i) {
        return i;
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {


        ViewHolder viewHolder;

        if (convertView == null) {
            viewHolder = new ViewHolder();
            LayoutInflater inflater = LayoutInflater.from(context);
            convertView = inflater.inflate(R.layout.history_item, parent, false);
            viewHolder.bpm = convertView.findViewById(R.id.bpm);
            viewHolder.date_time = convertView.findViewById(R.id.date_time);
            viewHolder.plot = convertView.findViewById(R.id.plot);

            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        viewHolder.bpm.setText(heartRate.get(position));
        viewHolder.date_time.setText(dateTimes.get(position));
        viewHolder.plot.setImageBitmap(plots.get(position));

        return convertView;
    }

    private static class ViewHolder {
        TextView bpm;
        TextView date_time;
        ImageView plot;
    }
}
