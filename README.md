SafeZone - Aplicacion Movil de Seguridad Ciudadana

SafeZone es una aplicacion movil para Android desarrollada en Java. Permite visualizar zonas de riesgo delictual en tiempo real, reportar incidentes con ubicacion exacta, trazar rutas peatonales seguras y activar una alerta SOS dirigida a contactos de confianza.


Funcionalidades principales

Mapa interactivo con semaforo de riesgo:
Agrupa los reportes cercanos en un radio de 150 metros, ampliando la zona y cambiando el nivel de alerta segun la cantidad de incidentes registrados:
- Verde: 1 reporte (Precaucion)
- Amarillo: 2 reportes (Alerta)
- Naranja: 3 a 4 reportes (Peligro)
- Rojo: 5 o mas reportes (Zona Critica)
- Gris: reportes con mas de 24 horas de antiguedad

Filtros de tiempo:
Permite alternar entre los incidentes en vivo (ultimas 24 horas) y el historial completo.

Reporte de incidentes y gestion en la nube:
Al mantener presionado en el mapa se puede crear un reporte con la ubicacion GPS real del dispositivo. Incluye seleccion del tipo de delito, horario, descripcion y opcion para adjuntar una fotografia. Los datos se sincronizan en tiempo real con Firebase Cloud Firestore, permitiendo crear, consultar, editar la descripcion y eliminar reportes con confirmacion.

Buscador de sectores y ruta segura:
Cuenta con autocompletado de calles y direcciones que evalua de inmediato el nivel de riesgo del lugar consultado. Ademas, calcula hasta 3 rutas peatonales alternativas mediante OSRM y selecciona la opcion que evita o reduce el paso por zonas de peligro.

Protocolo de emergencia SOS:
Obtiene la ubicacion actual por GPS, envia un SMS con el enlace directo de Google Maps a todos los contactos de emergencia registrados e inicia una llamada automatica al contacto principal.

Agenda de contactos de emergencia:
Permite agregar y administrar de forma local a las personas de confianza para el envio de alertas rapidas.


Tecnologias utilizadas

Lenguaje: Java (Java 11)
Interfaz: Android Views / XML, Material Design y ViewBinding
SDK de Android: minSdk 24 (Android 7.0), compileSdk 36 y targetSdk 36
Base de datos: Firebase Cloud Firestore (BOM 32.7.0)
Mapas: OSMDroid (6.1.18) con tiles de OSMFrance
Geolocalizacion: Google Play Services Location (21.2.0 - FusedLocationProviderClient)
Geocodificacion: Photon API (Komoot)
Enrutamiento: OSRM (Open Source Routing Machine)
Almacenamiento local: SharedPreferences en formato JSON para los contactos de emergencia


Estructura del proyecto

Ubicacion: app/src/main/java/com/example/safezone/

MainActivity.java: controlador principal del mapa, operaciones con Firestore, rutas OSRM y protocolo SOS.
ReportActivity.java: formulario para registrar reportes y capturar fotografias.
ContactosActivity.java: gestion de contactos de emergencia.
Incidente.java: modelo de datos para los incidentes.
Contacto.java: modelo de datos para los contactos de emergencia.
ResultadoDireccion.java: modelo geografico para los resultados del buscador.
DireccionAdapter.java: adaptador encargado del autocompletado de direcciones.