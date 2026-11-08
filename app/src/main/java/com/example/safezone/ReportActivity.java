package com.example.safezone;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReportActivity extends AppCompatActivity {

    private double latSeleccionada = 0.0;
    private double lonSeleccionada = 0.0;
    private ImageView ivVistaPreviaFoto;
    private String rutaFoto = null;

    private final ActivityResultLauncher<Void> capturarFotoLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicturePreview(),
            bitmap -> {
                if (bitmap != null) {
                    ivVistaPreviaFoto.setImageBitmap(bitmap);
                    ivVistaPreviaFoto.setVisibility(View.VISIBLE);
                    try {
                        File file = new File(getCacheDir(), "foto_reporte_" + System.currentTimeMillis() + ".png");
                        FileOutputStream out = new FileOutputStream(file);
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                        out.flush();
                        out.close();
                        rutaFoto = file.getAbsolutePath();
                        Toast.makeText(this, "Foto adjuntada", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        latSeleccionada = getIntent().getDoubleExtra("LAT_MAPA", 0.0);
        lonSeleccionada = getIntent().getDoubleExtra("LON_MAPA", 0.0);

        TextView btnVolverReporte = findViewById(R.id.btnVolverReporte);
        Spinner dropTipoCrimen = findViewById(R.id.dropTipoCrimen);
        EditText etHorario = findViewById(R.id.etHorario);
        EditText etDescripcion = findViewById(R.id.etDescripcion);
        Button btnEnviarReporte = findViewById(R.id.btnEnviarReporte);
        Button btnTomarFoto = findViewById(R.id.btnTomarFoto);
        ivVistaPreviaFoto = findViewById(R.id.ivVistaPreviaFoto);

        if (btnVolverReporte != null) {
            btnVolverReporte.setOnClickListener(v -> finish());
        }

        // Sin opción predeterminada válida
        String[] opcionesCrimen = {
                "Seleccione un tipo de delito...",
                "Robo con violencia",
                "Robo por sorpresa (Lanzazo)",
                "Hurto",
                "Actividad sospechosa",
                "Acoso callejero",
                "Vandalismo"
        };

        if (dropTipoCrimen != null) {
            ArrayAdapter<String> adapterCrimen = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, opcionesCrimen);
            dropTipoCrimen.setAdapter(adapterCrimen);
        }

        // Inyecta la hora actual del dispositivo en el campo de texto
        if (etHorario != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            etHorario.setText(sdf.format(new Date()) + " hrs");
        }

        if (btnTomarFoto != null) {
            btnTomarFoto.setOnClickListener(v -> capturarFotoLauncher.launch(null));
        }

        if (btnEnviarReporte != null) {
            btnEnviarReporte.setOnClickListener(v -> {
                String tipoCrimen = (dropTipoCrimen != null && dropTipoCrimen.getSelectedItem() != null)
                        ? dropTipoCrimen.getSelectedItem().toString() : "";

                if (tipoCrimen.equals("Seleccione un tipo de delito...")) {
                    Toast.makeText(this, "Debe seleccionar un tipo de delito", Toast.LENGTH_SHORT).show();
                    return;
                }

                String horario = (etHorario != null && etHorario.getText() != null)
                        ? etHorario.getText().toString().trim() : "No especificado";

                String descripcion = (etDescripcion != null && etDescripcion.getText() != null)
                        ? etDescripcion.getText().toString().trim() : "";

                if (descripcion.isEmpty()) {
                    descripcion = "Sin detalles adicionales.";
                }

                Intent resultIntent = new Intent();
                resultIntent.putExtra("TIPO_CRIMEN", tipoCrimen);
                resultIntent.putExtra("HORARIO", horario);
                resultIntent.putExtra("DESCRIPCION", descripcion);
                resultIntent.putExtra("LAT", latSeleccionada);
                resultIntent.putExtra("LON", lonSeleccionada);
                if (rutaFoto != null) {
                    resultIntent.putExtra("RUTA_FOTO", rutaFoto);
                }

                setResult(RESULT_OK, resultIntent);
                finish();
            });
        }
    }
}