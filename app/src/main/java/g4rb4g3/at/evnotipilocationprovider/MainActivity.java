package g4rb4g3.at.evnotipilocationprovider;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.TextView;

import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.text.DateFormat;
import java.util.Date;
import java.util.Queue;

import static g4rb4g3.at.evnotipilocationprovider.BroadcastsReceiver.EXTRA_EXCEPTION_MESSAGE;
import static g4rb4g3.at.evnotipilocationprovider.BroadcastsReceiver.EXTRA_NMEA_SENTENCE;
import static g4rb4g3.at.evnotipilocationprovider.BroadcastsReceiver.LOCAL_BROADCAST_EXCEPTION;
import static g4rb4g3.at.evnotipilocationprovider.BroadcastsReceiver.LOCAL_BROADCAST_NMEA_SENTENCE;

public class MainActivity extends Activity {

  public static final String PREFERENCES_NAME = "preferences";
  public static final String PREFERENCES_BROADCAST_GPS = "broadcast_gps";

  private SharedPreferences mSharedPreferences;
  private CheckBox mCbBroadcastGps;
  private TextView mTvNmeaMessages, mTvExceptionMessage;
  private Queue<String> mNmeaQueue = new CircularFifoQueue<>(15);

  private BroadcastReceiver mNmeaSentenceReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      String timestamp = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(new Date());
      if (LOCAL_BROADCAST_NMEA_SENTENCE.equals(intent.getAction())) {
        String sentence = timestamp + ": " + intent.getStringExtra(EXTRA_NMEA_SENTENCE);
        mNmeaQueue.add(sentence);
        StringBuilder msg = new StringBuilder();
        for (String s : mNmeaQueue) {
          msg.append(s);
        }
        mTvNmeaMessages.setText(msg.toString());
      } else if (LOCAL_BROADCAST_EXCEPTION.equals(intent.getAction())) {
        mTvExceptionMessage.setText(timestamp + ": " + intent.getStringExtra(EXTRA_EXCEPTION_MESSAGE));
      }
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    mSharedPreferences = getApplicationContext().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    mCbBroadcastGps = (CheckBox) findViewById(R.id.cb_broadcast_gps);
    mCbBroadcastGps.setOnCheckedChangeListener((compoundButton, b) -> mSharedPreferences.edit().putBoolean(PREFERENCES_BROADCAST_GPS, b).commit());

    mTvNmeaMessages = (TextView) findViewById(R.id.tv_nmea_messages);
    mTvExceptionMessage = (TextView) findViewById(R.id.tv_exception_message);
  }

  @Override
  protected void onResume() {
    super.onResume();
    mCbBroadcastGps.setChecked(mSharedPreferences.getBoolean(PREFERENCES_BROADCAST_GPS, false));
    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(LOCAL_BROADCAST_NMEA_SENTENCE);
    intentFilter.addAction(LOCAL_BROADCAST_EXCEPTION);
    getApplicationContext().registerReceiver(mNmeaSentenceReceiver, intentFilter);
  }

  @Override
  protected void onPause() {
    super.onPause();
    getApplicationContext().unregisterReceiver(mNmeaSentenceReceiver);
  }
}
