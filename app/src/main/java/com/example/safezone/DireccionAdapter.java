package com.example.safezone;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DireccionAdapter extends ArrayAdapter<ResultadoDireccion> implements Filterable {

    private final Context context;
    private final Geocoder geocoder;
    private List<ResultadoDireccion> resultados = new ArrayList<>();

    public DireccionAdapter(Context context) {
        super(context, R.layout.item_sugerencia_direccion);
        this.context = context;
        this.geocoder = new Geocoder(context, new Locale("es", "CL"));
    }

    @Override
    public int getCount() {
        return resultados.size();
    }

    @Override
    public ResultadoDireccion getItem(int position) {
        if (position >= 0 && position < resultados.size()) {
            return resultados.get(position);
        }
        return null;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_sugerencia_direccion, parent, false);
        }

        TextView tvTitulo = convertView.findViewById(R.id.tvTituloLugar);
        TextView tvSubtitulo = convertView.findViewById(R.id.tvSubtituloLugar);

        ResultadoDireccion item = getItem(position);
        if (item != null) {
            String textoCompleto = item.getNombre();
            if (textoCompleto.contains(",")) {
                String[] partes = textoCompleto.split(",", 2);
                tvTitulo.setText(partes[0].trim());
                tvSubtitulo.setText(partes[1].trim());
                tvSubtitulo.setVisibility(View.VISIBLE);
            } else {
                tvTitulo.setText(textoCompleto);
                tvSubtitulo.setVisibility(View.GONE);
            }
        }
        return convertView;
    }

    // MÉTODO FALTANTE RESTAURADO PARA QUE MAINACTIVITY COMPILE
    public void actualizarDatos(List<ResultadoDireccion> nuevosDatos) {
        resultados.clear();
        if (nuevosDatos != null) {
            resultados.addAll(nuevosDatos);
        }
        notifyDataSetChanged();
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults filterResults = new FilterResults();
                if (constraint != null && constraint.toString().trim().length() >= 3) {
                    List<ResultadoDireccion> lista = new ArrayList<>();
                    String consulta = constraint.toString().trim();

                    // RESCATE DE NÚMERO
                    String numeroEscrito = "";
                    for (String parte : consulta.split("\\s+")) {
                        if (parte.matches(".*\\d+.*")) {
                            numeroEscrito = parte;
                            break;
                        }
                    }

                    try {
                        List<Address> direcciones = null;

                        try {
                            direcciones = geocoder.getFromLocationName(
                                    consulta,
                                    10,
                                    -21.0, -70.3,
                                    -20.0, -69.8
                            );
                        } catch (Exception ignored) {}

                        if (direcciones == null || direcciones.isEmpty()) {
                            direcciones = geocoder.getFromLocationName(consulta, 8);
                        }

                        if (direcciones != null) {
                            for (Address addr : direcciones) {
                                StringBuilder titulo = new StringBuilder();
                                StringBuilder subtitulo = new StringBuilder();

                                if (addr.getThoroughfare() != null) {
                                    titulo.append(addr.getThoroughfare());
                                    String numOficial = addr.getSubThoroughfare();
                                    String numFinal = (numOficial != null && !numOficial.isEmpty()) ? numOficial : numeroEscrito;
                                    if (!numFinal.isEmpty()) {
                                        titulo.append(" #").append(numFinal);
                                    }
                                } else if (addr.getFeatureName() != null && !addr.getFeatureName().equalsIgnoreCase(addr.getCountryName())) {
                                    titulo.append(addr.getFeatureName());
                                    if (!numeroEscrito.isEmpty() && !titulo.toString().contains(numeroEscrito)) {
                                        titulo.append(" #").append(numeroEscrito);
                                    }
                                } else if (addr.getMaxAddressLineIndex() >= 0) {
                                    titulo.append(addr.getAddressLine(0));
                                }

                                if (addr.getLocality() != null) {
                                    subtitulo.append(addr.getLocality());
                                } else if (addr.getSubAdminArea() != null) {
                                    subtitulo.append(addr.getSubAdminArea());
                                }

                                if (addr.getAdminArea() != null) {
                                    if (subtitulo.length() > 0) subtitulo.append(", ");
                                    subtitulo.append(addr.getAdminArea());
                                }

                                String etiquetaFinal;
                                if (subtitulo.length() > 0 && !titulo.toString().contains(subtitulo.toString())) {
                                    etiquetaFinal = titulo.toString() + ", " + subtitulo.toString();
                                } else {
                                    etiquetaFinal = (titulo.length() > 0) ? titulo.toString() : consulta;
                                }

                                if (!etiquetaFinal.trim().equalsIgnoreCase("Chile")) {
                                    lista.add(new ResultadoDireccion(etiquetaFinal, addr.getLatitude(), addr.getLongitude()));
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    filterResults.values = lista;
                    filterResults.count = lista.size();
                } else {
                    filterResults.values = new ArrayList<ResultadoDireccion>();
                    filterResults.count = 0;
                }
                return filterResults;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                if (results != null && results.values != null) {
                    resultados = (List<ResultadoDireccion>) results.values;
                    if (results.count > 0) {
                        notifyDataSetChanged();
                    } else {
                        notifyDataSetInvalidated();
                    }
                } else {
                    resultados.clear();
                    notifyDataSetInvalidated();
                }
            }
        };
    }
}