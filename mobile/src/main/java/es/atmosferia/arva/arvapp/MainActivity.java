package es.atmosferia.arva.arvapp;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.internal.StreetViewLifecycleDelegate;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    // Private constants //
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int BLUETOOTH_SEARCH_DEVICE = 2;
    private static final int RECIEVE_MESSAGE = 3;

    //ARVA mode variable, 1 --> TX // 2 --> RX
    private int mode = 1;
    private static int fragmentToDisplay = 0;

    // Location variables //
    private double ourArvaLatitude = 0;
    private double ourArvaLongitude = 0;
    private double ourArvaAltitude = 0;

    private double receivingArvaLatitude = 0;
    private double receivingArvaLongitude = 0;

    private float bearing = 0;
    private float distance = 0;

    private LocationListener locationListener;

    // Orientation sensor variables
    private SensorManager mSensorManager;
    private float firstOrientation = -1;
    private float secondOrientation = -1;
    private float orientation = -1;

    //Bluetooth dependant variables
    private Handler h;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothSocket btSocket = null;
    private StringBuilder sb = new StringBuilder();

    private BluetoothAdapter myBA = BluetoothAdapter.getDefaultAdapter();
    private BluetoothDevice connectedDevice = null;
    private ConnectedThread mConnectedThread;

    //Fragment related variables
    private SectionsPagerAdapter mSectionsPagerAdapter;
    private ViewPager mViewPager;

    private ImageView arrow = null;
    private TextView distanceTextView = null;
    private TextView latitude_info = null, longitude_info = null, altitude_info = null;

    // function: onCreate -> Instantiates the important variables and listeners
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Creation of the toolbar (for appCompat)
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Orientation Sensor
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        // Fragments related
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
        mViewPager = (ViewPager) findViewById(R.id.container);
        assert mViewPager != null;
        mViewPager.setAdapter(mSectionsPagerAdapter);

        // Listeners
        setListeners();

        // Enable bluetooth
        if(myBA == null){
            Toast.makeText(this, "Bluetooth not supportted", Toast.LENGTH_LONG).show();
            finish();
        }
        else{
            if(!myBA.isEnabled()){
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }


    }

    //function: onActivityResult -> Evaluates the requestCode and do appropriate thigs with the received data
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        switch(requestCode){
            case REQUEST_ENABLE_BT:
                switch(resultCode){
                    case RESULT_CANCELED:
                        Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_LONG).show();
                        break;
                    case RESULT_OK:
                        Toast.makeText(this, "Bluetooth enabled and ready!", Toast.LENGTH_LONG).show();
                        break;
                    default:
                        Toast.makeText(this, "Something gone wrong while enabling bluetooth, try again please", Toast.LENGTH_LONG).show();
                        break;
                }
                break;
            case BLUETOOTH_SEARCH_DEVICE:
                switch(resultCode){
                    case RESULT_CANCELED:
                        Toast.makeText(this, "You must link a ARVA device to proceed", Toast.LENGTH_LONG).show();
                        break;
                    case RESULT_OK:
                        //desactivar botó i ficar la imatge en fletxa
                        Bundle bundle = data.getExtras();
                        connectedDevice = bundle.getParcelable("BluetoothDevice");
                        fragmentToDisplay = mode;
                        Toast.makeText(this, "Succeeded!", Toast.LENGTH_LONG).show();
                        break;
                    default:
                        Toast.makeText(this, "Something gone wrong while enabling bluetooth, try again please", Toast.LENGTH_LONG).show();
                        break;
                }
        }
    }

    // function: onCreateOptionsMenu -> spawns the options in the menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    // function: onOptionsItemSelected -> sets the listeners for the menu options
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // function: onResume -> Tries to create the bluetooth socket and registers the orientation sensor listener
    @Override
    public void onResume() {
        super.onResume();

        BluetoothDevice device = connectedDevice;
        if(device != null){
            try {
                btSocket = createBluetoothSocket(device);
            } catch (IOException e) {
                Toast.makeText(this, "Error, failed to resume the activity", Toast.LENGTH_LONG).show();
                finish();
            }
            myBA.cancelDiscovery();

            // Establish the connection.  This will block until it connects.
            Log.i("connect", "...Connecting...");
            try {
                btSocket.connect();
                Log.i("ok", "....Connection ok...");
            } catch (IOException e) {
                try {
                    btSocket.close();
                } catch (IOException e2) {
                    Toast.makeText(this, "Error, failed to resume the activity", Toast.LENGTH_LONG).show();
                    finish();
                }
            }

            Log.i("create socket", "...Create Socket...");
            mConnectedThread = new ConnectedThread(btSocket);
            mConnectedThread.start();
        }

        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),
                SensorManager.SENSOR_DELAY_GAME);
    }

    // function: onPause -> closes the bluetooth socket
    @Override
    public void onPause() {
        super.onPause();

        if(connectedDevice != null){
            try {
                btSocket.close();
            } catch (IOException e) {
                Toast.makeText(this, "Error, failed to pause the activity", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    // function: onSensorChanged -> When sensor changes do appropriate things with data
    @Override
    public void onSensorChanged(SensorEvent event) {
        if(firstOrientation == -1){
            firstOrientation = Math.round(event.values[0]);
            secondOrientation = -1;
        }
        else {
            secondOrientation = Math.round(event.values[0]);
        }

        updateArrow();
    }

    // function onAccuracyChanged -> not in use
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // not in use
    }

    /* ---------------------------------------------------------------------------------------------
                                            Private classes

     ---------------------------------------------------------------------------------------------*/
    private class MyLocationListener implements LocationListener {

        @Override
        public void onLocationChanged(Location loc) {
            ourArvaLatitude = loc.getLatitude();
            ourArvaLongitude = loc.getLongitude();
            ourArvaAltitude = loc.getAltitude();
            if (mConnectedThread != null){
                mConnectedThread.write(String.valueOf(ourArvaLatitude) + ":" + String.valueOf(ourArvaLongitude));
                if(arrow != null && distanceTextView != null){
                    arrow.setVisibility(View.VISIBLE);
                    distanceTextView.setVisibility(View.VISIBLE);
                }
            }
            /*else{
                Toast.makeText(MainActivity.this, String.valueOf(receivingArvaLatitude) + String.valueOf(receivingArvaLongitude), Toast.LENGTH_LONG).show();
            }*/
            recalculate();
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle savedInstance){}

        @Override
        public void onProviderDisabled(String provider){}

        @Override
        public void onProviderEnabled(String provider){}
    }

    public static class PlaceholderFragment extends Fragment implements OnMapReadyCallback {
        private static final String ARG_SECTION_NUMBER = "section_number";

        public PlaceholderFragment() { }

        private MapView mapView = null;
        private GoogleMap mMap;

        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = null;
            switch(getArguments().getInt(ARG_SECTION_NUMBER)){
                case 1:
                    switch (fragmentToDisplay){
                        case 0:
                            //BLE case
                            rootView = inflater.inflate(R.layout.fragment_main, container, false);
                            break;
                        case 1:
                            //TX case
                            rootView = inflater.inflate(R.layout.fragment_main_tx, container, false);
                            break;
                        case 2:
                            //RX case
                            rootView = inflater.inflate(R.layout.fragment_main_rx, container, false);
                            break;
                    }
                    setCustomListeners(fragmentToDisplay, rootView);
                    break;
                case 2:
                    // TODO: fix map
                    rootView = inflater.inflate(R.layout.fragment_gps, container, false);
                    /*mapView = (MapView) rootView.findViewById(R.id.map);
                    mapView.onCreate(savedInstanceState);
                    mMap = mapView.getMap();
                    mMap.getUiSettings().setMyLocationButtonEnabled(false);
                    try{
                        mMap.setMyLocationEnabled(true);
                    }catch (SecurityException se){
                        se.printStackTrace();
                    }

                    MapsInitializer.initialize(rootView.getContext());
                    //mMap.setMyLocationEnabled(true);
                    */
                    SupportMapFragment mapFragment = (SupportMapFragment) this.getChildFragmentManager().findFragmentById(R.id.map);
                    mapFragment.getMapAsync(this);

                    break;
                case 3:
                    rootView = inflater.inflate(R.layout.fragment_stats, container, false);
                    break;
            }

            return rootView;
        }

        @Override
        public void onMapReady(GoogleMap googleMap){
            mMap = googleMap;
            LatLng sydney = new LatLng(-34, 151);
            mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
            mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));
        }
    }

    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            return PlaceholderFragment.newInstance(position + 1);
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "Main Page";
                case 1:
                    return "Map";
                case 2:
                    return "Stats";
            }
            return null;
        }
    }

    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[256];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    bytes = mmInStream.read(buffer);		// Get number of bytes and message in "buffer"
                    h.obtainMessage(RECIEVE_MESSAGE, bytes, -1, buffer).sendToTarget();		// Send to message queue Handler
                } catch (IOException e) {
                    break;
                }
            }
        }

        public void write(String message) {

            byte[] msgBuffer = message.getBytes();
            try {
                mmOutStream.write(msgBuffer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //Private function: createBluetoothSocket -> creates and connects a BluetoothSocket
    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        if(Build.VERSION.SDK_INT >= 10){
            try {
                final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[] { UUID.class });
                return (BluetoothSocket) m.invoke(device, MY_UUID);
            } catch (Exception e) {
                Log.i("SOCKET: ", "Could not create Insecure RFComm Connection",e);
            }
        }
        return  device.createRfcommSocketToServiceRecord(MY_UUID);
    }

    //Private function: recalculate -> Recalculates the direction of the arrow
    private void recalculate(){
        if(fragmentToDisplay != 2) return;

        if(ourArvaLatitude != 0 && receivingArvaLatitude != 0) {
            Location ourArva = new Location("OurArva");

            ourArva.setLatitude(ourArvaLatitude);
            ourArva.setLongitude(ourArvaLongitude);
            Location receivingArva = new Location("ReceivingArva");
            receivingArva.setLatitude(receivingArvaLatitude);
            receivingArva.setLongitude(receivingArvaLongitude);

            bearing = ourArva.bearingTo(receivingArva);
            distance = ourArva.distanceTo(receivingArva);
            Log.i("bearing", String.valueOf(bearing));
            /*if(bearing < 0){
                orientation = 360 + bearing;
            }
            else {
                orientation = 0 + bearing;
            }

            if(firstOrientation != -1){
                Log.i("1st", String.valueOf(firstOrientation));
                orientation += firstOrientation;
                if(orientation > 360){
                    orientation -= 360;
                }
                Log.i("orientation", String.valueOf(orientation));
            }*/
            orientation = (360+((bearing + 360) % 360)-firstOrientation) % 360;
        }
        else {
            firstOrientation = -1;
            secondOrientation = -1;
        }

        updateArrow();
    }

    // private function: updateArrow -> Rotates the R.id.arrow according to the different values we have
    private void updateArrow() {
        if(fragmentToDisplay != 2) return;

        arrow = (ImageView) findViewById(R.id.rx_arrow);
        distanceTextView = (TextView) findViewById(R.id.rx_distance_textview);
        latitude_info = (TextView) findViewById(R.id.latitude);
        longitude_info = (TextView) findViewById(R.id.longitude);
        altitude_info = (TextView) findViewById(R.id.altitude);

        if (arrow != null && distanceTextView != null) {
            if (firstOrientation != -1 && secondOrientation != -1) {
                if (orientation != -1) {
                    arrow.setRotation(0);
                    arrow.setRotation((orientation + (firstOrientation - secondOrientation)));
                } else {
                    arrow.setRotation(0);
                    arrow.setRotation(bearing);
                }

                if(altitude_info != null && longitude_info != null && latitude_info != null) {
                    altitude_info.setText(String.valueOf(ourArvaAltitude));
                    longitude_info.setText(String.valueOf(ourArvaLongitude));
                    latitude_info.setText(String.valueOf(ourArvaLatitude));
                }
                distanceTextView.setText(String.valueOf(distance) + " meters");
            }
        }
    }

    // private function: setListeners -> Sets the listeners for the different buttons or services
    private void setListeners(){
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationListener = new MyLocationListener();
        try{
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        }catch (SecurityException se){
            se.printStackTrace();
        }

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        assert fab != null;
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new AlertDialog.Builder(view.getContext())
                        .setIcon(R.drawable.boto_112)
                        .setTitle("Emergency Services Alert")
                        .setMessage("Proceed calling 112 for an emergency?")
                        .setNegativeButton("No", null)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                callForHelp();
                            }
                        })
                        .show();
            }
        });

        h = new Handler() {
            public void handleMessage(android.os.Message msg) {
                switch (msg.what) {
                    case RECIEVE_MESSAGE:													// if receive massage
                        byte[] readBuf = (byte[]) msg.obj;
                        String strIncom = new String(readBuf, 0, msg.arg1);					// create string from bytes array
                        sb.append(strIncom);												// append string
                        int endOfLineIndex = sb.indexOf("\r\n");							// determine the end-of-line
                        if (endOfLineIndex > 0) {                                            // if end-of-line,
                            String sbprint = sb.substring(0, endOfLineIndex);                // extract string
                            sb.delete(0, sb.length());                                        // and clear

                            boolean error = false;
                            String aux = "";
                            String packetFragments[] = null;
                            String id = null, type = null, recievedMode = null;
                            int checksum = 0;
                            try {
                                aux = sbprint.substring(0, 3);
                                //Toast.makeText(MainActivity.this, aux, Toast.LENGTH_LONG).show();
                            }catch (IndexOutOfBoundsException e){
                                e.printStackTrace();
                            }
                            if (aux.equalsIgnoreCase("DVA")){
                                //Toast.makeText(MainActivity.this, sbprint, Toast.LENGTH_LONG).show();
                                try {
                                    packetFragments = sbprint.split(";");

                                    type = packetFragments[0].substring(3, 4);
                                    recievedMode = packetFragments[0].substring(4, 5);
                                    id = packetFragments[0].substring(5, packetFragments[0].length());
                                    Log.i("ID", id);

                                    receivingArvaLatitude = Double.parseDouble(packetFragments[1]);
                                    receivingArvaLongitude = Double.parseDouble(packetFragments[2]);
                                    checksum = Integer.parseInt(String.valueOf(packetFragments[3].charAt(0)));
                                    int multiplicador = 1;
                                    for(int i = 1; i < packetFragments[3].length(); i++){
                                        checksum += Integer.parseInt(String.valueOf(packetFragments[3].charAt(i))) * (multiplicador * 10);
                                        multiplicador *= 10;
                                    }
                                } catch (IndexOutOfBoundsException | NumberFormatException e) {
                                    e.printStackTrace();
                                    error = true;
                                }

                                if (packetFragments[0].length() + packetFragments[1].length() + packetFragments[2].length() + 3 == checksum && !error) {
                                    //Toast.makeText(MainActivity.this, String.valueOf(receivingArvaLatitude) + String.valueOf(receivingArvaLongitude), Toast.LENGTH_LONG).show();
                                    recalculate();
                                    mode = Integer.parseInt(recievedMode);
                                }
                            }
                        }
                        break;
                }
            }
        };
    }

    //private function: callForHelp() -> Sends some data to 112
    private void callForHelp(){
        SmsManager smsManager = SmsManager.getDefault();
        //smsManager.sendTextMessage("112", null, "DVA Automated message! A DVA device has detected an emergency in Latitude: " + String.valueOf(ourArvaLatitude) + " longitude: " + String.valueOf(ourArvaLongitude), null, null);
        Toast.makeText(this, "Sending message to 112", Toast.LENGTH_LONG).show();
    }

    //private function: setCustomListeners -> set the appropiated listeners for each fragment in our app
    private static void setCustomListeners(int currentFragment, final View rootView){
        Button aux;
        switch (currentFragment){
            case 0:
                aux = (Button) rootView.findViewById(R.id.ble_enable_butt);
                aux.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent i = new Intent(view.getContext(), BluetoothDeviceListActivity.class);
                        ((Activity) rootView.getContext()).startActivityForResult(i, BLUETOOTH_SEARCH_DEVICE);
                    }
                });
                break;
            case 1:
                aux = (Button) rootView.findViewById(R.id.tx_chmode_butt);
                aux.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        //TODO: Complete
                        fragmentToDisplay = 2;
                    }
                });
                break;
            case 2:
                aux = (Button) rootView.findViewById(R.id.rx_chmode_butt);
                aux.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        //TODO: Complete
                        fragmentToDisplay = 1;
                    }
                });
                break;
        }
    }
}
