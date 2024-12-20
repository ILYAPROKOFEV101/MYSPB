package com.ilya.MeetingMap.Mine_menu



import MapMarker_DATA
import Postquashen
import android.Manifest
import android.content.Intent

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.asLiveData
import bitmapDescriptorFromVector
import com.bumptech.glide.Glide
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.RoundCap
import com.ilya.MeetingMap.Map.DataModel.Friends_type
import com.ilya.myspb.android.Media.MediaActivity

import com.ilya.myspb.android.R
import com.ilya.nordmap.Map.DataModel.AIanswer
import com.ilya.nordmap.Map.DataModel.extractRussianLettersWithSpaces

import com.ilya.nordmap.Map.Openmarkers_map
import com.ilya.nordmap.Media.DataModel.Formattoken
import com.ilya.nordmap.Media.ServerAPI.sendNotificationToCloud


import decodePoly
import getMapRoute


import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch



import java.io.IOException


@OptIn(ExperimentalPermissionsApi::class)
class Map_Activity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnPolylineClickListener, GoogleMap.OnMapClickListener
{


    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var polylineOptions: PolylineOptions
    private var speedTextView: TextView? = null
    private var distanceTextView: TextView? = null
    private var totalDistance: Double = 0.0
    private var lastLocation: Location? = null
    private var speedUnit = "KM/H"
    private val updateSpeedHandler = Handler()
    private var destinationMarker: Marker? = null
    private lateinit var polyline: Polyline

    private var currentDialog: AlertDialog? = null
    private val collectedFriends = mutableListOf<Friends_type>()

    var currentLatLngGlobal by mutableStateOf<LatLng>(LatLng(0.0, 0.0))
    var routePoints by mutableStateOf<LatLng>(LatLng(0.0, 0.0))
    private val markerDataMap = mutableMapOf<Marker, MapMarker_DATA>()

    private var GIGCHAT_TOKEN by mutableStateOf<Formattoken>(Formattoken("", 0))



    private companion object {
        private const val MY_PERMISSIONS_REQUEST_LOCATION = 1
    }
    private lateinit var backgroundHandler: Handler
    private lateinit var handlerThread: HandlerThread





    override fun onCreate(savedInstanceState: Bundle?)  {
        super.onCreate(savedInstanceState)
       setContentView(R.layout.activity_map)



        Log.d("URL_GET_MAKER", "${currentLatLngGlobal.latitude} and ${currentLatLngGlobal.longitude}")

        supportActionBar?.hide()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        // Пример вызова этой функции из корутины

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            initializeMap()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                MY_PERMISSIONS_REQUEST_LOCATION
            )
        }

        // получение токена
        GlobalScope.launch(Dispatchers.IO) {
            try {
                GIGCHAT_TOKEN = sendNotificationToCloud()
                // Получаем access_token из ответа

                Log.d("Igot_new_Token:","$GIGCHAT_TOKEN")
                Log.d("Igot_new_Token:","${GIGCHAT_TOKEN.access_token}")

                //val answer =  Postquashen(GIGCHAT_TOKEN.access_token)
               // Log.d("Igot_new_Token:","$answer")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }


    }

    private var isItemDecorationAdded = false // Флаг

        // Для отрисовки меток

        // Вызов алерт диалог , для тогочтобы показать друга
    // Реализация метода интерфейса WebSocketCallback


    fun onFindLocation(lat: Double, lon: Double) {
        findLocation_mark(lat, lon) // Вызов функции перемещения камеры
        routePoints = LatLng(lat, lon)
    }

    private var currentPolyline: Polyline? = null

    fun findLocation_route() {
        CoroutineScope(Dispatchers.Main).launch {
            val routeGeometry = getMapRoute(currentLatLngGlobal.latitude, currentLatLngGlobal.longitude, routePoints.latitude, routePoints.longitude)
            routeGeometry?.let {
                val routePoints = decodePoly(it)

                // Удаляем предыдущий маршрут, если он существует
                currentPolyline?.remove()

                // Добавляем новый маршрут
                currentPolyline = addRouteToMap(routePoints, 5)
            }
            Log.d("MapRoute", "Route geometry: $routeGeometry")
        }
    }

    private fun initializeMap() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        polylineOptions = PolylineOptions()
    }



    private var isSatelliteView = false // Переменная для отслеживания состояния карты

    fun onMapTypeButtonClick(view: View) {
        val mapButton = view as ImageButton

        if (isSatelliteView) {
            // Переключаемся на обычный вид
            mMap.mapType = GoogleMap.MAP_TYPE_NORMAL
            mapButton.setImageResource(R.drawable.map_24px) // Устанавливаем иконку для обычного вида
        } else {
            // Переключаемся на спутниковый вид
            mMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
            mapButton.setImageResource(R.drawable.satellite) // Устанавливаем иконку для спутникового вида
        }

        // Инвертируем состояние
        isSatelliteView = !isSatelliteView
    }


    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
        mMap.uiSettings.isMapToolbarEnabled = true
        mMap.isTrafficEnabled = false
        mMap.isBuildingsEnabled = true
        mMap.isIndoorEnabled = true

        val styleOptions = MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style)
        mMap.setMapStyle(styleOptions)

        val locationAutoCompleteTextView = findViewById<AutoCompleteTextView>(R.id.locationAutoCompleteTextView)
        val findButton = findViewById<ImageView>(R.id.findButton)

        val Plot_route = findViewById<ImageButton>(R.id.RoutingButton)

        addAllMarkers(Openmarkers_map)

        /*socialbutton.setOnClickListener{
            val intent = Intent(this, SocialMapActivity::class.java)
            startActivity(intent)
        }*/



        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line)
        locationAutoCompleteTextView.setAdapter(adapter)

        locationAutoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
            val selectedItem = adapter.getItem(position).toString()
            findLocation(selectedItem)
        }

        findButton.setOnClickListener {
            val locationText = locationAutoCompleteTextView.text.toString()
            if (locationText.isNotEmpty()) {
                findLocation(locationText)
            } else {
                Toast.makeText(this, "Please enter a location", Toast.LENGTH_SHORT).show()
            }
        }



        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true

            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    location?.let {
                        val currentLatLng = LatLng(it.latitude, it.longitude)
                        currentLatLngGlobal = currentLatLng
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))





                        updateSpeed(it.speed)
                        updateDistance(it)
                        // новый поток
                        mMap.setOnMarkerClickListener { marker ->
                            val markerId = marker.title?.toIntOrNull()

                            // Проверяем, если id маркера валиден
                            if (markerId != null) {
                                // Выполняем операции в главном потоке
                                runOnUiThread {
                                    val mapMarker = Openmarkers_map.find { it.id == markerId }

                                    if (mapMarker != null) {
                                        showMarkerDialog(mapMarker)
                                        Log.d("MarkerData", "Clicked on marker: $mapMarker")
                                    } else {
                                        val errorMessage = "No data found for marker with ID: $markerId"
                                        Log.w("MarkerData", errorMessage)
                                        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                // Если id маркера неверен, выводим ошибку
                                val errorMessage = "Marker title is not a valid ID: ${marker.title}"
                                Log.w("MarkerData", errorMessage)
                                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                            }
                            true // Возвращаем true, чтобы подавить всплывающее окно
                        }





                        // Установка обработчика кликов по карте
                        mMap.setOnMapClickListener { latLng ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                //showAddMarkerDialog(latLng, this, uid_main, this)
                            }
                        }

                        // Инициализация объекта Polyline
                        polyline = mMap.addPolyline(PolylineOptions().width(5f).color(Color.BLUE))


                        var isRouteDrawn = false
                        Plot_route.setOnClickListener {
                            if (isRouteDrawn) {
                                currentPolyline?.remove()
                                removeMarkers()
                                isRouteDrawn = false
                            } else {
                                findLocation_route()
                                isRouteDrawn = true
                            }
                        }

                    }
                }
            }, 200) // Задержка в 0.2 секунду перед выполнением кода
        }
    }


    private val markerList = mutableListOf<Marker>()  // Список для сохранения маркеров

    private fun addRouteToMap(routePoints: List<LatLng>, circleSpacing: Int): Polyline {
        // Удаляем предыдущую полилинию, если она существует
        currentPolyline?.remove()

        // Удаляем все маркеры с карты
        removeMarkers()

        // Создаем полилинию для маршрута
        val polylineOptions = PolylineOptions()
            .addAll(routePoints)
            .width(12f)
            .color(Color.parseColor("#4285F4"))  // Основной цвет линии
            .geodesic(true)               // Сглаживание углов
            .startCap(RoundCap())         // Закругление начала линии
            .endCap(RoundCap())           // Закругление конца линии
            .jointType(JointType.ROUND)   // Закругление соединений между линиями

        // Добавляем полилинию на карту и сохраняем её в currentPolyline
        currentPolyline = mMap.addPolyline(polylineOptions)

        // Получаем уменьшенный Bitmap для кружков
        val customCircleBitmap = getResizedBitmap(R.drawable.custom_circle, 20, 20)  // Уменьшаем до 20x20 пикселей

        // Добавляем кастомные кружки с регулируемым расстоянием
        for (i in routePoints.indices step circleSpacing) {
            val point = routePoints[i]
            val marker = mMap.addMarker(
                MarkerOptions()
                    .position(point)
                    .icon(customCircleBitmap)  // Используем уменьшенное изображение кружка
                    .anchor(0.5f, 0.5f)  // Центр маркера совпадает с точкой маршрута
            )
            // Добавляем маркер в список для дальнейшего удаления
            markerList.add(marker!!)
        }

        // Возвращаем новую полилинию
        return currentPolyline!!
    }

    // Удаление всех маркеров с карты
    private fun removeMarkers() {
        for (marker in markerList) {
            marker.remove()
        }
        // Очищаем список маркеров
        markerList.clear()
    }

    // Функция для изменения размера Bitmap
    private fun getResizedBitmap(drawableId: Int, width: Int, height: Int): BitmapDescriptor {
        val drawable = ContextCompat.getDrawable(this, drawableId)
        val bitmap = Bitmap.createBitmap(
            drawable!!.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        // Создаем уменьшенный bitmap
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false)
        return BitmapDescriptorFactory.fromBitmap(resizedBitmap)
    }



    private fun showMarkerDialog(marker: MapMarker_DATA) {
        // Вызов диалога в главном потоке
        runOnUiThread {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view_marker, null)
            val marker_name = dialogView.findViewById<TextView>(R.id.marker_name)
            val marker_image = dialogView.findViewById<ImageView>(R.id.marker_image)
            val marker_coordinates = dialogView.findViewById<TextView>(R.id.marker_coordinates)
            val type = dialogView.findViewById<TextView>(R.id.type)
            val visit_time = dialogView.findViewById<TextView>(R.id.visit_time)
            val Aitext = dialogView.findViewById<TextView>(R.id.Aitext)
            val marker_description = dialogView.findViewById<TextView>(R.id.marker_description)
            val Plot_route = dialogView.findViewById<ImageButton>(R.id.find)
            val AiButton = dialogView.findViewById<ImageButton>(R.id.custom_button)

            // Установка данных маркера в элементы диалога
            marker_name.text = marker.name
            marker_coordinates.text = "Координаты :${marker.lat} ${marker.lon}"
            marker_description.text = marker.description
            type.text = marker.type
            visit_time.text = "${getString(R.string.Visittime)}: ${marker.visitTime}"

            routePoints = LatLng(marker.lat, marker.lon)

            var isRouteDrawn = false

            // Получение токена и обработка запроса в фоне
            AiButton.setOnClickListener {
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        GIGCHAT_TOKEN
                        Log.d("Postquashen", GIGCHAT_TOKEN.access_token)
                        // Получаем access_token из ответа
                        val responseBody = Postquashen(GIGCHAT_TOKEN.access_token, "${marker.queryPrompt}")

                        Log.d("Postquashen_Activity", "до парсинга $responseBody")
                        val aiAnswer = extractRussianLettersWithSpaces(responseBody)
                        Log.d("Postquashen_Activity", "после парсинга $aiAnswer")

                        // Обновление UI в главном потоке
                        runOnUiThread {
                            Aitext.text = aiAnswer
                        }

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            Log.d("MarkerDialog", "Marker name: $routePoints")

            Glide.with(this)
                .load(marker.imageUrl)  // Загрузка изображения по URL
                .into(marker_image)  // Установка изображения в ImageView

            val builder = AlertDialog.Builder(this)
            builder.setView(dialogView)

            // Создание и показ диалога
            val dialog = builder.create()
            dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_background)
            dialog.show()

            val window = dialog.window
            window?.let {
                it.setBackgroundDrawableResource(R.drawable.rounded_background)

                // Устанавливаем размеры окна напрямую
                val params = it.attributes
                params.width = WindowManager.LayoutParams.MATCH_PARENT  // Ширина в пикселях
                params.height = 1800 // Высота в пикселях

                it.attributes = params
            }

            Plot_route.setOnClickListener {
                if (isRouteDrawn) {
                    currentPolyline?.remove()
                    removeMarkers()
                    isRouteDrawn = false
                } else {
                    findLocation_route()
                    isRouteDrawn = true
                }
                routePoints = LatLng(marker.lat, marker.lon)
                findLocation_mark(marker.lat, marker.lon)
                dialog.dismiss()
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onMapClick(latLng: LatLng) {
     //   showAddMarkerDialog(latLng, this, uid_main, this)
    }

    private fun addAllMarkers(markers: List<MapMarker_DATA>) {
        if (::mMap.isInitialized) {
            markers.forEach { markerData ->
                // Создаем объект LatLng из координат метки
                val latLng = LatLng(markerData.lat, markerData.lon)
                // Добавляем метку на карту
                addMarker(latLng, markerData)
            }
        } else {
            Log.e("MapError", "mMap не инициализирован")
        }
    }


    private fun addMarker(latLng: LatLng, markerData: MapMarker_DATA): Marker? {
        if (::mMap.isInitialized) {
            val marker = mMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(markerData.id.toString())
                    .snippet(markerData.name)
                    .icon(
                        bitmapDescriptorFromVector(
                            this@Map_Activity,
                            R.drawable.location_on_,
                            "FF5757",
                            100,
                            100
                        )
                    )
            )
            if (marker != null) {
                markerDataMap[marker] = markerData // Связываем Marker с MapMarker_DATA
            }
            return marker
        } else {
            Log.e("MapError", "mMap не инициализирован")
            return null
        }
    }


    // Добавьте метод для поиска местоположения по адресу
    private fun findLocation(address: String) {
        val geocoder = Geocoder(this)
        try {
            val results = geocoder.getFromLocationName(address, 1)
            if (results != null && results.isNotEmpty()) {
                val location = results[0]
                val latLng = LatLng(location.latitude, location.longitude)


                // Перемещение камеры к метке
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))

                routePoints = LatLng(location.latitude, location.longitude)


                if (::mMap.isInitialized) {
                    val marker = mMap.addMarker(
                        MarkerOptions()
                            .position(latLng)
                            .title(address)
                            .icon(
                                bitmapDescriptorFromVector(
                                    this@Map_Activity,
                                    R.drawable.location_on_,
                                    "FF5757",
                                    100,
                                    100
                                )
                            )
                    )
                } else {
                    Log.e("MapError", "mMap не инициализирован")
                }


            } else {
                // Обработка случая, когда результаты геокодирования пусты
                Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun findLocation_mark(lat: Double, lon: Double) {
        // Создание объекта LatLng с переданными координатами
        val latLng = LatLng(lat, lon)

        // Проверка, что карта доступна
        mMap?.let { map ->
            // Добавление метки на карту
            // Перемещение камеры к метке
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        } ?: run {
            // Обработка случая, когда карта не доступна
            Toast.makeText(this, "Map is not available", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_LOCATION -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    initializeMap()
                } else {
                    // Handle the case where the user denies location permission
                }
            }
        }
    }

    private fun addPointToPolyline(latLng: LatLng) {
        polylineOptions.add(latLng)
        mMap.addPolyline(polylineOptions)
    }

    private fun clearPolyline() {
        polylineOptions.points.clear()
        mMap.clear()
    }

    override fun onPolylineClick(polyline: Polyline) {
        // Handle the click event on the polyline if needed
    }

    private fun updateSpeed(speed: Float) {
        speedUnit = "km/h" // Установка единиц измерения в километры в час
        // Отображение скорости
        speedTextView?.text = String.format("Speed: %.2f $speedUnit", speed)
    }

    private fun updateDistance(location: Location) {
        if (lastLocation != null) {
            val distance = location.distanceTo(lastLocation!!)
            totalDistance += distance.toDouble()
            distanceTextView?.text = String.format("Distance: %.2f meters", totalDistance)
        }
        lastLocation = location
    }


    override fun onResume() {
        super.onResume()
        if (!::mMap.isInitialized) {
            initializeMap() // Инициализировать карту, если она еще не инициализирована
        }
       



    }






    override fun onDestroy() {
        super.onDestroy()
         // Останавливаем детектор, когда activity уничтожается
    }

    private fun generateUniqueRequestId(): String {
        // Генерация уникального идентификатора для запроса
        return System.currentTimeMillis().toString()
    }



}

