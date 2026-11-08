package com.example.safezone;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SafeZoneMain";
    private static final XYTileSource FUENTE_LIBRE = new XYTileSource(
            "OSMFrance", 0, 19, 256, ".png",
            new String[]{"https://a.tile.openstreetmap.fr/osmfr/", "https://b.tile.openstreetmap.fr/osmfr/", "https://c.tile.openstreetmap.fr/osmfr/"}
    );

    private static class ZonaRiesgo {
        GeoPoint centro;
        int totalReportes;
        Polygon perimetroVisual;
        List<Marker> marcadores = new ArrayList<>(); // Guarda los pines para teñirlos al mutar la zona

        ZonaRiesgo(GeoPoint centro, Polygon perimetroVisual) {
            this.centro = centro;
            this.totalReportes = 1;
            this.perimetroVisual = perimetroVisual;
        }
    }

    private DatabaseHelper dbHelper;
    private String idDelitoSeleccionado = null;
    private boolean modoHistorial = false;
    private Polyline rutaActual = null;

    private BottomSheetBehavior<View> bottomSheetBehavior;
    private TextView tvTituloSector, tvHorario, tvTipoCrimen, tvDescripcion;
    private ImageView ivFotoReporte;
    private FloatingActionButton btnFlotanteSOS, btnCentrarMapa;
    private CardView btnPerfilContacto;
    private Button btnFiltroVivo, btnFiltroHistorial, btnRutaSegura;
    private MapView mapa;

    private AutoCompleteTextView txtBuscar;
    private ImageView btnLimpiarBuscador;
    private DireccionAdapter direccionAdapter;
    private Marker marcadorLugarBuscado;

    private final ExecutorService ejecutorRed = Executors.newSingleThreadExecutor();
    private final Handler handlerDebounce = new Handler(Looper.getMainLooper());
    private Runnable tareaBusqueda;

    private final List<Incidente> listaIncidentes = new ArrayList<>();
    private final List<ZonaRiesgo> listaZonas = new ArrayList<>();
    private MapEventsReceiver receptorEventos;

    private final androidx.activity.result.ActivityResultLauncher<Intent> reporteLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Intent data = result.getData();
                            procesarNuevoReporte(
                                    data.getDoubleExtra("LAT", -20.2155),
                                    data.getDoubleExtra("LON", -70.1513),
                                    data.getStringExtra("TIPO_CRIMEN"),
                                    data.getStringExtra("HORARIO"),
                                    data.getStringExtra("DESCRIPCION"),
                                    data.getStringExtra("RUTA_FOTO")
                            );
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        dbHelper = new DatabaseHelper(this);
        File cacheOsm = new File(getCacheDir(), "osmdroid");
        borrarDirectorio(cacheOsm);

        Configuration.getInstance().setUserAgentValue("SafeZone_Iquique_V5.0");
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid_prefs", MODE_PRIVATE));
        setContentView(R.layout.activity_main);
        solicitarPermisos();

        mapa = findViewById(R.id.map);
        mapa.setTileSource(FUENTE_LIBRE);
        mapa.setMultiTouchControls(true);
        mapa.getController().setZoom(15.0);
        mapa.getController().setCenter(new GeoPoint(-20.2155, -70.1513));

        View bottomSheet = findViewById(R.id.bottomSheet);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        tvTituloSector = findViewById(R.id.tvTituloSector);
        tvHorario = findViewById(R.id.tvHorario);
        tvTipoCrimen = findViewById(R.id.tvTipoCrimen);
        tvDescripcion = findViewById(R.id.tvDescripcion);
        ivFotoReporte = findViewById(R.id.ivFotoReporte);

        btnFiltroVivo = findViewById(R.id.btnFiltroVivo);
        btnFiltroHistorial = findViewById(R.id.btnFiltroHistorial);
        btnFlotanteSOS = findViewById(R.id.btnFlotanteSOS);
        btnCentrarMapa = findViewById(R.id.btnCentrarMapa);
        btnPerfilContacto = findViewById(R.id.btnPerfilContacto);
        btnRutaSegura = findViewById(R.id.btnRutaSegura);

        txtBuscar = findViewById(R.id.txtBuscar);
        btnLimpiarBuscador = findViewById(R.id.btnLimpiarBuscador);
        View cardBuscador = findViewById(R.id.cardBuscador);

        txtBuscar.setThreshold(1);
        if (cardBuscador != null) txtBuscar.setDropDownAnchor(cardBuscador.getId());
        direccionAdapter = new DireccionAdapter(this);
        txtBuscar.setAdapter(direccionAdapter);

        btnFiltroVivo.setOnClickListener(v -> {
            modoHistorial = false;
            btnFiltroVivo.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#EF4444")));
            btnFiltroVivo.setTextColor(Color.WHITE);
            btnFiltroHistorial.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#171B26")));
            btnFiltroHistorial.setTextColor(Color.parseColor("#94A3B8"));
            cargarIncidentesDesdeSQLite();
            Toast.makeText(this, "Mostrando últimas 24 hrs", Toast.LENGTH_SHORT).show();
        });

        btnFiltroHistorial.setOnClickListener(v -> {
            modoHistorial = true;
            btnFiltroHistorial.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#EF4444")));
            btnFiltroHistorial.setTextColor(Color.WHITE);
            btnFiltroVivo.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#171B26")));
            btnFiltroVivo.setTextColor(Color.parseColor("#94A3B8"));
            cargarIncidentesDesdeSQLite();
            Toast.makeText(this, "Mostrando Historial Completo", Toast.LENGTH_SHORT).show();
        });

        txtBuscar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (btnLimpiarBuscador != null) btnLimpiarBuscador.setVisibility((s != null && s.length() > 0) ? View.VISIBLE : View.GONE);
                if (tareaBusqueda != null) handlerDebounce.removeCallbacks(tareaBusqueda);
                if (s != null && s.toString().trim().length() >= 2) {
                    tareaBusqueda = () -> ejecutarBusquedaEnSegundoPlano(s.toString().trim());
                    handlerDebounce.postDelayed(tareaBusqueda, 350);
                } else {
                    direccionAdapter.actualizarDatos(new ArrayList<>());
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        if (btnLimpiarBuscador != null) {
            btnLimpiarBuscador.setOnClickListener(v -> {
                txtBuscar.setText("");
                txtBuscar.clearFocus();
                direccionAdapter.actualizarDatos(new ArrayList<>());
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(txtBuscar.getWindowToken(), 0);
            });
        }

        txtBuscar.setOnItemClickListener((parent, view, position, id) -> {
            ResultadoDireccion dir = direccionAdapter.getItem(position);
            if (dir != null) {
                txtBuscar.setText("");
                txtBuscar.clearFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(txtBuscar.getWindowToken(), 0);

                GeoPoint destino = new GeoPoint(dir.getLatitud(), dir.getLongitud());
                mapa.getController().animateTo(destino);
                mapa.getController().setZoom(17.5);

                if (marcadorLugarBuscado != null) mapa.getOverlays().remove(marcadorLugarBuscado);
                marcadorLugarBuscado = new Marker(mapa);
                marcadorLugarBuscado.setPosition(destino);
                marcadorLugarBuscado.setTitle("Destino Consultado");
                marcadorLugarBuscado.setSnippet(dir.getNombre());
                marcadorLugarBuscado.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

                // Asignamos el celeste exclusivo para el buscador, usando el método robusto
                cambiarColorPin(marcadorLugarBuscado, Color.parseColor("#38BDF8"));

                mapa.getOverlays().add(marcadorLugarBuscado);
                mapa.invalidate();
                evaluarPanoramaSector(destino, dir.getNombre());
            }
        });

        btnPerfilContacto.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, ContactosActivity.class)));
        btnFlotanteSOS.setOnClickListener(v -> ejecutarProtocoloSOS());

        if (btnCentrarMapa != null) {
            btnCentrarMapa.setOnClickListener(v -> centrarEnMiUbicacion());
        }

        if (btnRutaSegura != null) {
            btnRutaSegura.setOnClickListener(v -> {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                if (marcadorLugarBuscado != null) {
                    obtenerUbicacionYCalcularRuta(marcadorLugarBuscado.getPosition());
                } else {
                    Toast.makeText(MainActivity.this, "Primero busca un destino en el mapa.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        tvDescripcion.setOnClickListener(v -> {
            if (idDelitoSeleccionado != null) mostrarOpcionesGestion();
        });

        receptorEventos = new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                idDelitoSeleccionado = null;
                return false;
            }
            @Override
            public boolean longPressHelper(GeoPoint p) {
                iniciarReporteConUbicacionDispositivo();
                return true;
            }
        };

        cargarIncidentesDesdeSQLite();
    }

    private void centrarEnMiUbicacion() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            solicitarPermisos();
            return;
        }
        Toast.makeText(this, "Ubicando dispositivo...", Toast.LENGTH_SHORT).show();
        com.google.android.gms.location.FusedLocationProviderClient cliente = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this);
        cliente.getCurrentLocation(100, null).addOnSuccessListener(this, loc -> {
            if (loc != null) {
                GeoPoint miPunto = new GeoPoint(loc.getLatitude(), loc.getLongitude());
                mapa.getController().animateTo(miPunto);
                mapa.getController().setZoom(17.5);
            } else {
                Toast.makeText(this, "Esperando señal satelital...", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void cargarIncidentesDesdeSQLite() {
        mapa.getOverlays().clear();
        if (rutaActual != null) mapa.getOverlays().add(rutaActual);
        listaIncidentes.clear();
        listaZonas.clear();
        mapa.getOverlays().add(new MapEventsOverlay(receptorEventos));

        List<Incidente> incidentes = dbHelper.obtenerTodosLosIncidentes();
        long ahora = System.currentTimeMillis();
        long unDiaMillis = 24L * 60 * 60 * 1000L;

        for (Incidente inc : incidentes) {
            long tiempoIncidente;
            try { tiempoIncidente = Long.parseLong(inc.getIdDelito()); }
            catch (Exception e) { tiempoIncidente = ahora; }

            boolean esAntiguo = (ahora - tiempoIncidente) > unDiaMillis;

            if (!modoHistorial && esAntiguo) {
                continue; // Omitir antiguos si estamos en "En Vivo"
            }

            listaIncidentes.add(inc);
            GeoPoint punto = new GeoPoint(inc.getLatitud(), inc.getLongitud());

            // Creamos el pin y lo obtenemos para guardarlo en la zona
            Marker nuevoPin = agregarMarcador(inc.getIdDelito(), punto, inc.getTipoCrimen(), inc.getHorario(), inc.getDescripcionDelincuente(), inc.getRutaFoto(), esAntiguo);

            ZonaRiesgo zonaCercana = null;
            for (ZonaRiesgo z : listaZonas) {
                if (z.centro.distanceToAsDouble(punto) <= 150.0) {
                    zonaCercana = z;
                    break;
                }
            }

            if (zonaCercana != null) {
                zonaCercana.totalReportes++;
                zonaCercana.marcadores.add(nuevoPin);
                actualizarZonaVisual(zonaCercana);
            } else {
                crearNuevaZonaVisual(punto, nuevoPin, esAntiguo);
            }
        }

        if (marcadorLugarBuscado != null) mapa.getOverlays().add(marcadorLugarBuscado);
        mapa.invalidate();
    }

    private void procesarNuevoReporte(double lat, double lon, String tipo, String horario, String desc, String rutaFoto) {
        String idGenerado = String.valueOf(System.currentTimeMillis());
        Incidente nuevoIncidente = new Incidente(idGenerado, lat, lon, tipo, desc, horario);
        nuevoIncidente.setRutaFoto(rutaFoto);

        if (dbHelper.insertarIncidente(nuevoIncidente)) {
            cargarIncidentesDesdeSQLite();
            mapa.getController().animateTo(new GeoPoint(lat, lon));
            Toast.makeText(this, "Reporte guardado con éxito", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Error al guardar el reporte", Toast.LENGTH_SHORT).show();
        }
    }

    // MÉTODO NUEVO: Aísla el ícono y lo tiñe sin afectar a los demás pines
    private void cambiarColorPin(Marker pin, int colorHex) {
        android.graphics.drawable.Drawable icono = ContextCompat.getDrawable(this, org.osmdroid.library.R.drawable.marker_default);
        if (icono != null) {
            icono = icono.mutate();
            icono.setColorFilter(colorHex, PorterDuff.Mode.SRC_IN);
            pin.setIcon(icono);
        }
    }

    private Marker agregarMarcador(String idDelito, GeoPoint punto, String tipoCrimen, String horario, String descripcion, String rutaFoto, boolean esAntiguo) {
        Marker marcador = new Marker(mapa);
        marcador.setPosition(punto);
        marcador.setId(idDelito);
        marcador.setTitle(tipoCrimen);
        marcador.setSnippet(descripcion);
        marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        // Si es antiguo lo pintamos gris fijo, si no, le ponemos el verde inicial por defecto (luego la zona lo actualizará)
        if (esAntiguo) {
            cambiarColorPin(marcador, Color.parseColor("#64748B"));
        } else {
            cambiarColorPin(marcador, Color.parseColor("#10B981"));
        }

        marcador.setOnMarkerClickListener((marker, mapView) -> {
            idDelitoSeleccionado = marker.getId();
            tvTituloSector.setText("Incidente Reportado");
            tvTipoCrimen.setText(marker.getTitle());
            tvDescripcion.setText(marker.getSnippet());
            tvHorario.setText("Horario: " + horario);

            if (ivFotoReporte != null) {
                if (rutaFoto != null && !rutaFoto.isEmpty()) {
                    android.graphics.Bitmap bmp = BitmapFactory.decodeFile(rutaFoto);
                    if (bmp != null) {
                        ivFotoReporte.setImageBitmap(bmp);
                        ivFotoReporte.setVisibility(View.VISIBLE);
                    } else {
                        ivFotoReporte.setVisibility(View.GONE);
                    }
                } else {
                    ivFotoReporte.setVisibility(View.GONE);
                }
            }

            if (btnRutaSegura != null) btnRutaSegura.setVisibility(View.GONE);

            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            return true;
        });

        mapa.getOverlays().add(marcador);
        return marcador;
    }

    private void crearNuevaZonaVisual(GeoPoint punto, Marker pinInicial, boolean esAntiguo) {
        Polygon perimetro = new Polygon();
        perimetro.setPoints(Polygon.pointsAsCircle(punto, 60.0));

        int colorBorde = esAntiguo ? Color.parseColor("#64748B") : Color.parseColor("#10B981");
        int colorRelleno = esAntiguo ? Color.argb(60, 100, 116, 139) : Color.argb(60, 16, 185, 129);

        perimetro.getOutlinePaint().setColor(colorBorde);
        perimetro.getOutlinePaint().setStrokeWidth(3.0f);
        perimetro.getFillPaint().setColor(colorRelleno);

        mapa.getOverlays().add(0, perimetro);

        ZonaRiesgo nuevaZona = new ZonaRiesgo(punto, perimetro);
        nuevaZona.marcadores.add(pinInicial);
        listaZonas.add(nuevaZona);
    }

    private void actualizarZonaVisual(ZonaRiesgo zona) {
        double radio = 60.0 + ((zona.totalReportes - 1) * 25.0);
        zona.perimetroVisual.setPoints(Polygon.pointsAsCircle(zona.centro, radio));

        int colorBorde = (zona.totalReportes == 2) ? Color.parseColor("#FBBF24") : (zona.totalReportes <= 4 ? Color.parseColor("#F97316") : Color.parseColor("#EF4444"));
        int colorRelleno = (zona.totalReportes == 2) ? Color.argb(70, 251, 191, 36) : (zona.totalReportes <= 4 ? Color.argb(80, 249, 115, 22) : Color.argb(95, 239, 68, 68));

        zona.perimetroVisual.getOutlinePaint().setColor(colorBorde);
        zona.perimetroVisual.getFillPaint().setColor(colorRelleno);

        long ahora = System.currentTimeMillis();
        long unDiaMillis = 24L * 60 * 60 * 1000L;

        // Actualizamos de manera dinámica todos los pines de esta zona (excepto los antiguos/grises)
        for (Marker pin : zona.marcadores) {
            long tiempoPin;
            try { tiempoPin = Long.parseLong(pin.getId()); } catch (Exception e) { tiempoPin = ahora; }
            boolean pinAntiguo = (ahora - tiempoPin) > unDiaMillis;

            if (!pinAntiguo) {
                cambiarColorPin(pin, colorBorde);
            }
        }
    }

    private void ejecutarProtocoloSOS() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_FINE_LOCATION}, 200);
            return;
        }

        List<Contacto> contactos = obtenerContactosEmergencia();
        if (contactos.isEmpty()) {
            Toast.makeText(this, "Agrega tus contactos primero.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(MainActivity.this, ContactosActivity.class));
            return;
        }

        String mensajeSMS = "¡ALERTA SOS! Me encuentro en peligro.";
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm != null) {
            Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc != null) mensajeSMS += " Mi ubicación exacta: https://maps.google.com/?q=" + loc.getLatitude() + "," + loc.getLongitude();
        }

        try {
            SmsManager smsManager = SmsManager.getDefault();
            ArrayList<String> partes = smsManager.divideMessage(mensajeSMS);
            for (Contacto c : contactos) {
                smsManager.sendMultipartTextMessage(c.getTelefono(), null, partes, null, null);
            }
            Toast.makeText(this, "SMS despachado a la red", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error SMS: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                Intent callIntent = new Intent(Intent.ACTION_CALL);
                callIntent.setData(Uri.parse("tel:" + contactos.get(0).getTelefono()));
                startActivity(callIntent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 1500);
    }

    private void mostrarOpcionesGestion() {
        String[] opciones = {"✏️ Modificar descripción", "🗑️ Eliminar reporte"};
        new AlertDialog.Builder(this).setTitle("Gestión").setItems(opciones, (dialog, which) -> {
            if (which == 0) mostrarDialogoModificar(); else if (which == 1) confirmarEliminacion();
        }).show();
    }

    private void mostrarDialogoModificar() {
        EditText input = new EditText(this);
        input.setText(tvDescripcion.getText().toString());
        input.setPadding(40, 40, 40, 40);
        new AlertDialog.Builder(this).setTitle("Actualizar Reporte").setView(input)
                .setPositiveButton("Guardar", (d, w) -> {
                    String nuevaDesc = input.getText().toString().trim();
                    if (!nuevaDesc.isEmpty() && idDelitoSeleccionado != null) {
                        if (dbHelper.actualizarDescripcion(idDelitoSeleccionado, nuevaDesc)) {
                            tvDescripcion.setText(nuevaDesc);
                            cargarIncidentesDesdeSQLite();
                        }
                    }
                }).setNegativeButton("Cancelar", null).show();
    }

    private void confirmarEliminacion() {
        new AlertDialog.Builder(this).setTitle("Confirmar").setMessage("¿Eliminar reporte permanentemente?")
                .setPositiveButton("Eliminar", (d, w) -> {
                    if (idDelitoSeleccionado != null && dbHelper.eliminarIncidente(idDelitoSeleccionado)) {
                        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                        cargarIncidentesDesdeSQLite();
                        idDelitoSeleccionado = null;
                    }
                }).setNegativeButton("Cancelar", null).show();
    }

    private void iniciarReporteConUbicacionDispositivo() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            solicitarPermisos();
            return;
        }
        com.google.android.gms.location.FusedLocationProviderClient cliente = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this);
        cliente.getCurrentLocation(100, null).addOnSuccessListener(this, ubicacion -> {
            if (ubicacion != null) {
                Intent intent = new Intent(MainActivity.this, ReportActivity.class);
                intent.putExtra("LAT_MAPA", ubicacion.getLatitude());
                intent.putExtra("LON_MAPA", ubicacion.getLongitude());
                reporteLauncher.launch(intent);
            } else {
                Toast.makeText(this, "Buscando satélites...", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void ejecutarBusquedaEnSegundoPlano(String consulta) {
        ejecutorRed.execute(() -> {
            List<ResultadoDireccion> listaSugerencias = new ArrayList<>();
            HttpURLConnection conn = null; BufferedReader reader = null;
            try {
                String queryCod = URLEncoder.encode(consulta, "UTF-8");
                URL url = new URL("https://photon.komoot.io/api/?q=" + queryCod + "&lat=-20.2155&lon=-70.1513&limit=7&lang=es");
                conn = (HttpURLConnection) url.openConnection();
                if (conn.getResponseCode() == 200) {
                    reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder(); String linea;
                    while ((linea = reader.readLine()) != null) sb.append(linea);
                    JSONObject geoJson = new JSONObject(sb.toString());
                    JSONArray features = geoJson.optJSONArray("features");
                    if (features != null) {
                        for (int i = 0; i < features.length(); i++) {
                            JSONObject prop = features.getJSONObject(i).getJSONObject("properties");
                            JSONArray coord = features.getJSONObject(i).getJSONObject("geometry").getJSONArray("coordinates");
                            String nombre = prop.optString("name", "").trim();
                            String calle = prop.optString("street", "").trim();
                            String titulo = !calle.isEmpty() ? calle : (!nombre.isEmpty() ? nombre : consulta);
                            listaSugerencias.add(new ResultadoDireccion(titulo, coord.getDouble(1), coord.getDouble(0)));
                        }
                    }
                }
            } catch (Exception e) { Log.e(TAG, "Error: " + e.getMessage());
            } finally { try { if (reader != null) reader.close(); if (conn != null) conn.disconnect(); } catch (Exception ignored) {} }
            runOnUiThread(() -> {
                direccionAdapter.actualizarDatos(listaSugerencias);
                if (!listaSugerencias.isEmpty() && txtBuscar.hasFocus()) txtBuscar.showDropDown();
            });
        });
    }

    private void evaluarPanoramaSector(GeoPoint puntoConsultado, String nombreLugar) {
        ZonaRiesgo zonaEncontrada = null;
        for (ZonaRiesgo z : listaZonas) {
            if (z.centro.distanceToAsDouble(puntoConsultado) <= (60.0 + ((z.totalReportes - 1) * 25.0))) { zonaEncontrada = z; break; }
        }

        String encabezado = nombreLugar.contains(",") ? nombreLugar.split(",")[0].trim() : nombreLugar;
        tvTituloSector.setText("Ubicación: " + (encabezado.length() > 30 ? encabezado.substring(0, 30) + "..." : encabezado));

        if (zonaEncontrada != null) {
            int total = zonaEncontrada.totalReportes;
            tvTipoCrimen.setText(total == 1 ? "🟢 PRECAUCIÓN: 1 Reporte" : (total <= 4 ? "🟠 PELIGRO: " + total + " Reportes" : "🔴 ZONA CRÍTICA: " + total + " Delitos"));
            tvHorario.setText("Nivel de riesgo estimado");
            tvDescripcion.setText("Este sector se encuentra dentro de un polígono de riesgo.");
        } else {
            tvTipoCrimen.setText("🛡️ SECTOR SEGURO / SIN REPORTES");
            tvHorario.setText("Estado: Despejado");
            tvDescripcion.setText("No se registran focos delictuales en un radio de 60 metros.");
        }

        if (ivFotoReporte != null) ivFotoReporte.setVisibility(View.GONE);

        if (btnRutaSegura != null) btnRutaSegura.setVisibility(View.VISIBLE);

        idDelitoSeleccionado = null;
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private void obtenerUbicacionYCalcularRuta(GeoPoint destino) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            solicitarPermisos();
            return;
        }

        Toast.makeText(this, "🛰️ Trazando ruta...", Toast.LENGTH_SHORT).show();
        com.google.android.gms.location.FusedLocationProviderClient clienteUbicacion =
                com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this);

        clienteUbicacion.getCurrentLocation(100, null).addOnSuccessListener(this, ubicacion -> {
            if (ubicacion != null) {
                GeoPoint origen = new GeoPoint(ubicacion.getLatitude(), ubicacion.getLongitude());
                trazarRutaOSRM(origen, destino);
            } else {
                trazarRutaOSRM(new GeoPoint(-20.2155, -70.1513), destino);
            }
        });
    }

    private void trazarRutaOSRM(GeoPoint origen, GeoPoint destino) {
        new Thread(() -> {
            try {
                String urlStr = "https://router.project-osrm.org/route/v1/foot/" + origen.getLongitude() + "," + origen.getLatitude() + ";" + destino.getLongitude() + "," + destino.getLatitude() + "?geometries=geojson&overview=full&alternatives=3";
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestProperty("User-Agent", "SafeZoneApp/1.0");

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String linea;
                    while ((linea = reader.readLine()) != null) sb.append(linea);

                    JSONObject jsonResponse = new JSONObject(sb.toString());
                    JSONArray routes = jsonResponse.optJSONArray("routes");

                    if (routes != null && routes.length() > 0) {
                        int mejorIndiceRuta = 0;
                        int minimasIntersecciones = Integer.MAX_VALUE;
                        List<GeoPoint> puntosRutaMasSegura = new ArrayList<>();

                        for (int r = 0; r < routes.length(); r++) {
                            JSONArray coordinates = routes.getJSONObject(r).getJSONObject("geometry").getJSONArray("coordinates");
                            List<GeoPoint> puntosRutaActual = new ArrayList<>();
                            int interseccionesDePeligro = 0;

                            for (int i = 0; i < coordinates.length(); i++) {
                                JSONArray coord = coordinates.getJSONArray(i);
                                GeoPoint puntoCalle = new GeoPoint(coord.getDouble(1), coord.getDouble(0));
                                puntosRutaActual.add(puntoCalle);

                                for (ZonaRiesgo z : listaZonas) {
                                    if (z.totalReportes >= 2) {
                                        double radioZona = 60.0 + ((z.totalReportes - 1) * 25.0);
                                        if (z.centro.distanceToAsDouble(puntoCalle) <= radioZona) {
                                            interseccionesDePeligro++;
                                            break;
                                        }
                                    }
                                }
                            }

                            if (interseccionesDePeligro < minimasIntersecciones) {
                                minimasIntersecciones = interseccionesDePeligro;
                                mejorIndiceRuta = r;
                                puntosRutaMasSegura = puntosRutaActual;
                            }
                        }

                        final boolean esSeguraTotalmente = (minimasIntersecciones == 0);
                        final List<GeoPoint> puntosFinales = puntosRutaMasSegura;

                        runOnUiThread(() -> {
                            if (rutaActual != null) mapa.getOverlays().remove(rutaActual);
                            rutaActual = new Polyline();
                            rutaActual.setPoints(puntosFinales);

                            if (!esSeguraTotalmente) {
                                rutaActual.getOutlinePaint().setColor(Color.parseColor("#F97316"));
                                Toast.makeText(MainActivity.this, "⚠️ La ruta cruza por calles de riesgo.", Toast.LENGTH_LONG).show();
                            } else {
                                rutaActual.getOutlinePaint().setColor(Color.parseColor("#3B82F6"));
                            }

                            rutaActual.getOutlinePaint().setStrokeWidth(14.0f);
                            mapa.getOverlays().add(rutaActual);
                            mapa.invalidate();
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private List<Contacto> obtenerContactosEmergencia() {
        List<Contacto> lista = new ArrayList<>();
        SharedPreferences prefs = getSharedPreferences("SafeZoneContactosPrefs", MODE_PRIVATE);
        String data = prefs.getString("lista_contactos_json", null);
        if (data != null) {
            try {
                JSONArray jsonArray = new JSONArray(data);
                for (int i = 0; i < jsonArray.length(); i++) lista.add(new Contacto(jsonArray.getJSONObject(i).getString("id"), jsonArray.getJSONObject(i).getString("nombre"), jsonArray.getJSONObject(i).getString("telefono")));
            } catch (JSONException e) { e.printStackTrace(); }
        }
        return lista;
    }

    private void solicitarPermisos() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS}, 101);
    }

    private void borrarDirectorio(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] hijos = dir.list();
            if (hijos != null) for (String hijo : hijos) borrarDirectorio(new File(dir, hijo));
            dir.delete();
        }
    }

    @Override protected void onResume() { super.onResume(); if (mapa != null) mapa.onResume(); }
    @Override protected void onPause() { super.onPause(); if (mapa != null) mapa.onPause(); }
}