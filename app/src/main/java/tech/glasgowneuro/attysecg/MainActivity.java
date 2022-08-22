package tech.glasgowneuro.attysecg;

public class MainActivity extends android.app.NativeActivity {
    static {
    System.loadLibrary("vrapi");
    System.loadLibrary("attysecg");
  }
}
