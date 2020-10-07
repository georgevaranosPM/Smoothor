package com.reborntales.smoothor;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.ToggleButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TrackActivity extends AppCompatActivity {
    public float DEFAULT_VALUE = 0;
    public int SENSITIVITY_VALUE = 7;
    double BAD_VALUE = 0;
    private static final String FILE_EXT = ".geojson";

    private SensorManager sensorManager;
    private Sensor sensor;
    private ArrayList<GyroData> gyroDataList = new ArrayList<>();
    private ArrayList<Float> maxGyroDataList = new ArrayList<>();
    private long nowTime = System.currentTimeMillis();
    private int timePassed = 0;
    private boolean done = false;
    private float x = 0.002f, y = 0.002f, z = 0.002f;
    private int currentRoadValue = 0;
    private ArrayList<CoordinatesData> curCoordinates = new ArrayList<>();
    private ArrayList<CoordinatesData> potholesList = new ArrayList<>();
    private ImageView carImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_track);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        showInfo();
        retrieveValue();

        final SeekBar sensitivityBar = findViewById(R.id.seekBar);
        final ProgressBar rightTracking = findViewById(R.id.rightTrackingBar);
        final ProgressBar leftTracking = findViewById(R.id.leftTrackingBar);
        ToggleButton startStopBtn = findViewById(R.id.start_stop);
        carImage = findViewById(R.id.carImageView);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED);

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        LocationListener locationListener = new MyLocationListener();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(TrackActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    1);
            ActivityCompat.requestPermissions(TrackActivity.this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    2);

            return;
        }
        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 10000, 100, locationListener);

        sensitivityBar.setProgress(SENSITIVITY_VALUE);
        sensitivityBar.setMax(10);
        sensitivityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(progress==0) {
                    sensitivityBar.incrementProgressBy(1);
                }
                SENSITIVITY_VALUE = progress;
                if (SENSITIVITY_VALUE == 0) {
                    SENSITIVITY_VALUE++;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        startStopBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    rightTracking.setVisibility(View.VISIBLE);
                    leftTracking.setVisibility(View.VISIBLE);
                    sensorManager.registerListener(gyroListener, sensor, SensorManager.SENSOR_DELAY_NORMAL);
                    sensitivityBar.setEnabled(false);
                    done = false;
                } else {
                    rightTracking.setVisibility(View.INVISIBLE);
                    leftTracking.setVisibility(View.INVISIBLE);
                    sensitivityBar.setEnabled(true);
                    done = true;
                }
            }
        });
    }

    private void showInfo() {
        new AlertDialog.Builder(TrackActivity.this)
                .setTitle("Instructions")
                .setMessage("1. Make sure your GPS in enabled.\n\n2. Adjust the sensitivity based on your vehicle's standards.\n\n3. Place your phone in a stable position.\n\n3. Make sure you are in an open space in order to be able to track you!")
                .setPositiveButton(android.R.string.yes, null)
                .show();
    }

    private class MyLocationListener implements LocationListener {

        @Override
        public void onLocationChanged(Location loc) {
            curCoordinates.add(new CoordinatesData(loc.getLatitude(), loc.getLongitude()));
        }

        @Override
        public void onProviderDisabled(String provider) {}

        @Override
        public void onProviderEnabled(String provider) {}

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}
    }

    public SensorEventListener gyroListener = new SensorEventListener() {

        public void onAccuracyChanged(Sensor sensor, int acc) {
        }

        public void onSensorChanged(SensorEvent event) {
            if (!done) {
                //Calculate time passed
                timePassed = timePassed + (int) (System.currentTimeMillis() - nowTime);
                nowTime = System.currentTimeMillis();

                //Calculate rotation values
                x = Math.abs(Math.abs(event.values[0]) - x);
                y = Math.abs(Math.abs(event.values[1]) - y);
                z = Math.abs(Math.abs(event.values[2]) - z);

                if(detectPothole(x,y,z) && !curCoordinates.isEmpty()) {
                    potholesList.add(new CoordinatesData(curCoordinates.get(curCoordinates.size()-1).getLatitude(), curCoordinates.get(curCoordinates.size()-1).getLongitude()));
                }

                if (timePassed >= 100) {
                    gyroDataList.add(new GyroData(timePassed, x, y, z));
                    BAD_VALUE = (float) 1 / (SENSITIVITY_VALUE*2);

                    if (((x + y + z) / 3) >= (DEFAULT_VALUE + BAD_VALUE)) {
                        carImage.setColorFilter(Color.RED);
                    } else if (((x + y + z) / 3) > (DEFAULT_VALUE + ((DEFAULT_VALUE + BAD_VALUE) / 2)) && ((x + y + z) / 3) < (DEFAULT_VALUE + BAD_VALUE)) {
                        carImage.setColorFilter(Color.YELLOW);
                    } else if (((x + y + z) / 3) <= (DEFAULT_VALUE + ((DEFAULT_VALUE + BAD_VALUE) / 2))) {
                        carImage.setColorFilter(Color.GREEN);
                    }
                    timePassed = 0;
                }
            }
            else {
                addingMax();
                BAD_VALUE = (float) 1 / SENSITIVITY_VALUE*2;
                if (calculateAvg(maxGyroDataList) >= (DEFAULT_VALUE + BAD_VALUE)) {
                    currentRoadValue = 3;
                } else if (calculateAvg(maxGyroDataList) > (DEFAULT_VALUE + ((DEFAULT_VALUE + BAD_VALUE) / 2)) && calculateAvg(maxGyroDataList) < (DEFAULT_VALUE + BAD_VALUE)) {
                    currentRoadValue = 2;
                } else if (calculateAvg(maxGyroDataList) <= (DEFAULT_VALUE + ((DEFAULT_VALUE + BAD_VALUE) / 2))) {
                    currentRoadValue = 1;
                }
                if (curCoordinates.size() >= 1) {
                    try {
                        WriteJson();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    };

    private boolean detectPothole(float dx, float dy, float dz) {
        return ((dx + dy + dz) / 3) >= (DEFAULT_VALUE + BAD_VALUE);
    }

    public void addingMax() {
        for(int i=0; i<gyroDataList.size(); i++) {
            if((gyroDataList.get(i).getxRot()>=gyroDataList.get(i).getyRot()) && (gyroDataList.get(i).getxRot()>=gyroDataList.get(i).getzRot())) {
                if(i>3) {
                    if (!((gyroDataList.get(i - 1).getxRot() >= gyroDataList.get(i - 1).getyRot()) && (gyroDataList.get(i - 1).getxRot() >= gyroDataList.get(i - 1).getzRot())) &&
                            !((gyroDataList.get(i - 2).getxRot() >= gyroDataList.get(i - 2).getyRot()) && (gyroDataList.get(i - 2).getxRot() >= gyroDataList.get(i - 2).getzRot()))) {
                        maxGyroDataList.add(gyroDataList.get(i).getxRot());
                    } else {
                        System.out.println("TURN");
                    }
                }
            }
            else if((gyroDataList.get(i).getyRot()>=gyroDataList.get(i).getxRot()) && (gyroDataList.get(i).getyRot()>gyroDataList.get(i).getzRot())) {
                if(i>3) {
                    if (!((gyroDataList.get(i - 1).getyRot() >= gyroDataList.get(i - 1).getxRot()) && (gyroDataList.get(i - 1).getyRot() >= gyroDataList.get(i - 1).getzRot())) &&
                            !((gyroDataList.get(i - 2).getyRot() >= gyroDataList.get(i - 2).getxRot()) && (gyroDataList.get(i - 2).getyRot() >= gyroDataList.get(i - 2).getzRot()))) {
                        maxGyroDataList.add(gyroDataList.get(i).getyRot());
                    } else {
                        System.out.println("TURN");
                    }
                }
            }
            else if((gyroDataList.get(i).getzRot()>=gyroDataList.get(i).getyRot()) && (gyroDataList.get(i).getzRot()>=gyroDataList.get(i).getxRot())) {
                if(i>3) {
                    if (!((gyroDataList.get(i - 1).getzRot() >= gyroDataList.get(i - 1).getyRot()) && (gyroDataList.get(i - 1).getzRot() >= gyroDataList.get(i - 1).getxRot())) &&
                            !((gyroDataList.get(i - 2).getzRot() >= gyroDataList.get(i - 2).getyRot()) && (gyroDataList.get(i - 2).getzRot() >= gyroDataList.get(i - 2).getxRot()))) {
                        maxGyroDataList.add(gyroDataList.get(i).getzRot());
                    } else {
                        System.out.println("TURN");
                    }
                }
            }
        }
    }

    public float calculateAvg(ArrayList<Float> array) {
        float sum = 0;
        for(int i=0; i<array.size(); i++) {
            sum =+ array.get(i);
        }
        return (sum/array.size());
    }

    public void retrieveValue() {
        SharedPreferences settings = getApplicationContext().getSharedPreferences("preferences", 0);
        DEFAULT_VALUE = settings.getFloat("defaultValue", 0);
    }

    private void WriteJson() throws JSONException {
        done = false;
        if (Build.VERSION.SDK_INT >= 23) {
            int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        }

        JSONArray coordinatesArray = new JSONArray();
        for(int i=0; i<curCoordinates.size(); i++) {
            JSONArray entry = new JSONArray();
            entry.put(curCoordinates.get(i).getLongitude());
            entry.put(curCoordinates.get(i).getLatitude());
            coordinatesArray.put(entry);
        }

        JSONArray potholesArray = new JSONArray();
        for(int i=0; i<potholesList.size(); i++) {
            JSONArray potholeEntry = new JSONArray();
            potholeEntry.put(potholesList.get(i).getLongitude());
            potholeEntry.put(potholesList.get(i).getLatitude());
            potholesArray.put(potholeEntry);
        }

        JSONObject geometryObject = new JSONObject();
        geometryObject.put("type", "LineString");
        geometryObject.put("coordinates", coordinatesArray);

        JSONObject valueObject = new JSONObject();
        valueObject.put("value", currentRoadValue);

        JSONObject fillerObject = new JSONObject();
        fillerObject.put("type", "Feature");
        fillerObject.put("properties", valueObject);
        fillerObject.put("potholes", potholesArray);
        fillerObject.put("geometry", geometryObject);

        JSONArray featuresArray = new JSONArray();
        featuresArray.put(fillerObject);

        JSONObject wholeObject = new JSONObject();
        wholeObject.put("type", "FeatureCollection");
        wholeObject.put("features", featuresArray);


        try {
            Writer output;
            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "/" + returnCity(curCoordinates.get(0).getLatitude() , curCoordinates.get(0).getLongitude()) + "_" + returndate() + FILE_EXT);
            output = new BufferedWriter(new FileWriter(file));
            output.write(wholeObject.toString());
            output.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //Reset currentRoadValue, gyroDataList and curCoordinates
        currentRoadValue = 0;

        gyroDataList.clear();
        curCoordinates.clear();
    }

    private String returnCity(double latitude, double longitude) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        List<Address> addresses = null;
        try {
            addresses = geocoder.getFromLocation(latitude, longitude, 1);
        } catch (IOException e) {
            e.printStackTrace();
        }
        assert addresses != null;
        return addresses.get(0).getAddressLine(0).trim().substring(0, addresses.get(0).getAddressLine(0).indexOf(","));
    }

    private String returndate() {
        @SuppressLint("SimpleDateFormat") DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");
        Date date = new Date();
        return dateFormat.format(date);
    }
}
