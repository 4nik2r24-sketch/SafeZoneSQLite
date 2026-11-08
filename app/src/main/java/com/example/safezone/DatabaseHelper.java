package com.example.safezone;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "safezone.db";
    private static final int DATABASE_VERSION = 2; // Versión 2 para incluir la foto

    private static final String TABLE_INCIDENTES = "incidentes";
    private static final String COL_ID = "idDelito";
    private static final String COL_LAT = "latitud";
    private static final String COL_LON = "longitud";
    private static final String COL_TIPO = "tipoCrimen";
    private static final String COL_DESC = "descripcion";
    private static final String COL_HORARIO = "horario";
    private static final String COL_FOTO = "rutaFoto";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_INCIDENTES + " (" +
                COL_ID + " TEXT PRIMARY KEY, " +
                COL_LAT + " REAL, " +
                COL_LON + " REAL, " +
                COL_TIPO + " TEXT, " +
                COL_DESC + " TEXT, " +
                COL_HORARIO + " TEXT, " +
                COL_FOTO + " TEXT)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_INCIDENTES);
        onCreate(db);
    }

    public boolean insertarIncidente(Incidente incidente) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(COL_ID, incidente.getIdDelito());
        contentValues.put(COL_LAT, incidente.getLatitud());
        contentValues.put(COL_LON, incidente.getLongitud());
        contentValues.put(COL_TIPO, incidente.getTipoCrimen());
        contentValues.put(COL_DESC, incidente.getDescripcionDelincuente());
        contentValues.put(COL_HORARIO, incidente.getHorario());
        contentValues.put(COL_FOTO, incidente.getRutaFoto());

        long result = db.insert(TABLE_INCIDENTES, null, contentValues);
        return result != -1;
    }

    public List<Incidente> obtenerTodosLosIncidentes() {
        List<Incidente> lista = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_INCIDENTES, null);

        if (cursor.moveToFirst()) {
            do {
                Incidente inc = new Incidente();
                inc.setIdDelito(cursor.getString(0));
                inc.setLatitud(cursor.getDouble(1));
                inc.setLongitud(cursor.getDouble(2));
                inc.setTipoCrimen(cursor.getString(3));
                inc.setDescripcionDelincuente(cursor.getString(4));
                inc.setHorario(cursor.getString(5));
                inc.setRutaFoto(cursor.getString(6));
                lista.add(inc);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return lista;
    }

    public boolean actualizarDescripcion(String id, String nuevaDesc) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(COL_DESC, nuevaDesc);
        int result = db.update(TABLE_INCIDENTES, contentValues, COL_ID + "=?", new String[]{id});
        return result > 0;
    }

    public boolean eliminarIncidente(String id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_INCIDENTES, COL_ID + "=?", new String[]{id});
        return result > 0;
    }
}