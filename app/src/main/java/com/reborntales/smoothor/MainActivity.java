package com.reborntales.smoothor;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Environment;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.mapbox.api.matching.v5.MapboxMapMatching;
import com.mapbox.api.matching.v5.models.MapMatchingMatching;
import com.mapbox.api.matching.v5.models.MapMatchingResponse;
import com.mapbox.core.exceptions.ServicesException;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.PolygonOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.layers.LineLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import com.mapbox.mapboxsdk.utils.ColorUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

import static android.os.Environment.getExternalStoragePublicDirectory;
import static com.mapbox.api.directions.v5.DirectionsCriteria.PROFILE_DRIVING;
import static com.mapbox.core.constants.Constants.PRECISION_6;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineOpacity;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineWidth;

public class MainActivity extends AppCompatActivity {

    private static final String FILE_EXT = ".geojson";
    private MapView mapView;
    private MapboxMap map;
    private ArrayList<Route> allRoutes = new ArrayList<>();
    private ArrayList<CoordinatesData> allPotholes = new ArrayList<>();
    public static String fileName;
    private int layersCounter = 0;
    private ListView routesList;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(this, getString(R.string.access_token));
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        final FloatingActionButton trackButton = findViewById(R.id.trackFButton);
        final FloatingActionButton calibrateButton = findViewById(R.id.calibrateFButton);
        final FloatingActionButton layerButton = findViewById(R.id.layersFButton);
        final TextView layersText = findViewById(R.id.layersText);
        final TextView calibrateText = findViewById(R.id.calibrateText);
        final TextView trackText = findViewById(R.id.trackText);
        routesList = findViewById(R.id.routeList);
        final View backgroundView = findViewById(R.id.backroundToClick);
        final ImageView memoImageView = findViewById(R.id.memoImage);
        final ImageView largeMemoImageView = findViewById(R.id.largeMemoImage);
        final ImageView zoomImageView = findViewById(R.id.zoomImage);

        if(!checkInternetState()) {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Warning!")
                    .setMessage("You are not connected to the Internet. The routes won't show up until you have Internet access.")
                    .setPositiveButton(android.R.string.yes, null)
                    .show();
        }

        allRoutes.addAll(readFiles());
        try {
            allPotholes.addAll(getPotholes());
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }

        final RouteAdapter routesAdapter = new
                RouteAdapter(MainActivity.this,
                R.layout.list_view_item,
                allRoutes);
        routesList.setAdapter(routesAdapter);

        final CameraPosition position = new CameraPosition.Builder()
                .target(new LatLng(38.8, 23.5))
                .zoom(5.5)
                .build();

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull MapboxMap mapboxMap) {
                map = mapboxMap;
                map.setCameraPosition(position);
                map.setStyle(Style.MAPBOX_STREETS, new Style.OnStyleLoaded() {
                    @Override
                    public void onStyleLoaded(@NonNull Style style) {
                        loadRoutes();
                    }
                });
            }
        });

        routesList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                routesList.setVisibility(View.INVISIBLE);
                fileName = allRoutes.get(position).getRouteName() + "_" + allRoutes.get(position).getRouteDate();
                try {
                    map.animateCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder()
                            .target(new LatLng(getPosition().getLatitude(), getPosition().getLongitude()))
                            .zoom(10)
                            .build()), 2000);
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        backgroundView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                routesList.setVisibility(View.INVISIBLE);
                return false;
            }
        });

        routesList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Delete route?")
                        .setMessage("Are you sure you want to delete this route?")
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                deleteItem(position);
                                routesList.invalidateViews();
                            }
                        })
                        .setNegativeButton(android.R.string.no, null)
                        .show();
                return true;
            }
        });

        layerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(layerButton.getScaleX()==1.1f) {
                    if(routesList.getVisibility()==View.INVISIBLE) {
                        routesList.setVisibility(View.VISIBLE);
                    }
                    else {
                        routesList.setVisibility(View.INVISIBLE);
                    }
                }
                else {
                    layerButton.setScaleX(1.1f);
                    layerButton.setScaleY(1.1f);
                    calibrateButton.setScaleX(0.8f);
                    calibrateButton.setScaleY(0.8f);
                    trackButton.setScaleX(0.8f);
                    trackButton.setScaleY(0.8f);

                    trackText.setVisibility(View.INVISIBLE);
                    calibrateText.setVisibility(View.INVISIBLE);
                    layersText.setVisibility(View.VISIBLE);
                }
            }
        });

        trackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(trackButton.getScaleX()==1.1f) {
                    startActivity(new Intent(MainActivity.this, TrackActivity.class));
                }
                else {
                    layerButton.setScaleX(0.8f);
                    layerButton.setScaleY(0.8f);
                    calibrateButton.setScaleX(0.8f);
                    calibrateButton.setScaleY(0.8f);
                    trackButton.setScaleX(1.1f);
                    trackButton.setScaleY(1.1f);

                    trackText.setVisibility(View.VISIBLE);
                    calibrateText.setVisibility(View.INVISIBLE);
                    layersText.setVisibility(View.INVISIBLE);
                }
            }
        });

        calibrateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(calibrateButton.getScaleX()==1.1f) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Careful!")
                            .setMessage("You are about to calibrate your phone for the app's needs. Leave a phone in a stable position without moving it for 30 seconds.")
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    startActivity(new Intent(MainActivity.this, CalibrateActivity.class));
                                }
                            })
                            .setNegativeButton(android.R.string.no, null)
                            .show();
                }
                else {
                    layerButton.setScaleX(0.8f);
                    layerButton.setScaleY(0.8f);
                    calibrateButton.setScaleX(1.1f);
                    calibrateButton.setScaleY(1.1f);
                    trackButton.setScaleX(0.8f);
                    trackButton.setScaleY(0.8f);

                    trackText.setVisibility(View.INVISIBLE);
                    calibrateText.setVisibility(View.VISIBLE);
                    layersText.setVisibility(View.INVISIBLE);
                }
            }
        });

        zoomImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                memoImageView.setVisibility(View.INVISIBLE);
                zoomImageView.setVisibility(View.INVISIBLE);
                largeMemoImageView.setVisibility(View.VISIBLE);
            }
        });

        largeMemoImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                largeMemoImageView.setVisibility(View.GONE);
                memoImageView.setVisibility(View.VISIBLE);
                zoomImageView.setVisibility(View.VISIBLE);
            }
        });

    }

    private void loadRoutes() {
        for(int i=0; i<allRoutes.size(); i++) {
            fileName = allRoutes.get(i).getRouteName() + "_" + allRoutes.get(i).getRouteDate();
            //Call LoadGeoJson
            FeatureCollection featureCollection = LoadGeoJson();
            if (featureCollection != null) {
                drawLines(featureCollection);
            }
        }
    }

    private boolean checkInternetState() {
        boolean connected = false;
        ConnectivityManager connectivityManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        if(connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).getState() == NetworkInfo.State.CONNECTED ||
                connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState() == NetworkInfo.State.CONNECTED) {
            connected = true;
        }
        return connected;
    }

    //When we are in the Main Activity and you tap "Back" then the app minimizes
    @Override
    public void onBackPressed() {
        Intent close_Intent = new Intent(Intent.ACTION_MAIN);
        close_Intent.addCategory(Intent.CATEGORY_HOME);
        close_Intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(close_Intent);
    }

    private void deleteItem(int position) {
        File dcim = getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        if (dcim != null) {
            File[] DCIMfiles = dcim.listFiles();
            if (DCIMfiles != null) {
                for (File tempFile : DCIMfiles) {
                    if(tempFile.getName().equals(allRoutes.get(position).getRouteName() + "_" + allRoutes.get(position).getRouteDate() + FILE_EXT)) {
                        tempFile.delete();
                    }
                }
            }
        }
        allRoutes.remove(position);
    }

    private ArrayList readFiles() {
        File dcim = getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        ArrayList<Route> filesArray = new ArrayList<>();
        if (dcim != null) {
            File[] DCIMfiles = dcim.listFiles();
            if (DCIMfiles != null) {
                for (File tempFile : DCIMfiles) {
                    if(tempFile.getAbsolutePath().substring(tempFile.getAbsolutePath().lastIndexOf(".")+1).equals("geojson")) {
                        filesArray.add(new Route(tempFile.getName().substring(0, tempFile.getName().indexOf("_")), tempFile.getName().substring(tempFile.getName().indexOf("_")+1, tempFile.getName().lastIndexOf("."))));
                    }
                }
            }
        }
        return filesArray;
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
        allRoutes.clear();
        allPotholes.clear();
        allRoutes.addAll(readFiles());
        try {
            allPotholes.addAll(getPotholes());
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }
        routesList.invalidate();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    private FeatureCollection LoadGeoJson() {

        WeakReference<MainActivity> weakReference;

        weakReference = new WeakReference<>(this);

        MainActivity activity = weakReference.get();
        if (activity != null) {
            FileInputStream inputStream = null;
            try {
                inputStream = new FileInputStream(new File(getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "/" + fileName + FILE_EXT));
            } catch (FileNotFoundException e) {
                Timber.e("Exception Loading GeoJSON: %s", e.toString());
            }
            return FeatureCollection.fromJson(convertStreamToString(inputStream));
        }
        return null;
    }

    private String convertStreamToString(InputStream is) {
        Scanner scanner = new Scanner(is).useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
    }

    private void drawLines(@NonNull FeatureCollection featureCollection) {
        List<Feature> features = featureCollection.features();
        if (features != null && features.size() > 0) {
            Feature feature = features.get(0);
            //drawBeforeMapMatching(feature);
            requestMapMatched(feature);
        }
    }

    private void drawBeforeMapMatching(final Feature feature) {
        map.getStyle(new Style.OnStyleLoaded() {
            @Override
            public void onStyleLoaded(@NonNull Style style) {
                layersCounter++;
                style.addSource(new GeoJsonSource("pre-matched-source-id" + layersCounter, feature));
                try {
                    style.addLayer(new LineLayer("pre-matched-layer-id" + layersCounter, "pre-matched-source-id" + layersCounter).withProperties(
                            lineColor(ColorUtils.colorToRgbaString(Color.parseColor(getColor()))),
                            lineWidth(6f),
                            lineOpacity(1f)
                    ));
                } catch (JSONException | IOException e) {
                    e.printStackTrace();
                }
                style.removeSource("pre-matched-source-id");
            }
        });
    }

    private void requestMapMatched(Feature feature) {
        List<Point> points = ((LineString) Objects.requireNonNull(feature.geometry())).coordinates();

        try {
            // Setup the request using a client.
            MapboxMapMatching client = MapboxMapMatching.builder()
                    .accessToken(Objects.requireNonNull(Mapbox.getAccessToken()))
                    .profile(PROFILE_DRIVING)
                    .coordinates(points)
                    .build();

            // Execute the API call and handle the response.
            client.enqueueCall(new Callback<MapMatchingResponse>() {
                @Override
                public void onResponse(@NonNull Call<MapMatchingResponse> call,
                                       @NonNull Response<MapMatchingResponse> response) {
                    if (response.code() == 200) {
                        try {
                            drawMapMatched(Objects.requireNonNull(Objects.requireNonNull(response.body()).matchings()));
                        } catch (JSONException | IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        // If the response code does not response "OK" an error has occurred.
                        Timber.e("MapboxMapMatching failed with %s", response.code());
                    }
                }

                @Override
                public void onFailure(Call<MapMatchingResponse> call, Throwable throwable) {
                    Timber.e(throwable, "MapboxMapMatching error");
                }
            });
        } catch (ServicesException servicesException) {
            Timber.e(servicesException, "MapboxMapMatching error");
        }

    }

    private void drawMapMatched(@NonNull List<MapMatchingMatching> matchings) throws IOException, JSONException {
        Style style = map.getStyle();

        if (style != null && !matchings.isEmpty()) {
            layersCounter++;
            style.addSource(new GeoJsonSource("matched-source-id" + layersCounter, Feature.fromGeometry(LineString.fromPolyline(
                    Objects.requireNonNull(matchings.get(0).geometry()), PRECISION_6)))
            );
            style.addLayer(new LineLayer("matched-layer-id" + layersCounter, "matched-source-id" + layersCounter)
                    .withProperties(
                            lineColor(ColorUtils.colorToRgbaString(Color.parseColor(getColor()))),
                            lineWidth(6f),lineOpacity(0.7f)));
            addPotholes();
        }
    }

    private void addPotholes() {
        for(int cntr=0; cntr<allPotholes.size(); cntr++) {
            map.addPolygon(generatePerimeter(
                    new LatLng(allPotholes.get(cntr).getLatitude(), allPotholes.get(cntr).getLongitude()),
                    0.05,
                    16));
        }
    }

    private PolygonOptions generatePerimeter(LatLng centerCoordinates, double radiusInKilometers, int numberOfSides) {
        List<LatLng> positions = new ArrayList<>();
        double distanceX = radiusInKilometers / (111.319 * Math.cos(centerCoordinates.getLongitude() * Math.PI / 180));
        double distanceY = radiusInKilometers / 110.574;
        double slice = (2 * Math.PI) / numberOfSides;

        double theta, x, y;
        LatLng position;
        for (int i = 0; i < numberOfSides; ++i) {
            theta = i * slice;
            x = distanceX * Math.cos(theta);
            y = distanceY * Math.sin(theta);

            position = new LatLng(centerCoordinates.getLongitude() + y,
                    centerCoordinates.getLatitude() + x);
            positions.add(position);
        }

        return new PolygonOptions()
                .addAll(positions)
                .fillColor(Color.RED);
    }

    private String getColor() throws JSONException, IOException {
        JSONObject jsonFile;
        StringBuilder text = new StringBuilder();
        File fileToRead = new File(getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "/" + fileName + FILE_EXT);
        BufferedReader br = new BufferedReader(new FileReader(fileToRead));
        String line;

        while ((line = br.readLine()) != null) {
            text.append(line);
            text.append('\n');
        }
        br.close();

        jsonFile = new JSONObject(text.toString());

        int roadValue = jsonFile.getJSONArray("features").getJSONObject(0).getJSONObject("properties").getInt("value");
        String roadColor = "#000000";
        switch (roadValue) {
            case 1:
                roadColor = "#66ff33";
                break;
            case 2:
                roadColor = "#ffcc00";
                break;
            case 3:
                roadColor = "#ff0000";
                break;
        }
        return roadColor;
    }

    private ArrayList<CoordinatesData> getPotholes() throws JSONException, IOException {
        ArrayList<CoordinatesData> tempPotholes = new ArrayList<>();
        for(int fileCtr = 0; fileCtr<allRoutes.size(); fileCtr++) {
            File fileToRead = new File(getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "/" + allRoutes.get(fileCtr).getRouteName() + "_" + allRoutes.get(fileCtr).getRouteDate() + FILE_EXT);
            BufferedReader br = new BufferedReader(new FileReader(fileToRead));
            String line;
            JSONObject jsonFile;
            StringBuilder text = new StringBuilder();

            while ((line = br.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
            br.close();

            jsonFile = new JSONObject(text.toString());

            for(int j=0; j<jsonFile.getJSONArray("features").getJSONObject(0).getJSONArray("potholes").length(); j++) {
                tempPotholes.add(new CoordinatesData(jsonFile.getJSONArray("features").getJSONObject(0).getJSONArray("potholes").getJSONArray(j).getDouble(0),
                        jsonFile.getJSONArray("features").getJSONObject(0).getJSONArray("potholes").getJSONArray(j).getDouble(1)));
            }
        }
        return tempPotholes;
    }

    private CoordinatesData getPosition() throws IOException, JSONException {
        JSONObject jsonFile;
        StringBuilder text = new StringBuilder();
        File fileToRead = new File(getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "/" + fileName + FILE_EXT);
        BufferedReader br = new BufferedReader(new FileReader(fileToRead));
        String line;

        while ((line = br.readLine()) != null) {
            text.append(line);
            text.append('\n');
        }
        br.close();

        jsonFile = new JSONObject(text.toString());
        double tempLat = jsonFile.getJSONArray("features").getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates").getJSONArray(0).getDouble(0);
        double tempLong = jsonFile.getJSONArray("features").getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates").getJSONArray(0).getDouble(1);

        return new CoordinatesData(tempLong, tempLat);
    }
}