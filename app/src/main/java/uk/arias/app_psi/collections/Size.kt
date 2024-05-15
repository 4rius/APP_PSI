package uk.arias.app_psi.collections

import android.os.Parcel
import com.google.gson.internal.LinkedTreeMap

object Size {
    @JvmStatic
    fun getListSizeInBytes(list: List<Any>): Int {
        val parcel = Parcel.obtain()
        parcel.writeList(list)
        val size = parcel.dataSize()
        parcel.recycle()
        return size
    }

    @JvmStatic
    fun getMapSizeInBytes(map: LinkedTreeMap<String, String>): Int {
        val parcel = Parcel.obtain()
        parcel.writeMap(map)
        val size = parcel.dataSize()
        parcel.recycle()
        return size
    }

    @JvmStatic
    fun getSizeInBytes(o: Any): Int {
        val parcel = Parcel.obtain()
        parcel.writeValue(o)
        val size = parcel.dataSize()
        parcel.recycle()
        return size
    }
}
