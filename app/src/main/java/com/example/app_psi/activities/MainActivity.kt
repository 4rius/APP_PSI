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
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.app_psi.collections.DbConstants.ACTION_SERVICE_CREATED
import com.example.app_psi.collections.DbConstants.ACTION_STATUS_UPDATED
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
                    clearRV()
                    notConnected()
                }
                binding.textViewNetworkId.text =
                    getString(R.string.network_id, NetworkService.getNode()?.id)
                binding.textViewNetworkPort.text =
                    getString(R.string.network_port, NetworkService.getNode()?.port.toString())
            } else if (intent.action == ACTION_STATUS_UPDATED) {
                if (NetworkService.getStatus() == "Connected") {
                    connected()
                } else {
                    clearRV()
                    notConnected()
                }
                setupRecyclerView()
            }
        }
    }

    private fun clearRV() {
        binding.recyclerView.adapter = null
        binding.textViewNoDevices.visibility = View.VISIBLE
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

    @SuppressLint("InflateParams", "StringFormatMatches")
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
            val buttonConnect = bottomSheetView.findViewById<Button>(R.id.buttonConnect)
            val buttonDisconnect = bottomSheetView.findViewById<Button>(R.id.buttonDisconnect)
            val buttonMyKeys = bottomSheetView.findViewById<Button>(R.id.buttonMyKeys)
            val buttonMyData = bottomSheetView.findViewById<Button>(R.id.buttonDataSet)
            val buttonResults = bottomSheetView.findViewById<Button>(R.id.buttonResults)
            val buttonAddPeer = bottomSheetView.findViewById<Button>(R.id.buttonAddPeer)
            val buttonDiscoverPeers = bottomSheetView.findViewById<Button>(R.id.buttonDiscoverPeers)
            val buttonGenerateKeys = bottomSheetView.findViewById<Button>(R.id.buttonGenerateKeys)
            val buttonChangeSetup = bottomSheetView.findViewById<Button>(R.id.buttonChangeSetup)


            // Configuración de los detalles
            textViewDetails.text = buildString {
                append(getString(R.string.full_network_identifier))
                append(NetworkService.getNode()?.fullId)
                append(":")
                append(NetworkService.getNode()?.port)
                append(getString(R.string.network_status_str))
                append(NetworkService.getStatus())
            }

            // Configuración de los botones
            buttonConnect.setOnClickListener {
                bottomSheetDialog.dismiss()

                val progressDialog = ProgressDialog(this)
                progressDialog.setMessage(getString(R.string.starting_node))
                progressDialog.setCancelable(false)
                progressDialog.show()

                // No bloquear el hilo principal
                Thread {
                    NetworkService.connect()

                    runOnUiThread {
                        progressDialog.dismiss()
                        Snackbar.make(binding.root,
                            getString(R.string.finding_and_connecting_to_the_peers), Snackbar.LENGTH_SHORT).show()
                    }
                }.start()
            }

            buttonDisconnect.setOnClickListener {
                NetworkService.disconnect()
                bottomSheetDialog.dismiss()
                Snackbar.make(binding.root,
                    getString(R.string.node_destroyed), Snackbar.LENGTH_SHORT).show()
            }

            buttonMyKeys.setOnClickListener {
                val intent = Intent(this, KeysActivity::class.java)
                val publicKeyPaillier = NetworkService.getNode()?.getPublicKey("Paillier")
                val publicKeyDamgardJurik = NetworkService.getNode()?.getPublicKey("DamgardJurik")
                val pubkeys = "Paillier: $publicKeyPaillier\nDamgard Jurik: $publicKeyDamgardJurik"
                intent.putExtra("publicKey", pubkeys)
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

                builder.setTitle(getString(R.string.add_specific_peer))

                builder.setPositiveButton(getString(R.string.add)) { _, _ ->
                    val peer = editText.text.toString()
                    NetworkService.addPeer(peer)
                    setupRecyclerView()
                    bottomSheetDialog.dismiss()
                    Snackbar.make(binding.root,
                        getString(R.string.added_peer, peer), Snackbar.LENGTH_SHORT).show()
                }

                builder.setNegativeButton(getString(R.string.cancel_str)) { dialog, _ ->
                    dialog.cancel()
                    bottomSheetDialog.dismiss()
                }

                builder.show()
            }

            buttonDiscoverPeers.setOnClickListener {
                bottomSheetDialog.dismiss()

                val progressDialog = ProgressDialog(this)
                progressDialog.setMessage(getString(R.string.discovering_peers))
                progressDialog.setCancelable(false)
                progressDialog.show()

                // Don't block the UI thread
                Thread {
                    Looper.prepare()
                    NetworkService.discoverPeers()

                    runOnUiThread {
                        progressDialog.dismiss()
                        setupRecyclerView()
                        Snackbar.make(binding.root,
                            getString(R.string.peer_list_updated), Snackbar.LENGTH_SHORT).show()
                    }
                }.start()
            }

            buttonGenerateKeys.setOnClickListener {
                val builder = AlertDialog.Builder(this)
                val inflater = layoutInflater
                builder.setTitle(getString(R.string.generate_keys))

                val dialogLayout = inflater.inflate(R.layout.keygen_dialog, null)
                val spinnerImplementation = dialogLayout.findViewById<android.widget.Spinner>(R.id.spinnerImplementation)
                val editTextBitLength = dialogLayout.findViewById<EditText>(R.id.editTextBitLength)

                builder.setView(dialogLayout)
                builder.setPositiveButton(getString(R.string.generate)) { _, _ ->
                    val scheme = spinnerImplementation.selectedItem.toString()
                    val bitLength = editTextBitLength.text.toString().toInt()
                    if (bitLength < 16) {
                        Toast.makeText(this, getString(R.string.bit_length_too_small), Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    newKeys(scheme, bitLength)
                    bottomSheetDialog.dismiss()
                }
                builder.setNegativeButton(getString(R.string.cancel_str)) { dialog, _ ->
                    dialog.cancel()
                    bottomSheetDialog.dismiss()
                }
                builder.show()
            }

            buttonChangeSetup.setOnClickListener {
                val builder = AlertDialog.Builder(this)
                val inflater = layoutInflater
                builder.setTitle(getString(R.string.network_settings))

                val dialogLayout = inflater.inflate(R.layout.custom_alert_dialog, null)
                val editTextDomainSize  = dialogLayout.findViewById<EditText>(R.id.editTextDomainSize)
                val editTextSetSize  = dialogLayout.findViewById<EditText>(R.id.editTextSetSize)

                builder.setView(dialogLayout)
                builder.setPositiveButton("OK") { _, _ ->
                    val domainSize = editTextDomainSize.text.toString().toInt()
                    val setSize = editTextSetSize.text.toString().toInt()
                    NetworkService.getNode()?.modifySetup(domainSize, setSize)
                    Snackbar.make(binding.root,
                        getString(R.string.new_setup_domain_size_set_size, domainSize, setSize), Snackbar.LENGTH_SHORT).show()
                    bottomSheetDialog.dismiss()
                }
                builder.show()
            }



            bottomSheetDialog.show()
        }
    }

    private fun newKeys(scheme: String, bitLength: Int) {
        when (scheme) {
            "Paillier" -> {
                NetworkService.keygen("Paillier", bitLength)
            }
            "Damgard Jurik" -> {
                NetworkService.keygen("DamgardJurik", bitLength)
            }
        }
        Snackbar.make(binding.root,
            getString(R.string.new_keys_are_being_generated, scheme, bitLength), Snackbar.LENGTH_SHORT)
            .show()
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
        Snackbar.make(binding.root, getString(R.string.no_netowrk), Snackbar.LENGTH_SHORT).show()
    }

    private fun connected() {
        binding.textViewNetworkStatus.animate().alpha(0f).setDuration(500).withEndAction {
            binding.textViewNetworkStatus.text =
                getString(R.string.network_status, NetworkService.getStatus())
            binding.textViewNetworkStatus.setTextColor(Color.GREEN)
            binding.textViewNetworkStatus.animate().alpha(1f).setDuration(500).start()
        }
        Snackbar.make(binding.root, getString(R.string.node_started), Snackbar.LENGTH_SHORT).show()
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