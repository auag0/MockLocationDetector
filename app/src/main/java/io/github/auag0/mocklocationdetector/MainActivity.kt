package io.github.auag0.mocklocationdetector

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import io.github.auag0.mocklocationdetector.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            updateRequestButtonIcons()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        with(binding) {
            runDetection.setOnClickListener { viewModel.runDetection(this@MainActivity) }
            requestCoarsePermission.setOnClickListener { requestPermission(ACCESS_COARSE_LOCATION) }
            requestFinePermission.setOnClickListener { requestPermission(ACCESS_FINE_LOCATION) }
            openPermissionSettings.setOnClickListener { openPermissionSettings() }

            results.adapter = ResultAdapter()
            results.layoutManager = LinearLayoutManager(this@MainActivity)
        }

        viewModel.results.observe(this) {
            val adapter = binding.results.adapter as? ResultAdapter
            adapter?.results = it
        }
    }

    private fun requestPermission(permissionName: String) {
        if (shouldShowRequestPermissionRationale(permissionName)) {
            requestPermission.launch(permissionName)
        } else {
            if (!isGrantedPermission(permissionName)) {
                showPleaseGrantLocationPermission()
            }
        }
    }

    private fun showPleaseGrantLocationPermission() {
        Snackbar.make(
            binding.root,
            "Please grant location permission from permission settings",
            Snackbar.LENGTH_LONG
        ).setAction("open") {
            openPermissionSettings()
        }.show()
    }

    private fun openPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        updateRequestButtonIcons()
    }

    private fun updateRequestButtonIcons() {
        updateRequestButtonIcon(
            ACCESS_FINE_LOCATION,
            binding.requestFinePermission
        )
        updateRequestButtonIcon(
            ACCESS_COARSE_LOCATION,
            binding.requestCoarsePermission
        )
    }

    private fun updateRequestButtonIcon(permissionName: String, button: MaterialButton) {
        val iconResId = when (isGrantedPermission(permissionName)) {
            true -> R.drawable.ic_check_circle
            false -> R.drawable.ic_error_circle
        }
        button.setIconResource(iconResId)
    }

    private fun isGrantedPermission(permissionName: String): Boolean {
        return checkSelfPermission(permissionName) == PackageManager.PERMISSION_GRANTED
    }
}