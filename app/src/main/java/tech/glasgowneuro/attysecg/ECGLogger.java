package tech.glasgowneuro.attysecg;

import android.app.Activity;
import org.apache.commons.lang3.RandomStringUtils;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ECGLogger {

    static private final boolean doFirebaseLogging = !BuildConfig.DEBUG;

    static private final Object loginObj = new Object();

    private static final String TAG = "ECGLogger";
    private static final String PREFS_PRIV_ASKED_FOR_OPT_IN = "askedforoptin";
    private static final String PREFS_PRIV_OPTED_IN = "datashareoptin";
    private static final String PREFS_ID = "id";
    private static final String PREFS_LU = "lu";
    private static final String PREFS_PREFIX_PROPERTY = "prop_";
    private static final String PREFS_TIME_OF_CREATION = "timeofcreation";
    private static final String FB_UID = "fbuid";

    // the unique user ID
    private static String uniqueID = null;

    // time of creation (calc from internal storage and Tailors in the directory
    private static Long timeOfCreation = System.currentTimeMillis();
    private static final Object timeOfCreationSync = new Object();

    public static final String FIRESTORE_R_PEAKS = "rpeaks";
    public static final String FIRESTORE_ECG = "plays";

    private String losenord = null;

    ECGLogger(Activity activity) {
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .build();

        final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        firestore.setFirestoreSettings(settings);
        logIn(activity);
        updateFirebasePerm(activity);
    }

    private void updateFirebasePerm(Activity activity) {
        FirebaseAnalytics firebaseAnalytics = FirebaseAnalytics.getInstance(activity);
        if (hasOptedIn(activity)) {
            firebaseAnalytics.setAnalyticsCollectionEnabled(true);
            Log.d(TAG, "Firebase logging allowed");
        } else {
            firebaseAnalytics.setAnalyticsCollectionEnabled(false);
            Log.d(TAG, "Firebase logging disabled");
        }
    }

    static public int daysUsed() {
        long ms = System.currentTimeMillis() - timeOfCreation;
        final long msPerDay = 24 * 60 * 60 * 1000;
        return (int) (ms / msPerDay);
    }

    public boolean hasAskedForOptIn(Activity activity) {
        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(activity);
        return sharedPref.getBoolean(PREFS_PRIV_ASKED_FOR_OPT_IN, false);
    }

    private void setAskedForOptIn(Activity activity) {
        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(activity);
        final SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(PREFS_PRIV_ASKED_FOR_OPT_IN, true);
        editor.apply();
    }

    public int getProperty(Activity activity, String key, int default_val) {
        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(activity);
        return sharedPref.getInt(PREFS_PREFIX_PROPERTY + key, default_val);
    }

    public void setProperty(Activity activity, String key, int value) {
        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(activity);
        final FirebaseAnalytics firebaseAnalytics = FirebaseAnalytics.getInstance(activity);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt(PREFS_PREFIX_PROPERTY + key, value);
        editor.apply();
        if (hasOptedIn(activity)) {
            firebaseAnalytics.setUserProperty(key, Integer.toString(value));
        }
    }

    public long getPropertyLong(Activity activity, String key, long default_val) {
        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(activity);
        return sharedPref.getLong(PREFS_PREFIX_PROPERTY + key, default_val);
    }

    public void setPropertyLong(Activity activity, String key, long value) {
        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(activity);
        final FirebaseAnalytics firebaseAnalytics = FirebaseAnalytics.getInstance(activity);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putLong(PREFS_PREFIX_PROPERTY + key, value);
        editor.apply();
        if (hasOptedIn(activity)) {
            firebaseAnalytics.setUserProperty(key, Long.toString(value));
        }
    }

    boolean hasOptedIn(Activity activity) {
        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(activity);
        return sharedPref.getBoolean(PREFS_PRIV_OPTED_IN, false);
    }

    void setCollectAndSendDataOptedIn(Activity activity) {
        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(activity);
        setAskedForOptIn(activity);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(PREFS_PRIV_OPTED_IN, true);
        editor.apply();
        updateFirebasePerm(activity);
    }

    /**
     * Reads the preferences from internal memory. In particular the user ID.
     * Bundle can be null.
     */
    static public String getUniqueID() {
        return uniqueID;
    }

    public void logFirebaseEvent(Activity activity, String name, Bundle bundle) {
        if (doFirebaseLogging) {
            FirebaseAnalytics firebaseAnalytics = FirebaseAnalytics.getInstance(activity);
            firebaseAnalytics.logEvent(name, bundle);
        } else {
            String logString = "Fake (!) firebase logging: name = " + name;
            if (null != bundle) {
                logString = logString + ", bundle = " + bundle.toString();
            }
            Log.d(TAG, logString);
        }
    }

    String getCollectionAsString(String coll) {
        String data = "";
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
        final FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (null == currentUser) {
            return null;
        } else {
            Log.e(TAG,"Firebase user ID is null?!");
        }
        final CollectionReference collectionReference = firestore.collection(coll);
        final Task<QuerySnapshot> t =
                collectionReference.whereEqualTo(FB_UID, currentUser.getUid()).get();
        while (!t.isComplete()) {
            try {
                Thread.sleep(10);
            } catch (Exception e) {
                Log.v(TAG,"Thread.sleep",e);
            }
        }
        try {
            if (t.getResult() == null) {
                Log.e(TAG, "Firebase permission denied.");
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Firebase permission denied.",e);
            return null;
        }
        final List<DocumentSnapshot> documents = t.getResult().getDocuments();
        if (documents.isEmpty()) {
            return null;
        }
        for (DocumentSnapshot document : documents) {
            if (document.getData() != null) {
                data += document.getData().toString();
                data += "\n\n";
            }
        }
        return data;
    }

    class DeleteUserDataThread extends Thread {

        private void deleteCollection(String coll) {
            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
            final FirebaseUser currentUser = firebaseAuth.getCurrentUser();
            if (null == currentUser) {
                return;
            } else {
                Log.e(TAG,"Firebase user ID is null?!");
            }
            final CollectionReference collectionReference = firestore.collection(coll);
            final Task<QuerySnapshot> t =
                    collectionReference.whereEqualTo(FB_UID, currentUser.getUid()).get();
            while (!t.isComplete()) {
                try {
                    Thread.sleep(10);
                } catch (Exception e) {
                    Log.v(TAG,"Thread.sleep",e);
                }
            }
            try {
                if (t.getResult() == null) {
                    Log.e(TAG, "Firebase permission denied.");
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, "Firebase permission denied.",e);
                return;
            }
            final List<DocumentSnapshot> documents = t.getResult().getDocuments();
            for (DocumentSnapshot document : documents) {
                final String docid = document.getId();
                Log.d(TAG, "deleting " + docid + " => " + document.getData());
                final Task<Void> t2 = document.getReference().delete();
                while (!t2.isComplete()) {
                    try {
                        Thread.sleep(10);
                    } catch (Exception e) {
                        Log.v(TAG,"Thread.sleep",e);
                    }
                }
                Log.d(TAG, "Document " + docid + " deleted");
            }
        }

        public final void run() {
            Log.d(TAG, "Starting userdata cleanup thread");
            deleteCollection(FIRESTORE_R_PEAKS);
            deleteCollection(FIRESTORE_ECG);
            FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
            final FirebaseUser currentUser = firebaseAuth.getCurrentUser();
            if (currentUser != null) {
                if (currentUser.getEmail() == null) return;
                Log.d(TAG, "Deleting user: " + currentUser.getUid() + "[" + currentUser.getEmail() + "," + losenord + "]");
                AuthCredential credential =
                        EmailAuthProvider.getCredential(currentUser.getEmail(), losenord);
                // Prompt the user to re-provide their sign-in credentials
                currentUser.reauthenticate(credential)
                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                currentUser.delete()
                                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                                            @Override
                                            public void onComplete(@NonNull Task<Void> task) {
                                                if (task.isSuccessful()) {
                                                    Log.d(TAG, "User account deleted.");
                                                }
                                            }
                                        });
                            }
                        });
            } else {
                Log.e(TAG, "BUG: user no longer exists!");
            }
            Log.d(TAG, "Finished userdata cleanup thread");
        }
    }

    public void optOut(Activity activity) {
        new DeleteUserDataThread().start();
        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(activity);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.remove(PREFS_ID);
        editor.remove(PREFS_LU);
        editor.remove(PREFS_TIME_OF_CREATION);
        editor.remove(PREFS_PRIV_OPTED_IN);
        editor.remove(PREFS_PRIV_ASKED_FOR_OPT_IN);
        editor.apply();
        FirebaseAnalytics firebaseAnalytics = FirebaseAnalytics.getInstance(activity);
        firebaseAnalytics.resetAnalyticsData();
    }


    private String getEmail() {
        return uniqueID.replace("-", "") + "@glasgowneuro.tech";
    }


    public void logIn(Activity _activity) {
        final Activity activity = _activity;

        if (!hasOptedIn(activity)) return;

        final FirebaseAnalytics firebaseAnalytics = FirebaseAnalytics.getInstance(activity);
        final FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(activity);
        final SharedPreferences.Editor editor = sharedPref.edit();

        synchronized (loginObj) {
            // trying to get the identifier from the previous session
            // if not create a new one
            if (uniqueID == null) {
                uniqueID = sharedPref.getString(PREFS_ID, UUID.randomUUID().toString());
                firebaseAnalytics.setUserId(uniqueID);
                Log.d(TAG, "auth: uniqueID=" + uniqueID);
                editor.putString(PREFS_ID, uniqueID);
                editor.apply();
            }

            // password
            if (losenord == null) {
                String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
                losenord = sharedPref.getString(PREFS_LU, RandomStringUtils.random(15, characters));
                Log.d(TAG, "auth: password set");
                editor.putString(PREFS_LU, losenord);
                editor.apply();
            }

            // stores the time of the creation here
            timeOfCreation = sharedPref.getLong(PREFS_TIME_OF_CREATION, System.currentTimeMillis());
            Log.d(TAG, "time of creation=" + timeOfCreation);
            editor.putLong(PREFS_TIME_OF_CREATION, timeOfCreation);
            editor.apply();


            FirebaseUser currentUser = firebaseAuth.getCurrentUser();
            if (currentUser != null) {
                Log.d(TAG, "Logged in: " + currentUser.getEmail());
                return;
            }
            final String usr = getEmail();
            final String lwLocal = losenord;
            firebaseAuth.signInWithEmailAndPassword(usr, lwLocal)
                    .addOnCompleteListener(activity, new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {
                            if (task.isSuccessful()) {
                                Log.d(TAG, "Logged in: " + firebaseAuth.getCurrentUser().getEmail());
                            } else {
                                Log.d(TAG, "Creating new user: " + usr);
                                firebaseAuth.createUserWithEmailAndPassword(usr, lwLocal)
                                        .addOnCompleteListener(activity, new OnCompleteListener<AuthResult>() {
                                            @Override
                                            public void onComplete(@NonNull Task<AuthResult> task) {
                                                if (task.isSuccessful()) {
                                                    Log.d(TAG, "User created: " +
                                                            firebaseAuth.getCurrentUser().getEmail());
                                                } else {
                                                    Log.e(TAG,
                                                            "auth:newuser:fail", task.getException());
                                                }
                                            }
                                        });

                            }
                        }
                    });
        }
    }

    public void logOut() {
        synchronized (loginObj) {
            FirebaseAuth.getInstance().signOut();
        }
    }

    private void addToFirestore(String collection, Map<String, Object> data) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
        final FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (null != currentUser) {
            data.put(FB_UID,currentUser.getUid());
        } else {
            Log.e(TAG,"Firebase user ID is null?!");
        }
        data.put("build_config_is_debug", BuildConfig.DEBUG);
        try {
            firestore.collection(collection).add(data);
        } catch (Exception e) {
            Log.e(TAG,"Firebase couldn't save data.",e);
            return;
        }
        Log.d(TAG,"Firebase: data saved: "+collection);
    }

    public void addToFirestoreTailors(Map<String, Object> data) {
        addToFirestore(FIRESTORE_R_PEAKS, data);
    }

    public void addToFirestorePlays(Map<String, Object> data) {
        addToFirestore(FIRESTORE_ECG, data);
    }



}
