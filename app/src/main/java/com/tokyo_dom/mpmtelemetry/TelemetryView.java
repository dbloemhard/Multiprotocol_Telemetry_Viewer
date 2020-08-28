package com.tokyo_dom.mpmtelemetry;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class TelemetryView extends AppCompatActivity {

    //Visual Elements
    TextView mpmStatus, valueV1, valueV2, valueRSSI, valueTXRSSI, valueLQI, valueTXLQI, valueRawData, valueTimer, resetTimer;
    MenuItem ConnectionButton;
    Spinner valueAdditionalCallout;


    //Data from Telemetry
    String currentProtocol = "";
    String currentSubProtocol = "";
    String rawData = "";
    float V1 = 0, V2 = 0;
    int RSSI=0, TX_RSSI=0, LQI=0, TX_LQI=0;
    String errorMessage;

    FloatingActionButton fab;

    //Settings
    String address = "";
    boolean autoConnect = false;
    boolean alarmConnection = false;
    boolean alarmV1Enabled = false;
    float alarmV1Level = 0;
    boolean alarmV2Enabled = false;
    float alarmV2Level = 0;
    boolean alarmRssiEnabled = false;
    int alarmRssiLevel = 0;
    boolean alarmTxRssiEnabled = false;
    int alarmTxRssiLevel = 0;
    boolean alarmLqiEnabled = false;
    int alarmLqiLevel = 0;
    boolean alarmTxLqiEnabled = false;
    int alarmTxLqiLevel = 0;
    String additionalCallout = "";

    //Bluetooth objects
    private ProgressDialog progress;
    String ConnectedAddress = "";
    BluetoothAdapter myBluetooth = null;
    BluetoothSocket btSocket = null;
    public boolean isBtConnected = false;
    //SPP UUID. Look for it
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    InputStream btInputStream;
    Thread workerThread;
    byte[] readBuffer;
    byte[] telemetryMessage;
    int readBufferPosition;
    //int counter;
    volatile boolean stopWorker;
    int packetCount=0, shortPacketCount=0, shiftedCount=0, goodPacketCount=0, minLength=9999, maxLength=0, avgLength=0, totalLength=0;
    BluetoothLostReceiver bluetoothLostReceiver;

    //Speech objects
    TextToSpeech tts;
    long alarmInterval=10000;
    long alarmV1Issued;
    long alarmV2Issued;
    long alarmRSSIIssued;
    long alarmTXRSSIIssued;
    long alarmLQIIssued;
    long alarmTXLQIIssued;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //view of the telemetryViewer
        setContentView(R.layout.activity_telemetry_view);

        //Text views that display Telemetry Data
        mpmStatus = (TextView) findViewById(R.id.mpmStatus);
        valueV1 = (TextView) findViewById(R.id.valueV1);
        valueV2 = (TextView) findViewById(R.id.valueV2);
        valueRSSI = (TextView) findViewById(R.id.valueRSSI);
        valueTXRSSI = (TextView) findViewById(R.id.valueTXRSSI);
        valueLQI = (TextView) findViewById(R.id.valueLQI);
        valueTXLQI = (TextView) findViewById(R.id.valueTXLQI);
        valueRawData = (TextView) findViewById(R.id.valueRawData);

        // Timer display with reset button
        valueTimer = (TextView) findViewById(R.id.valueTimer);
        resetTimer = (TextView) findViewById(R.id.resetTimer);
        resetTimer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TimerCounter=0;
                updateTimer();
            }
        });

        // Additional callout dropdown
        valueAdditionalCallout = (Spinner) findViewById(R.id.valueAdditionalCallout);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.callout_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        valueAdditionalCallout.setAdapter(adapter);
        valueAdditionalCallout.setSelection(0);
        valueAdditionalCallout.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view,
                                               int position, long id) {
                        if(position > 0){
                            additionalCallout = (String) parent.getItemAtPosition(position);
                        }else{
                            additionalCallout = "";
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        // TODO Auto-generated method stub
                    }
                }
        );

        // Floating action button to start/stop the timer
        fab = findViewById(R.id.fab);
        fab.setImageResource(R.drawable.ic_baseline_play_arrow_24);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                StartTimer();
            }
        });

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        GetPreferences();
        InitializeBluetooth();
        InitializeSpeechEngine();

    }

    @Override
    protected void onResume() {
        super.onResume();

        // In case settings have been updated
        GetPreferences();

        // if connected, check if address is different
        // if not connected, set button up for connection
        if (btSocket!=null && btSocket.isConnected() && address != ConnectedAddress) //If the currently connected
        {
            DisconnectBT();
        }
        else
        {
            SetConnectButton();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_telemetry_viewer, menu);
        //TopMenuActionBar = menu;
        ConnectionButton = menu
                .findItem(R.id.action_connection);
        SetConnectButton();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            // Make an intent to open the settings activity.
            Intent i = new Intent(TelemetryView.this, TelemetrySettings.class);
            startActivity(i);
        }
        if (id == R.id.action_connection){
            if (item.getTitle().toString().contentEquals(getString(R.string.action_connect)))
            {
                new TelemetryView.ConnectBT().execute(); //Call the class to connect
            }
            else if  (item.getTitle().toString().contentEquals(getString(R.string.action_disconnect)))
            {
                DisconnectBT();
            }
            else if  (item.getTitle().toString().contentEquals(getString(R.string.action_settings)))
            {
                // Make an intent to open the settings activity.
                Intent i = new Intent(TelemetryView.this, TelemetrySettings.class);
                startActivity(i);
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void GetPreferences()
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        address = preferences.getString("address", ""); //"" is the default String to return if the preference isn't foundaddress = findPreference("address");
        autoConnect = preferences.getBoolean("autoconnect",false);
        alarmConnection = preferences.getBoolean("connectionalarm",false);
        alarmV1Enabled = preferences.getBoolean("v1alarm",false);
        alarmV1Level = Float.parseFloat(preferences.getString("v1alarmval", "0.0"));
        alarmV2Enabled = preferences.getBoolean("v2alarm",false);
        alarmV2Level = Float.parseFloat(preferences.getString("v2alarmval", "0.0"));
        alarmRssiEnabled = preferences.getBoolean("rssialarm",false);
        alarmRssiLevel = Integer.parseInt(preferences.getString("rssialarmval", "0"));
        alarmTxRssiEnabled = preferences.getBoolean("txrssialarm",false);
        alarmTxRssiLevel = Integer.parseInt(preferences.getString("txrssialarmval", "0"));
        alarmLqiEnabled = preferences.getBoolean("lqialarm",false);
        alarmLqiLevel = Integer.parseInt(preferences.getString("lqialarmval", "0"));
        alarmTxLqiEnabled = preferences.getBoolean("txlqialarm",false);
        alarmTxLqiLevel = Integer.parseInt(preferences.getString("txlqialarmval", "0"));
    }

    private void InitializeBluetooth()
    {
        //Register to receive notifications of bluetooth disconnection
        if (bluetoothLostReceiver == null)
        {
            bluetoothLostReceiver = new BluetoothLostReceiver();
            bluetoothLostReceiver.setMainActivity(this);
            IntentFilter filter = new IntentFilter("android.bluetooth.device.action.ACL_DISCONNECTED");
            registerReceiver(bluetoothLostReceiver, filter);
        }

        if(address=="")
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(TelemetryView.this);
            builder.setMessage(R.string.dialog_message_no_bt_selected)
                    .setTitle(R.string.dialog_title_no_bt_selected);
            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // User clicked OK button
                    // Make an intent to start the device list activity.
                    Intent i = new Intent(TelemetryView.this, TelemetrySettings.class);
                    startActivity(i);
                }
            });
            builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // User cancelled the dialog
                    msg("Bluetooth device not configured");
                }
            });
            AlertDialog dialog = builder.create();
            dialog.show();
        }
        else {
            if (autoConnect)
            {
                new TelemetryView.ConnectBT().execute(); //Call the class to connect
            }
        }
    }

    private void InitializeSpeechEngine()
    {
        tts=new TextToSpeech(TelemetryView.this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                // TODO Auto-generated method stub
                if(status == TextToSpeech.SUCCESS){
                    int result=tts.setLanguage(Locale.US);
                    if(result==TextToSpeech.LANG_MISSING_DATA ||
                            result==TextToSpeech.LANG_NOT_SUPPORTED){
                        Log.e("error", "This Language is not supported");
                    }
                    else{
                        //Say("Welcome to Open TX");
                    }
                }
                else
                    Log.e("error", "TTS Initilization Failed!");
            }
        });
    }


    //YOUR GLOBAL VARIABLES
    private static final long UPDATE_INTERVAL = 1000;
    private static final long DELAY_INTERVAL = 0;
    int TimerCounter;
    Timer timer;

    private void StartTimer() {
        Say("Timer Started",false);
        TimerCounter = 0;
        timer = new Timer();
        timer.scheduleAtFixedRate(
                new TimerTask() {

                    public void run() {
                        updateTimer();
                        if (TimerCounter>0 && TimerCounter%60==0)
                        MinuteCall();
                        TimerCounter++;
                    }
                },
                DELAY_INTERVAL,
                UPDATE_INTERVAL);
        fab.setImageResource(R.drawable.ic_baseline_pause_24);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                StopTimer();
            }
        });
    }

    private void MinuteCall() {
        int MinuteCounter=TimerCounter/60;
        Say(MinuteCounter + (MinuteCounter==1?" minute.":" minutes."),true);
        switch (additionalCallout){
            case "Voltage 1":
                Say("Voltage 1,,, " + String.format("%.2f", V1) + " volt", true);
                break;
            case "Voltage 2":
                Say("Voltage 1,,, " + String.format("%.2f", V1) + " volt", true);
                break;
            case "RSSI":
                Say("Voltage 1,,, " + String.format("%.2f", V1) + " volt", true);
                break;
            case "Transmitter RSSI":
                Say("Voltage 1,,, " + String.format("%.2f", V1) + " volt", true);
                break;
            case "Link Quality":
                Say("Voltage 1,,, " + String.format("%.2f", V1) + " volt", true);
                break;
            case "Transmitter Link Quality":
                Say("Voltage 1,,, " + String.format("%.2f", V1) + " volt", true);
                break;
            default: // "--None--" or ""
                // Do nothing
                break;
        }
    }

    private void StopTimer() {
        if (timer != null)
        {
            timer.cancel();
            fab.setImageResource(R.drawable.ic_baseline_play_arrow_24);
            fab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    StartTimer();
                }
            });
            Say("Timer Stopped",false);
        }

    }

    //Simple timer display, minutes and seconds... not hours because no drone will fly for an hour
    private void updateTimer()
    {
        //Just in case
        valueTimer = (TextView) findViewById(R.id.valueTimer);
        valueTimer.setText((int) TimerCounter/60 + ":" + String.format("%02d", (int) TimerCounter%60));
    }

    private void SetConnectButton()
    {
        if (ConnectionButton!=null)
        {
            if (btSocket!=null && btSocket.isConnected()) //If the currently connected
            {
                ConnectionButton.setIcon(R.drawable.ic_baseline_bluetooth_connected_24_white);
                ConnectionButton.setTitle(R.string.action_disconnect);
            }
            else
            {   // Not connected, check if the address is configured
                if (address != "")
                {
                    ConnectionButton.setIcon(R.drawable.ic_baseline_bluetooth_disabled_24_white);
                    ConnectionButton.setTitle(R.string.action_connect);
                }
                else
                {
                    ConnectionButton.setIcon(R.drawable.ic_baseline_settings_bluetooth_24_white);
                    ConnectionButton.setTitle(R.string.action_settings);
                }
            }
        }
    }

    /*
    private void SetConnectButton_old()
    {
        if (btSocket!=null && btSocket.isConnected()) //If the currently connected
        {
            fab.setImageResource(R.drawable.ic_baseline_bluetooth_connected_24);
            fab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    DisconnectBT();
                }
            });
        }
        else
        {   // Not connected, check if the address is configured
            if (address != "")
            {
                fab.setImageResource(R.drawable.ic_baseline_bluetooth_disabled_24);
                fab.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        new TelemetryView.ConnectBT().execute(); //Call the class to connect
                    }
                });

            }
            else
            {
                fab.setImageResource(R.drawable.ic_baseline_settings_bluetooth_24);
                fab.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // Make an intent to open the settings activity.
                        Intent i = new Intent(TelemetryView.this, TelemetrySettings.class);
                        startActivity(i);
                    }
                });
            }
        }
    }


    */
    void beginListenForData()
    {
        final Handler handler = new Handler();
        final byte startByte1 = 0x4D; //MPM Status
        final byte startByte2 = 0x50; //MPM Telemetry

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        telemetryMessage = new byte[1024];
        workerThread = new Thread(new Runnable()
        {
            public void run()
            {
                while(!Thread.currentThread().isInterrupted() && !stopWorker)
                {
                    try
                    {
                        int bytesAvailable = btInputStream.available();
                        if (bytesAvailable > 0) {
                            packetCount++;
                            totalLength += bytesAvailable;
                            avgLength = totalLength / packetCount;
                            if (bytesAvailable > maxLength)
                                maxLength = bytesAvailable;
                            if (bytesAvailable < minLength)
                                minLength = bytesAvailable;

                            byte[] packetBytes = new byte[bytesAvailable];
                            btInputStream.read(packetBytes);
                            //Move to start of valid packet in case of picking one up mid-way
                            int bytePos = 0;
                            while (bytesAvailable > bytePos + 1 && packetBytes[bytePos] != startByte1 && packetBytes[bytePos + 1] != startByte2)
                                bytePos++;
                            if (bytePos > 0)
                                shiftedCount++;
                            //                                                   Startpos  Packet Length            MP header
                            if (bytesAvailable > bytePos + 3 && bytesAvailable > (bytePos + packetBytes[bytePos + 3] + 3)) {
                                goodPacketCount++;
                                final byte messageType = packetBytes[bytePos + 2];
                                final int messageLength = packetBytes[bytePos + 3];
                                System.arraycopy(packetBytes, bytePos + 4, telemetryMessage, 0, messageLength);
                                handler.post(new Runnable() {
                                    public void run() {
                                        processTelemetryMessage(messageType, messageLength, telemetryMessage);
                                    }
                                });
                            } else {
                                shortPacketCount++;
                            }
                        }
                    }
                    catch (IOException ex)
                    {
                        stopWorker = true;
                        errorMessage = ex.getLocalizedMessage();
                        handler.post(new Runnable()
                        {
                            public void run()
                            {
                                valueRawData.setText(errorMessage);
                            }
                        });
                    }
                }
            }
        });

        workerThread.start();
    }

    void processTelemetryMessage(int msgType, int msgLength, byte[] msgData)
    {
        // Check the Multiprotocol Message type
        switch (msgType) {
            case 1:
                // Multi telemetry status
                if (msgLength == 24) {
                    //currentProtocol = processMultiStatus(msgData);
                    processMultiStatus(msgData);
                    rawData="Multi Telemetry Packet\n";
                }
                //else if (msgLength==5)
                // Multistatus without MULTI_NAMES
                break;
            case 3:
                // HUB telemetry
                if (msgLength==9) {
                    processHubTelemetry(msgData, (currentProtocol.contentEquals("FrSky D")));
                    rawData = "Hub Telemetry Packet\n";
                }

                break;
            default:
                //unknown
                rawData="Unknown Packet\n";

        }
        //Show Raw Data
        rawData+="Total Packets " + packetCount + "\n";
        rawData+="Good Packets " + goodPacketCount + "\n";
        rawData+="Shifted Packets " + shiftedCount + "\n";
        rawData+="Short Packets " + shortPacketCount + "\n";
        rawData+="Avg Packet Length " + avgLength + " (" + minLength + ", " + maxLength + ")";
        valueRawData.setText(rawData);
    }

    //private String processMultiStatus(byte[] msgData)
    private void processMultiStatus(byte[] msgData)
    {
        byte[] protBytes = new byte[7];
        byte[] subProtBytes = new byte[8];
        //String protocol, subProtocol, protocolString;

        System.arraycopy(msgData, 8, protBytes, 0, 7);
        try {
            currentProtocol = new String(protBytes, "US-ASCII");
        }
        catch(IOException ex) {
            currentProtocol = "---";
        }
        System.arraycopy(msgData, 16, subProtBytes, 0, 8);
        try {
            currentSubProtocol = new String(subProtBytes, "US-ASCII");
        }
        catch(IOException ex) {
            currentSubProtocol = "---";
        }

        updateUI();

        //protocolString = protocol + " / " + subProtocol;
        //mpmStatus.setText(protocolString);
        //return protocol;
    }

    private void processHubTelemetry(byte[] msgData, boolean isFrskyD)
    {
        float A1, A2;
        //int RSSI, TX_RSSI, LQI, TX_LQI;
        final byte linkFrame = (byte) 0xFE;
        final byte userFrame = (byte) 0xFD;
        boolean telemetryWarning=false;

        switch (msgData[0])
        {
            case linkFrame:
                // get values
                if (isFrskyD) {
                    A1 = (float)(msgData[1] & 0xff) / 20;
                    A2 = (float)(msgData[2] & 0xff) / 20;
                }
                else {
                    A1 = (float)(msgData[1] & 0xff) / 50;
                    A2 = (float)(msgData[2] & 0xff) / 50;
                }
                V1 = A1;
                V2 = A2;
                RSSI = msgData[3];
                TX_RSSI = msgData[4]>>1;
                LQI = msgData[5];
                TX_LQI = msgData[6];

                updateUI();
                processAlarms();

                break;
            case userFrame:
                // to do
                break;
        }
    }

    private void updateUI()
    {
        /*//Visual Elements
        TextView mpmStatus, valueV1, valueV2, valueRSSI, valueTXRSSI, valueLQI, valueTXLQI, valueRawData;
        //mpmStatus, valueV1, valueV2, valueRSSI, valueTXRSSI, valueLQI, valueTXLQI
        mpmStatus = (TextView) findViewById(R.id.mpmStatus);
        valueV1 = (TextView) findViewById(R.id.valueV1);
        valueV2 = (TextView) findViewById(R.id.valueV2);
        valueRSSI = (TextView) findViewById(R.id.valueRSSI);
        valueTXRSSI = (TextView) findViewById(R.id.valueTXRSSI);
        valueLQI = (TextView) findViewById(R.id.valueLQI);
        valueTXLQI = (TextView) findViewById(R.id.valueTXLQI);*/

        mpmStatus.setText(currentProtocol + " / " + currentSubProtocol );
        valueV1.setText(String.valueOf(V1) + "v");
        valueV2.setText(String.valueOf(V2) + "v");
        valueRSSI.setText(String.valueOf(RSSI) + "db");
        valueTXRSSI.setText(String.valueOf(TX_RSSI) + "db");
        valueLQI.setText(String.valueOf(LQI) + "%");
        valueTXLQI.setText(String.valueOf(TX_LQI) + "%");
    }

    private void processAlarms()
    {
        if (alarmV1Enabled && V1 < alarmV1Level && System.currentTimeMillis() - alarmV1Issued > alarmInterval)
        {
            alarmV1Issued = System.currentTimeMillis();
            Say("Voltage 1,,, " + String.format("%.2f", V1) + " volt", true);
        }
        if (alarmV2Enabled && V2 < alarmV2Level && System.currentTimeMillis() - alarmV2Issued > alarmInterval)
        {
            alarmV2Issued = System.currentTimeMillis();
            Say("Voltage 2,,, " + String.format("%.2f", V2) + " volt", true);
        }
        if (alarmRssiEnabled && RSSI < alarmRssiLevel && System.currentTimeMillis() - alarmRSSIIssued > alarmInterval)
        {
            alarmRSSIIssued = System.currentTimeMillis();
            Say("R.S.S.I,,, " + RSSI + " decibels", true);
        }
        if (alarmTxRssiEnabled && TX_RSSI < alarmTxRssiLevel && System.currentTimeMillis() - alarmTXRSSIIssued > alarmInterval)
        {
            alarmTXRSSIIssued = System.currentTimeMillis();
            Say("Telemetry R.S.S.I,,, " + TX_RSSI + " decibels", true);
        }
        if (alarmLqiEnabled && LQI < alarmLqiLevel && System.currentTimeMillis() - alarmLQIIssued > alarmInterval)
        {
            alarmLQIIssued = System.currentTimeMillis();
            Say("Link Quality,,, " + LQI + " percent", true);
        }
        if (alarmTxLqiEnabled && TX_LQI < alarmTxLqiLevel && System.currentTimeMillis() - alarmTXLQIIssued > alarmInterval)
        {
            alarmTXLQIIssued = System.currentTimeMillis();
            Say("Telemetry Link Quality,,, " + TX_LQI + " percent", true);
        }
    }

    //Convert hex data to string
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    // fast way to call Toast
    private void msg(String s)
    {
        Toast.makeText(getApplicationContext(),s,Toast.LENGTH_LONG).show();
    }

    private void Say(String s, boolean queue) {
        // TODO Auto-generated method stub
        if(s!=null&&!"".equals(s)) {
            if (!queue) {
                if (tts.isSpeaking())
                    tts.stop();
                tts.speak(s, TextToSpeech.QUEUE_FLUSH, null);
            }
            else
                tts.speak(s,TextToSpeech.QUEUE_ADD,null);
        }
    }

    private void DisconnectBT()
    {
        if (btSocket!=null) //If the btSocket is busy
        {
            try {
                btSocket.close(); //close connection
                btSocket = null;
            } catch (IOException e) {
                msg(("Error:" + e.getLocalizedMessage()));
            }
            SetConnectButton();
        }
        //finish(); //return to the first layout
    }

    public class ConnectBT extends AsyncTask<Void, Void, Void>  // UI thread
    {
        private boolean ConnectSuccess = true; //if it's here, it's almost connected

        @Override
        protected void onPreExecute()
        {
            progress = ProgressDialog.show(TelemetryView.this, "Connecting...", "Please wait");  //show a progress dialog
        }

        @Override
        protected Void doInBackground(Void... devices) //while the progress dialog is shown, the connection is done in background
        {
            try
            {
                if (btSocket == null || !isBtConnected)
                {
                    myBluetooth = BluetoothAdapter.getDefaultAdapter();//get the mobile bluetooth device
                    BluetoothDevice multiprotocolBT = myBluetooth.getRemoteDevice(address);//connects to the device's address and checks if it's available
                    btSocket = multiprotocolBT.createInsecureRfcommSocketToServiceRecord(myUUID);//create a RFCOMM (SPP) connection
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    btSocket.connect();//start connection
                }
            }
            catch (IOException e)
            {
                ConnectSuccess = false;//if the try failed, you can check the exception here
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) //after the doInBackground, it checks if everything went fine
        {
            super.onPostExecute(result);

            if (!ConnectSuccess)
            {
                msg("Connection Failed. Is the transmitter turned on?");
                //finish();
            }
            else
            {
                if (alarmConnection){
                    Say("Bluetooth Connected",false);
                }
                msg("Connected.");
                isBtConnected = true;
                ConnectedAddress = address;
                SetConnectButton();

                try {
                    btInputStream = btSocket.getInputStream();
                    beginListenForData();
                } catch (IOException e) {
                    msg(("Error reading from device:" + e.getLocalizedMessage()));
                }
            }
            progress.dismiss();
        }
    }

    public class BluetoothLostReceiver extends BroadcastReceiver {

        AppCompatActivity main = null;

        public void setMainActivity(AppCompatActivity main)
        {
            this.main = main;
        }

        @Override
        public void onReceive(Context context, Intent intent) {

            if(BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction()) && btSocket!=null)
            {
                if(alarmConnection){
                    Say("Bluetooth connection lost", false);
                }
                DisconnectBT();
                isBtConnected = false;
                msg("Bluetooth Disconnected, retrying connection");
                if(address != "") //If the currently connected)
                    new TelemetryView.ConnectBT().execute();
            }
        }
    }
}