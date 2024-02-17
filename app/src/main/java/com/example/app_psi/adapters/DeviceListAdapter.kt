package com.example.app_psi.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.app_psi.R
import com.example.app_psi.services.NetworkService
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar

class DeviceListAdapter(private val context: Context, private val devices: List<String>, private val parentView: View): RecyclerView.Adapter<DeviceListAdapter.ViewHolder>() {

    private val deletePositions: MutableList<Int> = mutableListOf()

    // Interfaz del adaptador
    class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
        val deviceName: TextView = view.findViewById(R.id.textViewTitle)
        val deviceDetails: TextView = view.findViewById(R.id.textViewContent)
        val buttonHide: Button = view.findViewById(R.id.buttonHide)
        val buttonActions: Button = view.findViewById(R.id.buttonActions)
        val buttonPing: Button = view.findViewById(R.id.buttonPing)
        val imageViewTypeNew: View = view.findViewById(R.id.imageViewTypeNew)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.device_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        setup(holder, position)
    }

    override fun getItemCount(): Int {
        return devices.size
    }

    @SuppressLint("SetTextI18n")
    private fun setup(holder: ViewHolder, position: Int) {
        if (deletePositions.contains(position)) {
            holder.buttonHide.visibility = View.VISIBLE
            holder.buttonActions.visibility = View.VISIBLE
            holder.buttonPing.visibility = View.VISIBLE
        } else {
            holder.buttonHide.visibility = View.GONE
            holder.buttonActions.visibility = View.GONE
            holder.buttonPing.visibility = View.GONE
        }


        holder.deviceName.text = devices[position]
        holder.deviceDetails.text = "Last seen: " + NetworkService.getLastSeen(devices[position])


        holder.itemView.setOnClickListener {
            if (deletePositions.contains(position)) {
                hideButtons(position)
            } else {
                showButtons(position)
            }
        }

        holder.buttonHide.setOnClickListener {
            deletePositions.remove(position)
            notifyItemChanged(position)
        }

        holder.buttonActions.setOnClickListener {
            showBottomSheet(position)
        }

        holder.buttonPing.setOnClickListener {
            holder.buttonPing.isClickable = false
            holder.buttonPing.isEnabled = false
            holder.buttonPing.text = "Pinging..."

            Thread {
                val pingSuccessful = NetworkService.pingDevice(devices[position])
                holder.buttonPing.post {
                    if (pingSuccessful) {
                        Snackbar.make(parentView, "${devices[position]} - Ping OK", Snackbar.LENGTH_SHORT).show()
                        holder.deviceDetails.text = "Last seen: " + NetworkService.getLastSeen(devices[position])
                    } else {
                        Snackbar.make(parentView, "${devices[position]} - Ping FAIL - Device likely disconnected", Snackbar.LENGTH_SHORT).show()
                    }
                    holder.buttonPing.isClickable = true
                    holder.buttonPing.isEnabled = true
                    holder.buttonPing.text = "Ping"
                }
            }.start()
        }



        holder.imageViewTypeNew.visibility = View.GONE // TODO: This and device type

    }

    @SuppressLint("InflateParams")
    private fun showBottomSheet(position: Int) {
        val bottomSheetView = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_device_options, null)
        val bottomSheetDialog = BottomSheetDialog(context)
        bottomSheetDialog.setContentView(bottomSheetView)
        val height = context.resources.displayMetrics.heightPixels * 0.5
        bottomSheetDialog.behavior.peekHeight = height.toInt()
        bottomSheetDialog.behavior.isDraggable = true
        bottomSheetDialog.findViewById<TextView>(R.id.textViewTitleOptions)?.text = context.getString(R.string.actions_for_device, devices[position])

        bottomSheetDialog.findViewById<Button>(R.id.buttonSendLargeMsg)?.setOnClickListener {
            NetworkService.findIntersection(devices[position])
            bottomSheetDialog.dismiss()
            Snackbar.make(parentView, "Finding intersection with ${devices[position]} using Paillier", Snackbar.LENGTH_SHORT).show()
        }

        bottomSheetDialog.findViewById<Button>(R.id.buttonFindIntersectionDJ)?.setOnClickListener {
            NetworkService.findIntersectionDJ(devices[position])
            bottomSheetDialog.dismiss()
            Snackbar.make(parentView, "Finding intersection with ${devices[position]} using DamgardJurik", Snackbar.LENGTH_SHORT).show()
        }

        bottomSheetDialog.findViewById<Button>(R.id.findIntersectionPaillierOPE)?.setOnClickListener {
            NetworkService.findIntersectionPaillierOPE(devices[position])
            bottomSheetDialog.dismiss()
            Snackbar.make(parentView, "Finding intersection with ${devices[position]} using Paillier OPE", Snackbar.LENGTH_SHORT).show()
        }

        bottomSheetDialog.findViewById<Button>(R.id.findIntersectionDJOPE)?.setOnClickListener {
            NetworkService.findIntersectionDJOPE(devices[position])
            bottomSheetDialog.dismiss()
            Snackbar.make(parentView, "Finding intersection with ${devices[position]} using Paillier OPE", Snackbar.LENGTH_SHORT).show()
        }

        bottomSheetDialog.show()
    }

    private fun showButtons(position: Int) {
        deletePositions.add(position)
        notifyItemChanged(position)
    }

    private fun hideButtons(position: Int) {
        deletePositions.remove(position)
        notifyItemChanged(position)
    }
}