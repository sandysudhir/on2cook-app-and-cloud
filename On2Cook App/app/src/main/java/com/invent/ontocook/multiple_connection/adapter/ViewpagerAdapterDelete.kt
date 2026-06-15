package com.invent.ontocook.multiple_connection.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.invent.ontocook.multiple_connection.model.FragmentData
import com.invent.ontocook.utils.Constants

class ViewpagerAdapterDelete(fragmentManager: FragmentManager?, lifecycle: Lifecycle?) :
    FragmentStateAdapter(fragmentManager!!, lifecycle!!) {

    private val fragmentList: ArrayList<FragmentData> = ArrayList()
    private val hashmapFragmentList: HashMap<String, Fragment> = HashMap()
    override fun getItemCount(): Int {
        return fragmentList.size
    }

    override fun createFragment(position: Int): Fragment {
        return fragmentList[position].fragment
    }

    fun addFragment(fragment: Fragment, macAddress: String) {
//        val fragmentData = FragmentData(fragment,fragmentList.size)
//        fragmentList.add(fragmentData)
//        hashmapFragmentList[macAddress] = fragment
    }

    fun getFragment(pos: Int): Fragment {
        return fragmentList[pos].fragment
    }

    fun getFragmentFromMac(mac: String): Fragment? {
        return hashmapFragmentList[mac]
    }

    override fun getItemId(position: Int): Long {
        return fragmentList[position].index.toLong()
    }

    fun remove(position: Int, macAddress: String) {
        fragmentList.removeAt(position)
        hashmapFragmentList.remove(macAddress)!!
        notifyItemRemoved(position)
    }

    fun change(it: String) {
        val obj: Fragment = hashmapFragmentList.remove(Constants.DummyMacAddress)!!
        hashmapFragmentList[it] = obj
    }
}