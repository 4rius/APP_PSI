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

class DeviceListAdapter(private val context: Context, private val devices: List<String>, private val parentView: View): RecyclerView.Adapter<DeviceListAdapter.ViewHolder>() {

    private val deletePositions: MutableList<Int> = mutableListOf()

    // Interfaz del adaptador
    class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
        val deviceName: TextView = view.findViewById(R.id.textViewTitle)
        val deviceDetails: TextView = view.findViewById(R.id.textViewContent)
        val buttonHide: Button = view.findViewById(R.id.buttonHide)
        val buttonActions: Button = view.findViewById(R.id.buttonActions)
        val buttonDetails: Button = view.findViewById(R.id.buttonDetails)
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
            holder.buttonDetails.visibility = View.VISIBLE
        } else {
            holder.buttonHide.visibility = View.GONE
            holder.buttonActions.visibility = View.GONE
            holder.buttonDetails.visibility = View.GONE
        }

        holder.deviceName.text = devices[position]
        holder.deviceDetails.text = "Last seen: " + NetworkService.getLastSeen(devices[position])

        holder.buttonHide.setOnClickListener {
            deletePositions.remove(position)
            notifyItemChanged(position)
        }

        holder.buttonActions.setOnClickListener {
            showBottomSheet(position)
        }

        holder.buttonDetails.setOnClickListener {
            // TODO or remove showDetails(position)
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
        bottomSheetDialog.findViewById<TextView>(R.id.textViewTitleOptions)?.text = "Action for device " + devices[position]
        bottomSheetDialog.findViewById<Button>(R.id.buttonSendLargeMsg)?.setOnClickListener {
            NetworkService.sendLargeMessage(devices[position])
        }
        bottomSheetDialog.findViewById<Button>(R.id.buttonSendSmallMsg)?.setOnClickListener {
            NetworkService.sendSmallMessage(devices[position])
        }
    }
}