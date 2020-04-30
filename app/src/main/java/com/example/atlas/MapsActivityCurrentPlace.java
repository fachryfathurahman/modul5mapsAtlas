package com.example.atlas;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import com.example.atlas.retrofit.APIService;
import com.example.atlas.retrofit.RetrofitClient;
import com.example.atlas.retrofit.response.Hasil;
import com.example.atlas.retrofit.response.Result;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Aktivitas yang menampilkan peta yang menunjukkan tempat di lokasi perangkat saat ini.
 */

public class MapsActivityCurrentPlace extends AppCompatActivity
        implements OnMapReadyCallback {

    private static final String TAG = MapsActivityCurrentPlace.class.getSimpleName();
    private GoogleMap mMap;
    private CameraPosition mCameraPosition;

    // Titik masuk ke Places API.
    private PlacesClient mPlacesClient;

    //Titik masuk ke Fused Location Provider.
    private FusedLocationProviderClient mFusedLocationProviderClient;

    //Lokasi default (malang, ijen) dan zoom default untuk digunakan ketika izin lokasi
    //tidak di izinkan
    double lat = -7.980301;
    double lon = 112.617647;
    private final LatLng mDefaultLocation = new LatLng(lat, lon);
    private static final int DEFAULT_ZOOM = 15;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private boolean mLocationPermissionGranted;

    //Lokasi geografis di mana perangkat saat ini berada. yaitu, saat
    //lokasi terakhir yang diambil oleh fused location provieder  .
    private Location mLastKnownLocation;

    // Kunci untuk menyimpan status aktivitas.
    private static final String KEY_CAMERA_POSITION = "camera_position";
    private static final String KEY_LOCATION = "location";

    // Digunakan untuk memilih tempat disekitar saat ini.
    private static final int M_MAX_ENTRIES = 1000;
    private String[] mLikelyPlaceNames;
    private String[] mLikelyPlaceAddresses;
    private List[] mLikelyPlaceAttributions;
    private LatLng[] mLikelyPlaceLatLngs;
    private int iMap = 0;

    // Untuk format lat dan lon
    NumberFormat formatter = NumberFormat.getInstance(new Locale("en"));
    private Hasil dataPlace = new Hasil();

    // untuk parameter searchnearby api oleh places
    @SuppressLint("DefaultLocale")
    String location = "";
    String radius = "1500";
    String format = "restaurant";
    String key = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // Ambil lokasi dan posisi kamera dari keadaan instance yang tersimpan tersimpan.
        if (savedInstanceState != null) {
            mLastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            mCameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION);
        }

        // Ambil kembali tampilan konten yang membuat peta.
        setContentView(R.layout.activity_maps);


        // membuat PlacesClient
        Places.initialize(getApplicationContext(), getString(R.string.google_maps_key));
        mPlacesClient = Places.createClient(this);

        // membuat sebuah FusedLocationProviderClient.
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        // membuat map.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        //mengambil api key
        key = getResources().getString(R.string.google_maps_key);


    }

    /**
     * Menyimpan status peta saat aktivitas dijeda.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mMap != null) {
            outState.putParcelable(KEY_CAMERA_POSITION, mMap.getCameraPosition());
            outState.putParcelable(KEY_LOCATION, mLastKnownLocation);
            super.onSaveInstanceState(outState);
        }
    }

    /**
     * Mengatur menu opsi.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.current_place_menu, menu);
        return true;
    }

    /**
     * Menangani klik pada opsi menu untuk mendapatkan tempat.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.option_get_place) {
            showCurrentPlace();
        }
        return true;
    }

    /**
     * Memanipulasi peta saat tersedia.
     * Callback ini dipicu ketika peta siap digunakan.
     */
    @Override
    public void onMapReady(GoogleMap map) {
        mMap = map;

        // Gunakan adaptor jendela info khusus untuk menangani beberapa baris teks di
        // isi info jendela.
        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {

            @Override
            // Kembalikan null di sini, sehingga getInfoContents () dipanggil selanjutnya.
            public View getInfoWindow(Marker arg0) {
                return null;
            }

            @Override
            public View getInfoContents(Marker marker) {
                // Kembangkan tata letak untuk jendela info, judul, dan cuplikan.
                View infoWindow = getLayoutInflater().inflate(R.layout.custom_info_contents,
                        (FrameLayout) findViewById(R.id.map), false);

                TextView title = infoWindow.findViewById(R.id.title);
                title.setText(marker.getTitle());

                TextView snippet = infoWindow.findViewById(R.id.snippet);
                snippet.setText(marker.getSnippet());

                return infoWindow;
            }
        });

        // menanyakan ijin lokasi kepada user
        getLocationPermission();

        // Aktifkan lapisan Lokasiku dan kontrol terkait di peta.
        updateLocationUI();

        // Dapatkan lokasi perangkat saat ini dan atur posisi peta.
        getDeviceLocation();

    }

    /**
     * Mendapat lokasi perangkat saat ini, dan memposisikan kamera peta.
     */
    private void getDeviceLocation() {
        /*
         * Dapatkan lokasi perangkat terbaik dan terbaru, yang mungkin nol
         * dalam kasus yang jarang terjadi ketika lokasi tidak tersedia.
         */
        try {
            if (mLocationPermissionGranted) {
                Task<Location> locationResult = mFusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful()) {
                            // Atur posisi kamera peta ke lokasi perangkat saat ini.
                            mLastKnownLocation = task.getResult();
                            if (mLastKnownLocation != null) {
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                        new LatLng(mLastKnownLocation.getLatitude(),
                                                mLastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                                lon = mLastKnownLocation.getLongitude();
                                lat = mLastKnownLocation.getLatitude();
                            }
                        } else {
                            Log.d(TAG, "lokasi saat ini null, pake default.");
                            Log.e(TAG, "Exception: %s", task.getException());
                            mMap.moveCamera(CameraUpdateFactory
                                    .newLatLngZoom(mDefaultLocation, DEFAULT_ZOOM));
                            mMap.getUiSettings().setMyLocationButtonEnabled(false);
                        }
                        /*
                        setelah dapat lokasi terkini, mencari lokasi restoran
                        berdasarkan lokasi saat ini
                         */
                        getNearbySearch();
                    }
                });
            }
        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    /**
     * mendapatkan lokasi  disekitar berdasarkakn lokasi device
     */

    private void getNearbySearch() {
        formatter.setMaximumFractionDigits(9);
        location = String.format("%s,%s", formatter.format(lat), formatter.format(lon));

        APIService service = RetrofitClient.getRetrofit().create(APIService.class);

        Call<Hasil> call = service.getHasil(location, radius, format, key);
        Log.d("TAG", "onResponse: ConfigurationListener::" + call.request().url());
        call.enqueue(new Callback<Hasil>() {
            @Override
            public void onResponse(Call<Hasil> call, Response<Hasil> response) {
                dataPlace = response.body();
                Log.d("TAG", "onResponse: " + dataPlace.getResults().get(0).getName());
                int count;
                if (dataPlace.getResults().size() < M_MAX_ENTRIES) {
                    count = dataPlace.getResults().size();
                } else {
                    count = M_MAX_ENTRIES;
                }

                int i = 0;
                mLikelyPlaceNames = new String[count];
                mLikelyPlaceAddresses = new String[count];
                mLikelyPlaceAttributions = new List[count];
                mLikelyPlaceLatLngs = new LatLng[count];

                for (Result place : dataPlace.getResults()) {
                    // Buat daftar tempat untuk ditampilkan kepada pengguna.

                    mLikelyPlaceNames[i] = place.getName();
                    mLikelyPlaceAddresses[i] = place.getVicinity();
                    mLikelyPlaceAttributions[i] = place.getTypes();
                    mLikelyPlaceLatLngs[i] = new LatLng(place.getGeometry().getLocation().getLat(), place.getGeometry().getLocation().getLng());
                    i++;
                }

                if(mLocationPermissionGranted){
                    i = 0;
                    for (String namePlaces :
                            mLikelyPlaceNames) {
                        String markerSnippet = mLikelyPlaceAddresses[i];
                        if (mLikelyPlaceAttributions[i] != null) {
                            markerSnippet = markerSnippet + "\n" + mLikelyPlaceAttributions[i];
                        }
                        mMap.addMarker(new MarkerOptions()
                                .title(namePlaces)
                                .position(mLikelyPlaceLatLngs[i])
                                .snippet(markerSnippet));
                        i++;
                    }
                }
            }

            @Override
            public void onFailure(Call<Hasil> call, Throwable t) {
                Log.d("TAG", "onFailure: " + t.toString());
            }
        });
    }

    /**
     * Meminta pengguna untuk menggunakan lokasi perangkat.
     */
    private void getLocationPermission() {
        /*
         * Minta izin lokasi, sehingga kita bisa mendapatkan lokasi perangkat.
         * Hasil permintaan izin ditangani oleh callback, onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
            getNearbySearch();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    /**
     * Menangani hasil permintaan izin lokasi.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        mLocationPermissionGranted = false;
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // Jika permintaan dibatalkan, array hasil kosong.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true;
                }
            }
        }
        updateLocationUI();
    }

    /**
     * Meminta pengguna untuk memilih tempat saat ini dari daftar kemungkinan tempat, dan menunjukkan
     * tempat saat ini di peta - asalkan pengguna telah memberikan izin lokasi.
     */
    private void showCurrentPlace() {
        if (mMap == null) {
            return;
        }
        if (mLocationPermissionGranted) {
            MapsActivityCurrentPlace.this.openPlacesDialog();

        } else {
            // Pengguna belum memberikan izin maka akan
            // masuk sini.
            Log.i(TAG, "The user did not grant location permission.");

            // Tambahkan penanda default, karena pengguna belum memilih tempat.
            mMap.addMarker(new MarkerOptions()
                    .title(getString(R.string.default_info_title))
                    .position(mDefaultLocation)
                    .snippet(getString(R.string.default_info_snippet)));

            // Meminta pengguna untuk izin.
            getLocationPermission();
        }
    }

    /**
     * Menampilkan formulir yang memungkinkan
     * pengguna untuk memilih tempat dari daftar tempat yang mungkin.
     */
    private void openPlacesDialog() {
        // Minta pengguna untuk memilih tempat di mana mereka berada sekarang.
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // variabel "which" berisi posisi item yang dipilih.
                LatLng markerLatLng = mLikelyPlaceLatLngs[which];

                iMap = which;
                // menggambar polyline dari titik asal ke tempat tujuan.
                Polyline polyline1 = mMap.addPolyline(new PolylineOptions()
                        .clickable(true)
                        .add(
                                new LatLng(mLastKnownLocation.getLatitude(), mLastKnownLocation.getLongitude()),
                                mLikelyPlaceLatLngs[iMap]));
                //Posisikan kamera peta di lokasi penanda
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(markerLatLng,
                        DEFAULT_ZOOM));
            }
        };

        // menampilkan dialog.
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.pick_place)
                .setItems(mLikelyPlaceNames, listener)
                .show();
    }

    /**
     * Memperbarui pengaturan UI peta berdasarkan pengguna, telah memberikan izin lokasi atau belum.
     */
    private void updateLocationUI() {
        if (mMap == null) {
            return;
        }
        try {
            if (mLocationPermissionGranted) {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);

            } else {
                mMap.setMyLocationEnabled(false);
                mMap.getUiSettings().setMyLocationButtonEnabled(false);
                mLastKnownLocation = null;
                getLocationPermission();
            }
        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage());
        }
    }
}
