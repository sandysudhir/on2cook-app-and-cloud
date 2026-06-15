package com.invent.ontocook.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.invent.ontocook.multiple_connection.model.PairedDeviceData

class SharedPreferencesManager {

    lateinit var sharedPreferences: SharedPreferences
    lateinit var contex: Context

    companion object {
        private fun getPref(context: Context): SharedPreferences {
            return context.getSharedPreferences("On2CookPref", Context.MODE_PRIVATE)
        }

        fun insertData(context: Context, key: String, value: String) {
            val editor = getPref(context).edit()
            editor.putString(key, value)
            editor.apply()
        }

        fun retriveData(context: Context, key: String): String? {
            return getPref(context).getString(key, "")
        }

        fun getMacAddressList(context: Context): ArrayList<PairedDeviceData> {
            val macAddressList = ArrayList<PairedDeviceData>()
            val stringPairedDeviceList = retriveData(
                context, Constants.MAC_ADDRESS_LIST
            )
            if (!stringPairedDeviceList.isNullOrEmpty()) {
//                val strMacList = retriveData(context, Constants.MAC_ADDRESS_LIST) //Commented by PrinceEWW, because we have already same data in "stringPairedDeviceList"
                val nameType = object : TypeToken<ArrayList<PairedDeviceData>>() {}.type
                macAddressList.addAll(Gson().fromJson(stringPairedDeviceList, nameType))
            }
            DebugLog.e("retrive MacAddressList${Gson().toJson(macAddressList)}")
            Log.e("PrinceEWW>>>", "SharedPreferenceManager - getMacAddressList: ${retriveData(
                context, Constants.MAC_ADDRESS_LIST
            )}")
            return macAddressList
        }

        fun updateMacAddressList(context: Context, list: ArrayList<PairedDeviceData>) {
            DebugLog.e("updateMacAddressList${Gson().toJson(list)}")
            insertData(
                context, Constants.MAC_ADDRESS_LIST, Gson().toJson(list)
            )
            Log.e("PrinceEWW>>>", "SharedPreferenceManager - updateMacAddressList - list: $list")
            Log.e("PrinceEWW>>>", "SharedPreferenceManager - updateMacAddressList: ${retriveData(
                context, Constants.MAC_ADDRESS_LIST
            )}")
        }

        fun insertDevice(context: Context, macAddress: String, name: String): PairedDeviceData? {
            val stringPairedDeviceList = retriveData(
                context, Constants.MAC_ADDRESS_LIST
            )
            Log.e("PrinceEWW>>>", "insertDevice - stringPairedDeviceList: $stringPairedDeviceList")
            if (!stringPairedDeviceList.isNullOrEmpty()) {
                val nameType = object : TypeToken<ArrayList<PairedDeviceData>>() {}.type
                val itemPairedDeviceDataList: ArrayList<PairedDeviceData> =
                    Gson().fromJson(stringPairedDeviceList, nameType)

                itemPairedDeviceDataList.sortBy { it.id } //During insert device we give id according, so we need to do sortBY, So in case we delete device with id=1, and device with id = 2 is still connected, so we can add new device on id 1-------//
                if (!itemPairedDeviceDataList.any { it.macAddress == macAddress }) {
                    try {
                        var oldValue = 0
                        run breaking@{
                            itemPairedDeviceDataList.forEachIndexed { index, pairedDeviceData ->
                                if (pairedDeviceData.id.toInt() - oldValue > 1) {
//                                    DebugLog.e("Answer $index value ${pairedDeviceData.id}")
                                    Log.e("PrinceEWW>>>", "insertDevice - oldValue: $oldValue at index $index")
                                    return@breaking
                                }
                                oldValue = pairedDeviceData.id
                            }
                        }
                        Log.e("PrinceEWW>>>", "insertDevice - itemPairedDeviceData: oldValue is $oldValue, So id is ${oldValue.plus(1)}")
                        val itemPairedDeviceData = PairedDeviceData(
                            macAddress,
                            oldValue + 1,
//                            "${oldValue + 1}:- ${macAddress.substring(12, macAddress.length)}",
                            name,
                            isEdit = false,
//                            isConnected = false
                            isConnected = true //By PrinceEWW, because on connection success we call this function(insert device), So we need to do is connected = true
                        )
                        itemPairedDeviceDataList.add(itemPairedDeviceData)
                        DebugLog.e("insertMacAddressList If ${Gson().toJson(itemPairedDeviceDataList)}")
                        insertData(
                            context,
                            Constants.MAC_ADDRESS_LIST,
                            Gson().toJson(itemPairedDeviceDataList)
                        )
                        Log.e("PrinceEWW>>>", "SharedPreferenceManager - insertDevice if: ${retriveData(
                            context, Constants.MAC_ADDRESS_LIST
                        )}")
                        return itemPairedDeviceData
                    } catch (e: Exception) {
                        e.printStackTrace()
                        DebugLog.e("Insert Device ${e.message}")
                    }
                }
            } else {
                val itemPairedDeviceDataList: ArrayList<PairedDeviceData> = ArrayList()
                Log.e("TAG", "insertDevice:Else ${itemPairedDeviceDataList.size}")

                val itemPairedDeviceData = PairedDeviceData(
                    macAddress,
                    1,
//                    "1:- ${macAddress.substring(12, macAddress.length)}",
                    name,
                    false,
                    true //By PrinceEWW, because on connection success we call this function(insert device), So we need to do is connected = true
                )
                itemPairedDeviceDataList.add(itemPairedDeviceData)
                insertData(
                    context, Constants.MAC_ADDRESS_LIST, Gson().toJson(itemPairedDeviceDataList)
                )
                Log.e("PrinceEWW>>>", "SharedPreferenceManager - insertDevice else: ${retriveData(
                    context, Constants.MAC_ADDRESS_LIST
                )}")
                return itemPairedDeviceData
            }
            return null
        }
    }
}