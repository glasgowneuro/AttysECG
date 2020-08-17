package tech.glasgowneuro.attysecg;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

public class ppgHistory extends AppCompatActivity {
    public ListView historyList;
    Intent mainActivity;
    SQLiteDatabase database;
    Cursor cursor;

    tech.glasgowneuro.attysecg.ListAdapter lAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ppg_histroy);

        database = openOrCreateDatabase("database", MODE_PRIVATE, null);
        cursor = database.rawQuery("Select * from RecordingsTable", null);
        cursor.moveToFirst();
        mainActivity = getIntent();

        Constants.savedBPMs.clear(); Constants.savedDateTimes.clear(); Constants.savedPlots.clear();

        for (int i=cursor.getPosition(); i < cursor.getCount(); i++) {
            String recordedBPM = cursor.getString(0);
            String recordedDateTime = cursor.getString(1);
            byte[] byteArray = cursor.getBlob(2);

            Log.i("Length", byteArray.length + "");

            Bitmap recordedPlot = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);

            Constants.savedBPMs.add(recordedBPM); Constants.savedDateTimes.add(recordedDateTime); Constants.savedPlots.add(recordedPlot);

            cursor.moveToNext();
        }

        historyList = findViewById(R.id.historyList);
        lAdapter = new ListAdapter(this, Constants.savedBPMs, Constants.savedDateTimes, Constants.savedPlots);
        historyList.setAdapter(lAdapter);

        historyList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                ImageView preview = new ImageView(ppgHistory.this);
                preview.setImageBitmap(Constants.savedPlots.get(position));

                AlertDialog.Builder builder = new AlertDialog.Builder(ppgHistory.this);
                builder.setView(preview)
                        .setTitle(Constants.savedBPMs.get(position))
                        .setIcon(R.drawable.heart)
                        .setMessage(Constants.savedDateTimes.get(position))
                        .setCancelable(false)
                        .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                deleteFromDatabase(Constants.savedDateTimes.get(position));
                                Constants.savedBPMs.remove(position);
                                Constants.savedDateTimes.remove(position);
                                Constants.savedPlots.remove(position);
                                lAdapter.notifyDataSetChanged();
                            }
                        })
                        .setNegativeButton("Keep", null)
                        .show();
            }
        });
    }

    public void deleteAll(View view) {
        if (Constants.savedBPMs.size() != 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Are you sure you want to delete all your recordings?")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            deleteAllFromDatabase();
                            Constants.savedBPMs.clear();
                            Constants.savedDateTimes.clear();
                            Constants.savedPlots.clear();
                            lAdapter.notifyDataSetChanged();
                        }
                    })
                    .setNegativeButton("No", null)
                    .show();
        }
        else Toast.makeText(this, "No recordings to delete", Toast.LENGTH_SHORT).show();
    }

    public void back(View view) {
        finish();
    }

    public void deleteFromDatabase(String DateTime) {
        database.delete("RecordingsTable", "DateTimes=?", new String[]{DateTime});
    }

    public void deleteAllFromDatabase() {
        database.delete("RecordingsTable", null, null);
    }
}
