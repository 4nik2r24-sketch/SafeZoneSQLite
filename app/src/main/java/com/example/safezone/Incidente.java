package com.example.safezone;

public class Incidente {

    private String idDelito;
    private double latitud;
    private double longitud;
    private String tipoCrimen;
    private String descripcionDelincuente;
    private String horario;
    private String rutaFoto; // NUEVO: Permite recordar la foto

    public Incidente() {
    }

    public Incidente(String idDelito, double latitud, double longitud, String tipoCrimen, String descripcionDelincuente, String horario) {
        this.idDelito = idDelito;
        this.latitud = latitud;
        this.longitud = longitud;
        this.tipoCrimen = tipoCrimen;
        this.descripcionDelincuente = descripcionDelincuente;
        this.horario = horario;
    }

    public String getIdDelito() { return idDelito; }
    public void setIdDelito(String idDelito) { this.idDelito = idDelito; }

    public double getLatitud() { return latitud; }
    public void setLatitud(double latitud) { this.latitud = latitud; }

    public double getLongitud() { return longitud; }
    public void setLongitud(double longitud) { this.longitud = longitud; }

    public String getTipoCrimen() { return tipoCrimen; }
    public void setTipoCrimen(String tipoCrimen) { this.tipoCrimen = tipoCrimen; }

    public String getDescripcionDelincuente() { return descripcionDelincuente; }
    public void setDescripcionDelincuente(String descripcionDelincuente) { this.descripcionDelincuente = descripcionDelincuente; }

    public String getHorario() { return horario; }
    public void setHorario(String horario) { this.horario = horario; }

    public String getRutaFoto() { return rutaFoto; }
    public void setRutaFoto(String rutaFoto) { this.rutaFoto = rutaFoto; }
}