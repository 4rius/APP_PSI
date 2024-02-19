@file:Suppress("DEPRECATION")

package com.example.app_psi.activities

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.app_psi.DbConstants.ACTION_SERVICE_CREATED
import com.example.app_psi.DbConstants.ACTION_STATUS_UPDATED
import com.example.app_psi.R
import com.example.app_psi.adapters.DeviceListAdapter
import com.example.app_psi.databinding.ActivityMainBinding
import com.example.app_psi.services.LogService
import com.example.app_psi.services.NetworkService
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.FirebaseApp

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_SERVICE_CREATED) {
                if (NetworkService.getStatus() == "Connected") {
                    // Encendemos el LogService
                    startService(Intent(this@MainActivity, LogService::class.java))
                    connected()
                } else {
                    notConnected()
                }
                binding.textViewNetworkId.text =
                    getString(R.string.network_id, NetworkService.getNode()?.id)
                binding.textViewNetworkPort.text =
                    getString(R.string.network_port, NetworkService.getNode()?.port.toString())
            } else if (intent.action == ACTION_STATUS_UPDATED) {
                binding.textViewNetworkStatus.text =
                    getString(R.string.network_status, NetworkService.getStatus())
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

        FirebaseApp.initializeApp(this)

        registerReceiver(receiver, IntentFilter(ACTION_SERVICE_CREATED), RECEIVER_EXPORTED)
        registerReceiver(receiver, IntentFilter(ACTION_STATUS_UPDATED), RECEIVER_EXPORTED)
        startNetworkService()
        setupFloatingActionButton()

        binding.textViewNoDevices.visibility = View.VISIBLE

    }

    @SuppressLint("InflateParams")
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
            val buttonAddPeer = bottomSheetView.findViewById<Button>(R.id.buttonAddPeer)
            val buttonDiscoverPeers = bottomSheetView.findViewById<Button>(R.id.buttonDiscoverPeers)
            val buttonGenerateKeys = bottomSheetView.findViewById<Button>(R.id.buttonGenerateKeys)


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

            buttonAddPeer.setOnClickListener {
                // Show a modal alert to input the peer's IP
                val builder = AlertDialog.Builder(this)
                val editText = EditText(this)
                editText.inputType = InputType.TYPE_CLASS_TEXT
                builder.setView(editText)

                builder.setTitle("Add a specific peer")

                builder.setPositiveButton("Add") { _, _ ->
                    var peer = editText.text.toString()
                    peer += ":${NetworkService.getNode()?.port}"
                    NetworkService.getNode()?.addPeer(peer)
                    setupRecyclerView()
                    bottomSheetDialog.dismiss()
                    Snackbar.make(binding.root, "Added peer $peer", Snackbar.LENGTH_SHORT).show()
                }

                builder.setNegativeButton("Cancel") { dialog, _ ->
                    dialog.cancel()
                    bottomSheetDialog.dismiss()
                }

                builder.show()
            }

            buttonDiscoverPeers.setOnClickListener {
                bottomSheetDialog.dismiss()

                val progressDialog = ProgressDialog(this)
                progressDialog.setMessage("Discovering peers...")
                progressDialog.setCancelable(false)
                progressDialog.show()

                // Don't block the UI thread
                Thread {
                    NetworkService.discoverPeers()

                    runOnUiThread {
                        progressDialog.dismiss()
                        setupRecyclerView()
                        Snackbar.make(binding.root, "Peer list updated", Snackbar.LENGTH_SHORT).show()
                    }
                }.start()
            }

            buttonGenerateKeys.setOnClickListener {
                // Alert box asking if Paillier or ElGamal
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Choose encryption scheme")
                builder.setMessage("Choose the encryption scheme you need new keys for")
                builder.setPositiveButton("Paillier") { _, _ ->
                    bottomSheetDialog.dismiss()
                    newKeys("Paillier")
                }
                builder.setNegativeButton("Damgard Jurik") { _, _ ->
                    bottomSheetDialog.dismiss()
                    newKeys("Damgard Jurik")
                }
                builder.setNeutralButton("Cancel") { dialog, _ ->
                    dialog.cancel()
                    bottomSheetDialog.dismiss()
                }
                builder.show()
            }


            bottomSheetDialog.show()
        }
    }

    private fun newKeys(scheme: String) {
        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Generating new keys...")
        progressDialog.setCancelable(false)
        progressDialog.show()
        // Don't block the UI thread
        Thread {
            var aCode = ""
            var time: Double? = null
            when (scheme) {
                "Paillier" -> {
                    time = NetworkService.getNode()?.generatePaillierKeys()
                    aCode = "GENKEYS_PAILLIER"
                }
                "Damgard Jurik" -> {
                    time = NetworkService.getNode()?.generateDJKeys()
                    aCode = "GENKEYS_DJ"
                }
            }
            if (time != null) {
                LogService.logActivity(aCode, time, packageManager.getPackageInfo(packageName, 0).versionName)
            } else {
                LogService.logActivity("GENKEYS_ERROR", 0.0, packageManager.getPackageInfo(packageName, 0).versionName)
            }

            runOnUiThread {
                progressDialog.dismiss()
                setupRecyclerView()
                Snackbar.make(binding.root, "New $scheme keys generated", Snackbar.LENGTH_SHORT).show()
            }
        }.start()
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
            binding.textViewNetworkStatus.text =
                getString(R.string.network_status, NetworkService.getStatus())
            binding.textViewNetworkStatus.setTextColor(Color.RED)
            binding.textViewNetworkStatus.animate().alpha(1f).setDuration(500).start()
        }
        Snackbar.make(binding.root, "Couldn't find or connect to a network", Snackbar.LENGTH_SHORT).show()
    }

    private fun connected() {
        binding.textViewNetworkStatus.animate().alpha(0f).setDuration(500).withEndAction {
            binding.textViewNetworkStatus.text =
                getString(R.string.network_status, NetworkService.getStatus())
            binding.textViewNetworkStatus.setTextColor(Color.GREEN)
            binding.textViewNetworkStatus.animate().alpha(1f).setDuration(500).start()
        }
        Snackbar.make(binding.root, "Node started", Snackbar.LENGTH_SHORT).show()
        setupRecyclerView()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        val filter = IntentFilter()
        filter.addAction(ACTION_SERVICE_CREATED)
        filter.addAction(ACTION_STATUS_UPDATED)
        registerReceiver(receiver, filter, RECEIVER_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }
}