package edu.umd.outdoorwalking;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import edu.umd.cmsc436.frontendhelper.TrialMode;
import edu.umd.cmsc436.sheets.Sheets;

public class MainActivity extends AppCompatActivity implements LocationListener, OnMapReadyCallback, Sheets.Host {

    public static final int LIB_ACCOUNT_NAME_REQUEST_CODE = 1001;
    public static final int LIB_AUTHORIZATION_REQUEST_CODE = 1002;
    public static final int LIB_PERMISSION_REQUEST_CODE = 1003;
    public static final int LIB_PLAY_SERVICES_REQUEST_CODE = 1004;
    public static final int LIB_CONNECTION_REQUEST_CODE = 1005;
    private static final int MIN = 0;
    private static final int MAX = 100;
    private final int REQUEST = 1;

    private Sheets sheet;

    //Location Variables
    private LocationManager manager;
    private Location previousLocation;
    private LatLng startLatLng, endLatLng;
    private Set<Polyline> lineSet;
    private String provider;
    private Marker startMarker, mCurrLocationMarker;
    private ArrayList<Marker> markers = new ArrayList<>();

    //Keep track of on/off status
    private boolean running;

    //Map Stuff
    private GoogleMap map;
    private UiSettings uiSettings;


    //Movement Variables
    private long startTime, endTime;
    private final long MIN_TIME = 5000;
    private final float MIN_DIST = 10;
    private float mps = 0.00f;
    private float dist = 0.00f;

    private GoogleMap.SnapshotReadyCallback callback;

    String[] permissions= new String[]{
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sheet = new Sheets(this, this, "Outdoor Walking Test",
                getString(R.string.CMSC436_central_spreadsheet), getString(R.string.CMSC436_private_test_spreadsheet));

        MapFragment mapFragment = (MapFragment)getFragmentManager().findFragmentById(R.id.map_fragment);
        mapFragment.getMapAsync(this);
        lineSet = new HashSet<>();
        previousLocation = null;
        manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        final Criteria criteria = new Criteria();

        provider = manager.getBestProvider(criteria, true);
        running = false;
        mCurrLocationMarker = null;

        hasPermissions(this, permissions);
        if(!hasPermissions(this, permissions)){
            ActivityCompat.requestPermissions(this, permissions, REQUEST);
        }

        findViewById(R.id.endWalk).setEnabled(false);
        findViewById(R.id.startWalk).setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                running = true;
                findViewById(R.id.startWalk).setEnabled(false);
                findViewById(R.id.endWalk).setEnabled(true);

                mps = 0.00f;
                dist = 0.00f;
                startTime = SystemClock.elapsedRealtime();

                if (ActivityCompat.checkSelfPermission(getBaseContext(), Manifest.permission.ACCESS_FINE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.checkSelfPermission(getBaseContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                                != PackageManager.PERMISSION_GRANTED) {
                    return;
                }

                Location loc = manager.getLastKnownLocation(provider);
                if (loc != null) {
                    onLocationChanged(loc);
                }
                for(Polyline p : lineSet){
                    p.setVisible(false);
                }
                manager.requestLocationUpdates(provider,MIN_TIME,MIN_DIST,MainActivity.this);

                LatLng latLng = new LatLng(loc.getLatitude(), loc.getLongitude());
                MarkerOptions markerOptions = new MarkerOptions();
                markerOptions.position(latLng);
                markerOptions.title("Curr Pos");
                markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE));
                if(startMarker != null) {
                    startMarker.remove();
                    startMarker = null;
                    markers = new ArrayList<>();
                }
                startMarker = map.addMarker(new MarkerOptions().position(latLng).title("Start"));
                startMarker.setIcon(BitmapDescriptorFactory
                        .defaultMarker(BitmapDescriptorFactory.HUE_ORANGE));
                Toast startToast = Toast.makeText(getApplicationContext(),
                        "Start!", Toast.LENGTH_SHORT);
                startToast.show();
                markers.add(startMarker);
            }
        });


        callback = new GoogleMap.SnapshotReadyCallback() {
            Bitmap bitmap;

            @Override
            public void onSnapshotReady(Bitmap snapshot) {
                bitmap = snapshot;

                String imgSaved = MediaStore.Images.Media.insertImage(
                        getContentResolver(),
                        bitmap,
                        UUID.randomUUID().toString()+".png",
                        "drawing");

                //sheet.uploadToDrive(getString(R.string.CMSC436_test_folder), "image", bitmap);

                //sheet.uploadToDrive("0B3B-mQ6gORpBYzNjUC1icGNQOTA", "image", bitmap);

                if(imgSaved!=null){
                    //Database.getInstance().addImage(bitmap);
                    Toast savedToast = Toast.makeText(getApplicationContext(),
                            "Saved locally.", Toast.LENGTH_SHORT);
                    //savedToast.show();
                } else{
                    Toast unsavedToast = Toast.makeText(getApplicationContext(),
                            "Failed.", Toast.LENGTH_SHORT);
                    unsavedToast.show();
                }

            }
        };

        findViewById(R.id.endWalk).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                running = false;
                findViewById(R.id.startWalk).setEnabled(true);
                findViewById(R.id.endWalk).setEnabled(false);

                endTime = SystemClock.elapsedRealtime();
                //long elapsedMilli = endTime-startTime;
                double realTime = (endTime-startTime)/1000.0;
                mps = dist/(float)realTime;
                //Database.getInstance().addOutdoorWalkTest(new MainActivity(elapsedMilli/1000, dist, mps));
                Toast numToast = Toast.makeText(getApplicationContext(),
                        "Avg. Speed of " + mps + "m/s for " + String.valueOf(realTime) + " seconds.", Toast.LENGTH_SHORT);
                numToast.show();

                //float[] trial = {mps, 0.5f};
                //sheet.writeTrials(Sheets.TestType.OUTDOOR_WALKING, "test", trial);

                Intent data = new Intent();
                data.putExtra("score", mps);
                setResult(MainActivity.RESULT_OK, data);

                //sheet = new Sheets(MainActivity.this, MainActivity.this, getString(R.string.app_name), getString(R.string.CMSC436_testing_spreadsheet), getString(R.string.CMSC436_private_test_spreadsheet));
                //sheet.writeData(Sheets.TestType.OUTDOOR_WALKING, "demo", mps);
                //manager.removeUpdates(OutdoorWalkActivity.this);


                // Center Camera between the two points
                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                for (Marker marker : markers) {
                    builder.include(marker.getPosition());
                }
                LatLngBounds bounds = builder.build();
                int padding = 75; // Padding between marker and edges of the map
                CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);
                map.moveCamera(cu);
                mCurrLocationMarker.setIcon(BitmapDescriptorFactory
                        .defaultMarker(BitmapDescriptorFactory.HUE_ORANGE));
                map.snapshot(callback);
                finish();
            }
        });

    }

    @Override
    public void onBackPressed() {
        Intent data = new Intent();
        data.putExtra("score",(float)0);
        setResult(MainActivity.RESULT_CANCELED,data);
        finish();
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult (int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        this.sheet.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        this.sheet.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public int getRequestCode(Sheets.Action action) {
        switch (action) {
            case REQUEST_ACCOUNT_NAME:
                return LIB_ACCOUNT_NAME_REQUEST_CODE;
            case REQUEST_AUTHORIZATION:
                return LIB_AUTHORIZATION_REQUEST_CODE;
            case REQUEST_PERMISSIONS:
                return LIB_PERMISSION_REQUEST_CODE;
            case REQUEST_PLAY_SERVICES:
                return LIB_PLAY_SERVICES_REQUEST_CODE;
            case REQUEST_CONNECTION_RESOLUTION:
                return LIB_CONNECTION_REQUEST_CODE;
            default:
                return -1;
        }
    }

    @Override
    public void notifyFinished(Exception e) {
        if (e != null) {
            throw new RuntimeException(e);
        }

        finish();
    }

    @Override
    public void onLocationChanged(Location location) {
        LatLng prevLatLng;

        if(previousLocation == null){
            previousLocation = location;
            return;
        }else{
            dist += previousLocation.distanceTo(location);
            prevLatLng = new LatLng(previousLocation.getLatitude(), previousLocation.getLongitude());
            previousLocation = location;
        }

        if (mCurrLocationMarker != null) {
            mCurrLocationMarker.remove();
        }

        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        endLatLng = latLng;
        Polyline line = map.addPolyline(new PolylineOptions()
                .add(prevLatLng, latLng)
                .width(5)
                .color(Color.RED));
        lineSet.add(line);
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(latLng);
        markerOptions.title("Current Position");
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE));
        mCurrLocationMarker = map.addMarker(markerOptions);

        markers.add(mCurrLocationMarker);

        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 18);
        map.moveCamera(cameraUpdate);
    }


    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    protected void onResume() {
        super.onResume();
        if(running) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                    PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            Location loc = manager.getLastKnownLocation(provider);

            if (loc != null) {
                onLocationChanged(loc);
            }
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Location location = manager.getLastKnownLocation(provider);
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(latLng);
        markerOptions.title("Current Position");
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE));

        startMarker = map.addMarker(new MarkerOptions().position(latLng).title("Start"));
        startMarker.setIcon(BitmapDescriptorFactory
                .defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
        markers.add(startMarker);

        mCurrLocationMarker = map.addMarker(markerOptions);
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 16);
        startLatLng = latLng;
        map.moveCamera(cameraUpdate);

        uiSettings = map.getUiSettings();
        uiSettings.setAllGesturesEnabled(false);
    }
}
