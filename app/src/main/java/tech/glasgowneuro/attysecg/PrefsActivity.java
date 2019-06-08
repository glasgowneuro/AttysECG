package tech.glasgowneuro.attysecg;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;


/**
 * Fires up the preferences
 */
public class PrefsActivity extends AppCompatActivity {

    public static final String HRV_FILENAME = "hr.tsv";
    public static final String HRV_KEY_FILENAME = "hrv_filename";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager().beginTransaction().
                replace(android.R.id.content, new MyPreferenceFragment()).commit();
    }

    public static class MyPreferenceFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(final Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
        }

        private void fixPath() {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            String f = prefs.getString(HRV_KEY_FILENAME, HRV_FILENAME);
            final int i = f.lastIndexOf('.');
            if (i > 0) {
                String fext = f.substring(0, i) + ".tsv";
                prefs.edit().putString(HRV_KEY_FILENAME, fext).apply();
            } else {
                if (f.isEmpty()) {
                    prefs.edit().putString(HRV_KEY_FILENAME, HRV_FILENAME).apply();
                } else {
                    prefs.edit().putString(HRV_KEY_FILENAME, f + ".tsv").apply();
                }
            }
        }

        @Override
        public void onPause() {
            super.onPause();
            fixPath();
        }

        @Override
        public void onStop() {
            super.onStop();
            fixPath();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            fixPath();
        }
    }

}
