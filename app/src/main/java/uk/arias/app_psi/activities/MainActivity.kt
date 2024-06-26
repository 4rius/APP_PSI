@file:Suppress("DEPRECATION")

package uk.arias.app_psi.activities

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import uk.arias.app_psi.R
import uk.arias.app_psi.adapters.DeviceListAdapter
import uk.arias.app_psi.collections.DbConstants.ACTION_SERVICE_CREATED
import uk.arias.app_psi.collections.DbConstants.ACTION_STATUS_UPDATED
import uk.arias.app_psi.databinding.ActivityMainBinding
import uk.arias.app_psi.services.LogService
import uk.arias.app_psi.services.NetworkService
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.FirebaseApp
import uk.arias.app_psi.BuildConfig

class MainActivity : AppCompatActivity() {

    private lateinit var handler: Handler
    private lateinit var runnable: Runnable
    private lateinit var binding: ActivityMainBinding


    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_SERVICE_CREATED) {
                handleServiceCreated()
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

    private fun handleServiceCreated() {
        binding.swiperefresh.isRefreshing = false
        if (NetworkService.getStatus() == getString(R.string.connected)) {
            // Encendemos el LogService, solo si no está encendido, se espera a que se inicie el NetworkService
            if (LogService.instance == null) {
                Thread.sleep(2000)
                startService(Intent(this@MainActivity, LogService::class.java))
            }
            connected()
        } else {
            clearRV()
            notConnected()
        }
        binding.textViewNetworkId.text =
            getString(R.string.network_id, NetworkService.getNode()?.id)
        binding.textViewNetworkPort.text =
            getString(R.string.network_port, NetworkService.getNode()?.port.toString())
    }

    private fun setupHandler() {
        handler = Handler()
        runnable = object : Runnable {
            override fun run() {
                val executors = NetworkService.getExecutors()
                if (executors.size < 2) {
                    Log.d("MainActivity", "No executors found")
                    return
                }
                val executor1 = executors[0]
                val executor2 = executors[1]
                if (executor1.activeCount > 0 || executor2.activeCount > 0) {
                    binding.imageViewExecutorsStatus.setImageResource(R.drawable.baseline_cloud_sync_36)
                    val completedTasks = executor1.completedTaskCount + executor2.completedTaskCount
                    val pendingTasks = executor1.taskCount + executor2.taskCount - completedTasks
                    binding.textViewTasksDone.text = getString(R.string.tasks_done, pendingTasks.toString())
                } else {
                    binding.imageViewExecutorsStatus.setImageResource(R.drawable.baseline_cloud_done_36)
                    binding.textViewTasksDone.text = getString(R.string.all_tasks_string)
                }
                // Vuelve a ejecutar el Runnable después de un segundo
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(runnable)
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

        val filter = IntentFilter()
        filter.addAction(ACTION_SERVICE_CREATED)
        filter.addAction(ACTION_STATUS_UPDATED)
        registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        startNetworkService()
        setupHandler()
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
            val textViewFirebaseStatus = bottomSheetView.findViewById<TextView>(R.id.textViewFirebaseStatus)
            val buttonConnect = bottomSheetView.findViewById<Button>(R.id.buttonConnect)
            val buttonDisconnect = bottomSheetView.findViewById<Button>(R.id.buttonDisconnect)
            val buttonMyKeys = bottomSheetView.findViewById<Button>(R.id.buttonMyKeys)
            val buttonMyData = bottomSheetView.findViewById<Button>(R.id.buttonDataSet)
            val buttonResults = bottomSheetView.findViewById<Button>(R.id.buttonResults)
            val buttonAddPeer = bottomSheetView.findViewById<Button>(R.id.buttonAddPeer)
            val buttonDiscoverPeers = bottomSheetView.findViewById<Button>(R.id.buttonDiscoverPeers)
            val buttonGenerateKeys = bottomSheetView.findViewById<Button>(R.id.buttonGenerateKeys)
            val buttonChangeSetup = bottomSheetView.findViewById<Button>(R.id.buttonChangeSetup)
            val buttonFirebase = bottomSheetView.findViewById<Button>(R.id.buttonFirebase)
            val textViewVersion = bottomSheetView.findViewById<TextView>(R.id.textViewVersion)
            updateFBButtom(buttonFirebase)


            // Configuración de los detalles
            textViewDetails.text = buildString {
                append(getString(R.string.full_network_identifier))
                append(NetworkService.getNode()?.fullId)
                append(":")
                append(NetworkService.getNode()?.port)
                append(getString(R.string.network_status_str))
                append(NetworkService.getStatus())
            }
            textViewVersion.text = getString(R.string.version_string, BuildConfig.VERSION_NAME)
            // Modo oscuro
            if (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
                textViewDetails.setTextColor(Color.WHITE)
            } else {
                textViewDetails.setTextColor(Color.BLACK)
            }

            // Configuración del estado de Firebase
            if (LogService.instance == null) {
                textViewFirebaseStatus.text = getString(R.string.log_service_not_running)
                textViewFirebaseStatus.setTextColor(Color.RED)
            } else {
                if (!LogService.instance?.propsFile!!) {
                    textViewFirebaseStatus.text = getString(R.string.firebase_not_enabled)
                    textViewFirebaseStatus.setTextColor(Color.RED)
                } else {
                    if (LogService.instance?.authenticated == true) {
                        textViewFirebaseStatus.text = getString(R.string.firebase_authenticated)
                        textViewFirebaseStatus.setTextColor(Color.GREEN)
                    } else {
                        textViewFirebaseStatus.text = getString(R.string.firebase_not_authenticated)
                        textViewFirebaseStatus.setTextColor(Color.YELLOW)
                    }
                }
            }

            // Configuración de los botones
            buttonConnect.setOnClickListener {
                val builder = AlertDialog.Builder(this)
                val editText = EditText(this)
                editText.inputType = InputType.TYPE_CLASS_NUMBER
                builder.setView(editText)
                builder.setTitle(getString(R.string.port_string_set))
                builder.setPositiveButton(getString(R.string.connect)) { _, _ ->
                    bottomSheetDialog.dismiss()
                    val port = editText.text.toString().toInt()
                    connectToNetwork(port)
                }
                builder.setNegativeButton(getString(R.string.cancel_str)) { dialog, _ ->
                    dialog.cancel()
                    bottomSheetDialog.dismiss()
                }
                builder.setNeutralButton(getString(R.string.default_port)) { _, _ ->
                    bottomSheetDialog.dismiss()
                    connectToNetwork()
                }
                builder.show()
            }

            buttonDisconnect.setOnClickListener {
                NetworkService.disconnect()
                bottomSheetDialog.dismiss()
                binding.textViewNetworkPort.text = getString(R.string.port_string)
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
                    if (editTextBitLength.text.toString().isEmpty()) {
                        Toast.makeText(this, getString(R.string.bit_length_empty), Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
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

            buttonFirebase.setOnClickListener {
                toggleFB()
                bottomSheetDialog.dismiss()
            }



            bottomSheetDialog.show()
        }
    }

    private fun updateFBButtom(buttonFirebase: Button?) {
        if (LogService.instance == null) {
            buttonFirebase?.text = getString(R.string.firebase_not_enabled)
            buttonFirebase?.isClickable = false
            buttonFirebase?.isEnabled = false
            buttonFirebase?.setBackgroundColor(Color.parseColor("#B0B0B0"))
            buttonFirebase?.setTextColor(Color.parseColor("#FFFFFF"))
            return
        }
        if (!LogService.instance?.propsFile!!) {
            buttonFirebase?.text = getString(R.string.firebase_not_enabled)
            buttonFirebase?.isClickable = false
            buttonFirebase?.isEnabled = false
            buttonFirebase?.setBackgroundColor(Color.parseColor("#B0B0B0"))
            buttonFirebase?.setTextColor(Color.parseColor("#FFFFFF"))
            return
        }
        if (LogService.instance?.authenticated == true) {
            buttonFirebase?.text = getString(R.string.firebase_stop_logging)
            buttonFirebase?.setBackgroundColor(Color.parseColor("#B00020"))
        } else {
            buttonFirebase?.text = getString(R.string.firebase_start_logging)
            buttonFirebase?.setBackgroundColor(Color.parseColor("#424bf5"))
        }
    }

    private fun newKeys(scheme: String, bitLength: Int) {
        when (scheme) {
            "Paillier" -> {
                NetworkService.keygen("Paillier", bitLength)
            }
            "DamgardJurik" -> {
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

    private fun connectToNetwork(port: Int? = null) {
        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage(getString(R.string.starting_node))
        progressDialog.setCancelable(false)
        progressDialog.show()

        // No bloquear el hilo principal
        Thread {
            Looper.prepare()
            NetworkService.connect(port)

            runOnUiThread {
                progressDialog.dismiss()
                binding.textViewNetworkPort.text =
                    getString(R.string.network_port, NetworkService.getNode()?.port.toString())
            }
        }.start()
    }

    private fun toggleFB() {
        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage(getString(R.string.firebase_authenticating))
        progressDialog.setCancelable(false)
        progressDialog.show()
        Thread {
            LogService.instance?.toggleFirebaseAuth()
            Thread.sleep(2000) // Esperar a que se actualice el estado
            runOnUiThread {
                if (LogService.instance?.authenticated == true) {
                    progressDialog.dismiss()
                    Snackbar.make(binding.root,
                        getString(R.string.firebase_authenticated), Snackbar.LENGTH_SHORT).show()
                } else {
                    progressDialog.dismiss()
                    Snackbar.make(binding.root,
                        getString(R.string.firebase_not_authenticated), Snackbar.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        val filter = IntentFilter()
        filter.addAction(ACTION_SERVICE_CREATED)
        filter.addAction(ACTION_STATUS_UPDATED)
        registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        handler.post(runnable)
        handleServiceCreated()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
        handler.removeCallbacks(runnable)
    }
}
