package com.example.mew.gpstesting;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.StrictMode;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;
import java.util.*;
import java.text.*;

public class MainActivity extends AppCompatActivity {

    private static final int location_perm_request_code = 1;

    @SuppressLint("SimpleDateFormat")
    public static String ms_to_simpledate(long milliseconds) {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date(milliseconds));
    }

    private final LocationListener listener = new LocationListener() {
        @Override
        public void onLocationChanged(Location new_location) {
            updateLocationInfo(new_location);
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) { }

        @Override
        public void onProviderEnabled(String s) { }

        @Override
        public void onProviderDisabled(String s) { }
    };

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set main thread blocking on network calls, todo async etc. with additional features alike
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);


        // Show some UI, for debug purposes
        setContentView(R.layout.activity_main);

        // If no permission to access fine location
        if (!(PackageManager.PERMISSION_GRANTED == checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION))) {
            // Request it -> GOTO onRequestPermissionsResult
            requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, location_perm_request_code);
        } else {
            // Try to retrieve system location service
            LocationManager loc_manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (loc_manager != null) {
                attachLocationListenerToLocationManager(loc_manager, listener);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(int request_code, String[] perms, int[] request_results) {
        if (request_code == location_perm_request_code) {
            LocationManager loc_manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (loc_manager != null) {
                attachLocationListenerToLocationManager(loc_manager, listener);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void attachLocationListenerToLocationManager(LocationManager loc_manager, LocationListener listener) {
        if (PackageManager.PERMISSION_GRANTED == checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            boolean gps_enabled = loc_manager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean network_enabled = loc_manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            boolean passive_enabled = loc_manager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER);
            if (!gps_enabled || !network_enabled || !passive_enabled) {
                // Open config menu
                startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
            // Check last known location via most accurate -> least accurate provider
            Location last_gps_location = loc_manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location last_network_location = loc_manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            Location last_passive_location = loc_manager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            if (last_gps_location != null) {
                updateLocationInfo(last_gps_location);
            } else if (last_network_location != null) {
                updateLocationInfo(last_network_location);
            } else if (last_passive_location != null) {
                updateLocationInfo(last_passive_location);
            }
            loc_manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 0, listener);
            loc_manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 0, listener);
            loc_manager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 5000, 0, listener);
        }
    }

    private void updateLocationInfo(Location location) {
        String geocoded_address_locality = "Unavailable";
        try {
            Geocoder gcd = new Geocoder(getBaseContext(), Locale.getDefault());
            List<Address> addresses = gcd.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses.size() > 0) {
                geocoded_address_locality = addresses.get(0).getLocality();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        HttpURLConnection conn = null;
        try {
            URL url = new URL(BuildConfig.LOCATION_API);
            Map<String,Object> params = new LinkedHashMap<>();
            params.put("actor", "mii");
            params.put("registertime", String.valueOf((int)Math.floor(location.getTime()/1000)));
            params.put("providername", location.getProvider());
            params.put("accuracy_m", String.valueOf(location.getAccuracy()));
            params.put("lat", String.valueOf(location.getLatitude()));
            params.put("lon", String.valueOf(location.getLongitude()));
            params.put("location", geocoded_address_locality);
            params.put("speed_m_s", String.valueOf(location.getSpeed()));

            StringBuilder postdata = new StringBuilder();
            for (Map.Entry<String,Object> param : params.entrySet()) {
                if (postdata.length() != 0) postdata.append('&');
                postdata.append(URLEncoder.encode(param.getKey(), "UTF-8"));
                postdata.append('=');
                postdata.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
            }
            byte[] postdata_bytes = postdata.toString().getBytes("UTF-8");

            conn = (HttpURLConnection)url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Content-Length", String.valueOf(postdata_bytes.length));
            conn.setDoOutput(true);
            conn.getOutputStream().write(postdata_bytes);
            Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int c; (c = in.read()) >= 0;)
                sb.append((char)c);
            String response = sb.toString();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        TextView text = findViewById(R.id.headerTextView);
        // Go around the "use i18n placeholders" check using this... prefer not to suppress the warn
        String s = "Updated " +ms_to_simpledate(location.getTime());
        text.setText(s);
    }
}