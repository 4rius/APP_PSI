package com.example.app_psi.activities

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.ui.AppBarConfiguration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.app_psi.R
import com.example.app_psi.adapters.DeviceListAdapter
import com.example.app_psi.databinding.ActivityMainBinding
import com.example.app_psi.services.NetworkService
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == NetworkService.ACTION_SERVICE_CREATED) {
                if (NetworkService.getStatus() == "Connected") {
                    connected()
                } else {
                    notConnected()
                }
                binding.textViewNetworkId.text = "Network ID: ${NetworkService.getNode()?.id}"
                binding.textViewNetworkPort.text = "Network port: ${NetworkService.getNode()?.port}"
            } else if (intent.action == NetworkService.ACTION_STATUS_UPDATED) {
                binding.textViewNetworkStatus.text = "Network status: ${NetworkService.getStatus()}"
                setupRecyclerView()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        registerReceiver(receiver, IntentFilter(NetworkService.ACTION_SERVICE_CREATED), RECEIVER_EXPORTED)
        registerReceiver(receiver, IntentFilter(NetworkService.ACTION_STATUS_UPDATED), RECEIVER_EXPORTED)
        startNetworkService()
        setupFloatingActionButton()

        binding.textViewNoDevices.visibility = View.VISIBLE

    }

    private fun setupFloatingActionButton() {
        // Launch the bottom sheet when the FAB is clicked
        binding.fabButton.setOnClickListener {
            val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_options, null)
            val bottomSheetDialog = BottomSheetDialog(this)
            bottomSheetDialog.setContentView(bottomSheetView)
            val height = resources.displayMetrics.heightPixels * 0.5
            bottomSheetDialog.behavior.peekHeight = height.toInt()
            bottomSheetDialog.behavior.isDraggable = true

            // Interfaz de la bottom sheet
            val textViewDetails = bottomSheetView.findViewById<TextView>(R.id.textViewDetails)
            val intersectEveryonePaillier = bottomSheetView.findViewById<Button>(R.id.buttonSendLargeMsg)
            val intersectEveryoneElGamal = bottomSheetView.findViewById<Button>(R.id.buttonSendSmallMsg)
            val buttonConnect = bottomSheetView.findViewById<Button>(R.id.buttonConnect)
            val buttonDisconnect = bottomSheetView.findViewById<Button>(R.id.buttonDisconnect)
            val buttonMyKeys = bottomSheetView.findViewById<Button>(R.id.buttonMyKeys)
            val buttonMyData = bottomSheetView.findViewById<Button>(R.id.buttonDataSet)
            val buttonResults = bottomSheetView.findViewById<Button>(R.id.buttonResults)


            // Configuración de los detalles
            textViewDetails.text = buildString {
            append("Full network identifier: ")
            append(NetworkService.getNode()?.fullId)
            append(":")
            append(NetworkService.getNode()?.port)
            append("\nNetwork status: ")
            append(NetworkService.getStatus())
            }

            // Configuración de los botones
            intersectEveryonePaillier.setOnClickListener {
                NetworkService.sendLargeMessageToAll()
                bottomSheetDialog.dismiss()
                Snackbar.make(binding.root, "Finding every intersection using Paillier encryption...", Snackbar.LENGTH_SHORT).show()
            }

            intersectEveryoneElGamal.setOnClickListener {
                NetworkService.sendSmallMessageToAll()
                bottomSheetDialog.dismiss()
                Snackbar.make(binding.root, "Finding every intersection using ElGamal encryption...", Snackbar.LENGTH_SHORT).show()
            }

            buttonConnect.setOnClickListener {
                NetworkService.findNetwork()
                bottomSheetDialog.dismiss()
                Snackbar.make(binding.root, "Finding and connecting to the peers", Snackbar.LENGTH_SHORT).show()
            }

            buttonDisconnect.setOnClickListener {
                NetworkService.disconnect()
                bottomSheetDialog.dismiss()
                Snackbar.make(binding.root, "Disconnecting from the peers", Snackbar.LENGTH_SHORT).show()
            }

            buttonMyKeys.setOnClickListener {
                val intent = Intent(this, KeysActivity::class.java)
                val publicKey = NetworkService.getNode()?.paillier?.n.toString()
                val privateKey = NetworkService.getNode()?.paillier?.lambda.toString()
                intent.putExtra("publicKey", publicKey)
                intent.putExtra("privateKey", privateKey)
                startActivity(intent)
                bottomSheetDialog.dismiss()
            }

            buttonMyData.setOnClickListener {
                val intent = Intent(this, KeysActivity::class.java)
                val dataset = NetworkService.getNode()?.myData?.toString()
                intent.putExtra("dataset", dataset)
                startActivity(intent)
                bottomSheetDialog.dismiss()
            }

            buttonResults.setOnClickListener {
                val intent = Intent(this, KeysActivity::class.java)
                val results = NetworkService.getNode()?.results?.toString()
                intent.putExtra("results", results)
                startActivity(intent)
                bottomSheetDialog.dismiss()
            }

            bottomSheetDialog.show()
        }
    }

    private fun startNetworkService() {
        startService(Intent(this, NetworkService::class.java))
        binding.textViewNetworkStatus.text = getString(R.string.network_status_connecting_string)
        binding.textViewNetworkStatus.setTextColor(Color.YELLOW)
    }

    private fun setupRecyclerView() {
        binding.swiperefresh.setOnRefreshListener {
            setupRecyclerView()
            binding.swiperefresh.isRefreshing = false
        }
        val deviceList: MutableList<String> = (NetworkService.getNode()?.peers ?: listOf()).toMutableList()
        if (deviceList.isEmpty()) {
            binding.textViewNoDevices.visibility = View.VISIBLE
            return
        }
        binding.textViewNoDevices.visibility = View.GONE
        for (i in deviceList.indices) {
            deviceList[i] = deviceList[i].substringBeforeLast(":")
        }
        val adapter = DeviceListAdapter(this, deviceList, binding.root)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.setHasFixedSize(true)
    }

    private fun notConnected() {
        binding.textViewNetworkStatus.animate().alpha(0f).setDuration(500).withEndAction {
            binding.textViewNetworkStatus.text = "Network status: ${NetworkService.getStatus()}"
            binding.textViewNetworkStatus.setTextColor(Color.RED)
            binding.textViewNetworkStatus.animate().alpha(1f).setDuration(500).start()
        }
        Snackbar.make(binding.root, "Couldn't find or connect to a network", Snackbar.LENGTH_SHORT).show()
    }

    private fun connected() {
        binding.textViewNetworkStatus.animate().alpha(0f).setDuration(500).withEndAction {
            binding.textViewNetworkStatus.text = "Network status: ${NetworkService.getStatus()}"
            binding.textViewNetworkStatus.setTextColor(Color.GREEN)
            binding.textViewNetworkStatus.animate().alpha(1f).setDuration(500).start()
        }
        Snackbar.make(binding.root, "Found a network and connected to the peers", Snackbar.LENGTH_SHORT).show()
        setupRecyclerView()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        val filter = IntentFilter()
        filter.addAction(NetworkService.ACTION_SERVICE_CREATED)
        filter.addAction(NetworkService.ACTION_STATUS_UPDATED)
        registerReceiver(receiver, filter, RECEIVER_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }
}