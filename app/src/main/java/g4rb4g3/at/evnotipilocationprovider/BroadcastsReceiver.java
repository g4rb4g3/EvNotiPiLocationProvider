package g4rb4g3.at.evnotipilocationprovider;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.util.Log;

import com.lge.ivi.carinfo.CarInfoManager;

import net.sf.marineapi.nmea.parser.SentenceFactory;
import net.sf.marineapi.nmea.sentence.GGASentence;
import net.sf.marineapi.nmea.sentence.GSASentence;
import net.sf.marineapi.nmea.sentence.RMCSentence;
import net.sf.marineapi.nmea.sentence.SentenceId;
import net.sf.marineapi.nmea.sentence.TalkerId;
import net.sf.marineapi.nmea.util.DataStatus;
import net.sf.marineapi.nmea.util.Date;
import net.sf.marineapi.nmea.util.FaaMode;
import net.sf.marineapi.nmea.util.GpsFixQuality;
import net.sf.marineapi.nmea.util.GpsFixStatus;
import net.sf.marineapi.nmea.util.Position;
import net.sf.marineapi.nmea.util.Time;
import net.sf.marineapi.nmea.util.Units;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BroadcastsReceiver extends BroadcastReceiver {
  public static final String TAG = "EvNotiPiLocationProvidr";
  public static final String ACTION_NAVI_GPS_CHANGED = "com.hkmc.telematics.gis.action.NAVI_GPS_CHANGED";
  public static final String EXTRA_ALTITUDE = "com.hkmc.telematics.gis.extra.ALT";
  public static final String EXTRA_LONGITUDE = "com.hkmc.telematics.gis.extra.LON";
  public static final String EXTRA_LATITUDE = "com.hkmc.telematics.gis.extra.LAT";
  public static final String EXTRA_HDOP = "com.hkmc.telematics.gis.extra.HDOP";
  public static final String EXTRA_PDOP = "com.hkmc.telematics.gis.extra.PDOP";
  public static final String EXTRA_TIME = "com.hkmc.telematics.gis.extra.TIME";
  public static final String LOCAL_BROADCAST_NMEA_SENTENCE = "g4rb4g3.at.evnotipilocationprovider.nmeasentence";
  public static final String LOCAL_BROADCAST_EXCEPTION = "g4rb4g3.at.evnotipilocationprovider.exception";
  public static final String EXTRA_NMEA_SENTENCE = "nmea.sentece";
  public static final String EXTRA_EXCEPTION_MESSAGE = "exception.message";

  final static int PORT = 6942;

  private Context mContext;

  @Override
  public void onReceive(Context context, Intent intent) {
    mContext = context;
    boolean broadcastGps = context.getSharedPreferences(MainActivity.PREFERENCES_NAME, Context.MODE_PRIVATE).getBoolean(MainActivity.PREFERENCES_BROADCAST_GPS, false);
    if (ACTION_NAVI_GPS_CHANGED.equals(intent.getAction()) && broadcastGps) {
      double altitude = intent.getDoubleExtra(EXTRA_ALTITUDE, 0.0);
      double longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0);
      double latitude = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0);
      double hdop = intent.getIntExtra(EXTRA_HDOP, 0);
      double pdop = intent.getIntExtra(EXTRA_PDOP, 0);
      String time = intent.getStringExtra(EXTRA_TIME);
      double speed = CarInfoManager.getInstance().getCarSpeed();
      Date date = new Date(Integer.parseInt(time.substring(0, 4)), Integer.parseInt(time.substring(4, 6)), Integer.parseInt(time.substring(6, 8)));
      List<byte[]> messages = buildNmeaMessages(altitude, longitude, latitude, hdop, pdop, new Time(time.substring(8)), date, speed);
      new BroadcastServer().execute(messages);
    }
  }

  private List<byte[]> buildNmeaMessages(double altitude, double longitude, double latitude, double hdop, double pdop, Time time, Date date, double speed) {
    Intent intent = new Intent(LOCAL_BROADCAST_NMEA_SENTENCE);

    ArrayList<byte[]> sentences = new ArrayList<>();
    Position position = new Position(latitude, longitude, altitude);
    SentenceFactory sentenceFactory = SentenceFactory.getInstance();

    RMCSentence rmcSentence = (RMCSentence) sentenceFactory.createParser(TalkerId.GP, SentenceId.RMC);
    rmcSentence.setStatus(DataStatus.ACTIVE);
    rmcSentence.setMode(FaaMode.AUTOMATIC);
    rmcSentence.setDate(date);
    rmcSentence.setTime(time);
    rmcSentence.setPosition(position);
    rmcSentence.setSpeed(speed * 0.539956803); //convert km/h to kn
    String sentence = rmcSentence.toSentence() + "\r\n";
    sentences.add(sentence.getBytes());
    intent.putExtra(EXTRA_NMEA_SENTENCE, sentence);
    mContext.sendBroadcast(intent);

    GGASentence ggaSentence = (GGASentence) sentenceFactory.createParser(TalkerId.GP, SentenceId.GGA);
    ggaSentence.setFixQuality(GpsFixQuality.NORMAL);
    ggaSentence.setPosition(position);
    ggaSentence.setHorizontalDOP(hdop);
    ggaSentence.setTime(time);
    ggaSentence.setAltitudeUnits(Units.METER);
    ggaSentence.setSatelliteCount(3); //not provided by the head unit, we need at least 3 satellites
    sentence = ggaSentence.toSentence() + "\r\n";
    sentences.add(sentence.getBytes());
    intent.putExtra(EXTRA_NMEA_SENTENCE, sentence);
    mContext.sendBroadcast(intent);

    GSASentence gsaSentence = (GSASentence) sentenceFactory.createParser(TalkerId.GP, SentenceId.GSA);
    gsaSentence.setMode(FaaMode.AUTOMATIC);
    gsaSentence.setFixStatus(GpsFixStatus.GPS_3D);
    gsaSentence.setHorizontalDOP(hdop);
    gsaSentence.setPositionDOP(pdop);
    sentence = gsaSentence.toSentence() + "\r\n";
    sentences.add(sentence.getBytes());
    intent.putExtra(EXTRA_NMEA_SENTENCE, sentence);
    mContext.sendBroadcast(intent);

    return Collections.unmodifiableList(sentences);
  }

  private class BroadcastServer extends AsyncTask<List<byte[]>, Void, Void> {
    @Override
    protected Void doInBackground(List<byte[]>... messages) {
      try {
        WifiManager wifi = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcp = wifi.getDhcpInfo();

        int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
        byte[] quads = new byte[4];
        for (int k = 0; k < 4; k++) {
          quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
        }
        InetAddress broadcastAddress = InetAddress.getByAddress(quads);

        DatagramSocket socket = new DatagramSocket();
        socket.setBroadcast(true);
        for (byte[] message : messages[0]) {
          DatagramPacket sendPacket = new DatagramPacket(message, message.length, broadcastAddress, PORT);
          socket.send(sendPacket);
        }
      } catch (IOException e) {
        Log.e(TAG, "error sending broadcast message", e);
        Intent intent = new Intent(LOCAL_BROADCAST_EXCEPTION);
        intent.putExtra(EXTRA_EXCEPTION_MESSAGE, e.getMessage());
        mContext.sendBroadcast(intent);
      }
      return null;
    }
  }
}
