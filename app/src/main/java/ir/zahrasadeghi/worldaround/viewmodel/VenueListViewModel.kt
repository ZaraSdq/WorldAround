package ir.zahrasadeghi.worldaround.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
import ir.zahrasadeghi.worldaround.datasource.VenuesDataSourceFactory
import ir.zahrasadeghi.worldaround.model.LiveLocation
import ir.zahrasadeghi.worldaround.model.NetworkState
import ir.zahrasadeghi.worldaround.model.RecommendedItem
import ir.zahrasadeghi.worldaround.repo.LocationRepo
import ir.zahrasadeghi.worldaround.repo.VenueExploreRepo
import ir.zahrasadeghi.worldaround.util.AppConstants


class VenueListViewModel(
    private val locationRepo: LocationRepo,
    private val venueExploreRepo: VenueExploreRepo,
    application: Application
) :
    BaseAndroidViewModel(application) {

    companion object {
        private const val MIN_PLACEMENT = 100
        private const val INITIAL_LOAD_SIZE = 20
        private const val PAGE_SIZE = 10
    }

    //region Private Parameters
    private val lastLocation: Location?
        get() = locationRepo.lastLocation

    private var venuesDataSourceFactory: VenuesDataSourceFactory? = null
    private val pagingNotInitialized: Boolean
        get() = venuesDataSourceFactory == null

    private var venueItems: LiveData<PagedList<RecommendedItem>> = MutableLiveData()
    private var state: LiveData<NetworkState> = MutableLiveData()
    //endregion

    //region Public parameters
    private val _location: MutableLiveData<Location> = locationRepo.currentLocation
    val location: LiveData<Location> = _location

    private var _locationPermissionGranted = MutableLiveData<Boolean>()
    var locationPermissionGranted: LiveData<Boolean> = _locationPermissionGranted

    private var _locationSettingSatisfied = MutableLiveData<Boolean>()
    var locationSettingSatisfied: LiveData<Boolean> = _locationSettingSatisfied

    private var _needRefresh = MutableLiveData<Boolean>().apply { value = false }
    val needRefresh: LiveData<Boolean> = _needRefresh
    //endregion

    init {
        checkLocationPermission()
        initPaging()
    }

    //region Private functions
    private fun checkLocationPermission() {

        _locationPermissionGranted.value = ContextCompat.checkSelfPermission(
            getApplication<Application>().applicationContext,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    //endregion

    //region Public functions
    fun getVenueItems(): LiveData<PagedList<RecommendedItem>> = venueItems

    fun getState(): LiveData<NetworkState> = state

    fun checkLocationSettings() {

        val client: SettingsClient = LocationServices.getSettingsClient(getApplication<Application>())
        val task: Task<LocationSettingsResponse> =
            client.checkLocationSettings((_location as LiveLocation).locationSettingRequest)

        task.addOnSuccessListener {
            _locationSettingSatisfied.postValue(true)
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                _locationSettingSatisfied.postValue(false)
            }
        }
    }

    fun handlePermissionResult(requestCode: Int, grantResults: IntArray) {
        when (requestCode) {
            AppConstants.LOCATION_PERMISSIONS_REQUEST_CODE -> {
                _locationPermissionGranted.value =
                    grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    fun checkLocation() {

        _location.value?.let {
            if (lastLocation == null || lastLocation!!.distanceTo(it) > MIN_PLACEMENT) {
                locationRepo.lastLocation = it
                _needRefresh.value = true
            }
            _needRefresh.value = false
        }
    }

    private fun initPaging() {
        lastLocation?.let {

            val latLngStr = it.latitude.toString() + "," + it.longitude.toString()

            venuesDataSourceFactory =
                VenuesDataSourceFactory(latLngStr, venueExploreRepo)

            val pagedListConfig = PagedList.Config.Builder()
                .setEnablePlaceholders(false)
                .setInitialLoadSizeHint(INITIAL_LOAD_SIZE)
                .setPageSize(PAGE_SIZE)
                .build()

            venuesDataSourceFactory?.let { factory ->
                venueItems = LivePagedListBuilder(factory, pagedListConfig)
                    .build()

                state = Transformations.switchMap(factory.venuesSourceLiveData) { venueDataSource ->
                    venueDataSource.state
                }
            }
        }
    }

    fun refresh() {
        if (pagingNotInitialized) {
            initPaging()
        } else {
            venuesDataSourceFactory?.venuesSourceLiveData?.value?.invalidate()
        }
    }
    //endregion
}