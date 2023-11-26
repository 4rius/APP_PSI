package com.example.app_psi.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.ui.AppBarConfiguration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.app_psi.adapters.DeviceListAdapter
import com.example.app_psi.databinding.ActivityMainBinding
import com.example.app_psi.services.NetworkService

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startNetworkService()
        setupRecyclerView()

    }

    private fun startNetworkService() {
        startService(Intent(this, NetworkService::class.java))
        binding.textViewNetworkStatus.text = "Network status: " + NetworkService.getStatus()
    }

    private fun setupRecyclerView() {
        val deviceList: List<String> = NetworkService.getNode()?.peers ?: listOf()
        val adapter = DeviceListAdapter(this, deviceList, binding.root)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.setHasFixedSize(true)
    }
}