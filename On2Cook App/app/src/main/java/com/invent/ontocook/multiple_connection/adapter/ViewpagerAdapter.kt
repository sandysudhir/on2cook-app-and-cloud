package com.invent.ontocook.multiple_connection.adapter

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.invent.ontocook.multiple_connection.model.FragmentData
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ViewpagerAdapter(private var fragmentManager: FragmentManager?, lifecycle: Lifecycle?) :
    FragmentStateAdapter(fragmentManager!!, lifecycle!!) {

    private var index = 0
    private var id = 0
    private val fragmentList: ArrayList<FragmentData> = ArrayList()
    private val hashmapFragmentList: HashMap<String, Fragment> = HashMap()
    override fun getItemCount(): Int {
        DebugLog.e("getItemCount ${fragmentList.size}")
        return fragmentList.size
    }

    override fun createFragment(position: Int): Fragment {
        DebugLog.e("CreateFragment $position")
        return fragmentList[position].fragment
    }

    fun addFragment(fragment: Fragment, macAddress: String) {
        Log.e("TAG", "onCreate:Add Before $index")
        Log.e("TAG", "onCreate:Add Before $index")
        val fragmentData = FragmentData(fragment, index, macAddress,id)
        fragmentList.add(fragmentData)
        index++
        id++
        Log.e("TAG", "onCreate:Add List  After$fragmentList")
        Log.e("TAG", "onCreate:Add After $index")
        hashmapFragmentList[macAddress] = fragment
    }

    fun getFragment(pos: Int): Fragment {
        return fragmentList[pos].fragment
    }

    fun getFragmentFromMac(mac: String): Fragment? {
        return hashmapFragmentList[mac]
    }

    override fun getItemId(position: Int): Long {
        return fragmentList[position].id.toLong()
    }

    fun remove(position: Int, macAddress: String) {
        Log.e("TAG", "onCreate:Remove Before $position")
        Log.e("TAG", "onCreate:Remove Before $fragmentList")
        CoroutineScope(Dispatchers.IO).launch {
            var foundIndex = -1
            run breaking@{
                fragmentList.forEachIndexed { index, fragmentData ->
                    if (fragmentData.mac == macAddress) {
                        foundIndex = index
                        return@breaking
                    }
                }
            }
            Log.e("TAG", "onCreate:foundIndex $foundIndex")
            if (foundIndex == -1) return@launch
            val fragment = fragmentList.removeAt(foundIndex)
            index--
            val trans: FragmentTransaction = fragmentManager!!.beginTransaction()
            trans.remove((fragment.fragment as Fragment?)!!)
            trans.commit()
            Log.e("TAG", "onCreate:Remove After ${fragmentList.size}")
            hashmapFragmentList.remove(macAddress)!!
            Log.e("TAG", "onCreate:Remove After $fragmentList")
        }
        notifyItemRemoved(position)

    }

    fun change(it: String) {
        val obj: Fragment = hashmapFragmentList.remove(Constants.DummyMacAddress)!!
        hashmapFragmentList[it] = obj
        if (fragmentList.any { it.mac == Constants.DummyMacAddress }) {
            fragmentList[0].mac = it
        }
    }
}