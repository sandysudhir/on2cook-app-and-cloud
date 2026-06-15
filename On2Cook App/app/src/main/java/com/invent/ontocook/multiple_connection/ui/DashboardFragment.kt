package com.invent.ontocook.multiple_connection.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.invent.ontocook.R
import com.invent.ontocook.databinding.FragmentDashboardBinding
import com.invent.ontocook.extension.showSnackBarShort
import com.invent.ontocook.multiple_connection.HomeActivity
import com.invent.ontocook.multiple_connection.HomeTvActivity
import com.invent.ontocook.utils.Constants
import com.invent.ontocook.utils.DebugLog


/**
 * A simple [Fragment] subclass.
 * Use the [DashboardFragment.newInstance] factory method to
 * create an instance of this fragment.
 */

private const val ARG_PARAM2 = Constants.MAC_ADDRESS

class DashboardFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var macAddress: String? = null
    private lateinit var binding: FragmentDashboardBinding
    var navController: NavController? = null
    var doubleBackToExitPressedOnce = false
    internal val isNavInitialised: Boolean
        get() = navController != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        arguments?.let {
            macAddress = it.getString(ARG_PARAM2)
        }
        DebugLog.e("onCreate $macAddress")
        if (macAddress == Constants.DummyMacAddress)
            if (Constants.IS_TABLET)
                (activity as HomeTvActivity).changeDummyMac.observe(
                    requireActivity(), androidx.lifecycle.Observer {
                        DebugLog.e("changeDummyMac $it")
                        macAddress = it
                    }
                )
            else (activity as HomeActivity).changeDummyMac.observe(
                requireActivity(), androidx.lifecycle.Observer {
                    DebugLog.e("changeDummyMac $it")
                    macAddress = it
                }
            )
        DebugLog.e("onCreate MAc $macAddress")


    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding =
            DataBindingUtil.inflate(layoutInflater, R.layout.fragment_dashboard, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init()
    }

    private fun init() {
        DebugLog.e("onCreate init $macAddress")
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.host_fragment) as NavHostFragment
        navController = navHostFragment.navController
//        navController=findNavController()
        val bundle = Bundle()
        bundle.putString(Constants.MAC_ADDRESS, "$macAddress")
        navController?.setGraph(R.navigation.nav_graph, bundle)
        Log.e("macAddress", "init:macAddress $macAddress")
//        changeFragment(cookingFragment)

//        Handler(Looper.getMainLooper()).postDelayed({
//            val cookingFragment = HomeFragment.newInstance("1")
//            changeFragment(cookingFragment)
//        },5000)
    }

    fun getCurrentFragment(): Fragment? {
        return (childFragmentManager.findFragmentById(R.id.host_fragment) as NavHostFragment).childFragmentManager.fragments[0] //PrinceEWW, To prevent crash issue of null pointer exception
        /*val navHostFragment = childFragmentManager.findFragmentById(R.id.host_fragment) as? NavHostFragment
        if (navHostFragment?.isAdded == true) {
            return navHostFragment.childFragmentManager.fragments.firstOrNull()
        }
        return null*/
    }

    fun onBackPress() {

        if (navController == null) {
            DebugLog.e("onBackPress null")
            activity?.onBackPressedDispatcher?.onBackPressed()
            return
        }
//        DebugLog.e("onBackPress ${navController.currentDestination == null}")
//        DebugLog.e("onBackPress Or ${navController.currentDestination!!.id == R.id.homeFragment}")
        navController?.let { navigationController ->
            navigationController.currentDestination?.let {it->
                if (it.id == R.id.homeFragment) {
                    if (doubleBackToExitPressedOnce) {
                        activity?.onBackPressedDispatcher?.onBackPressed()
                        return
                    }

                    this.doubleBackToExitPressedOnce = true
                    showSnackBarShort(requireContext().resources.getText(R.string.backpress_msg))
                    Handler(Looper.getMainLooper()).postDelayed(Runnable {
                        doubleBackToExitPressedOnce = false
                    }, 2000)
                } else {
                    navigationController.popBackStack()
                }
            }

        }

    }

    override fun onDestroy() {
        super.onDestroy()
        DebugLog.e("onDestroy ")
        if (Constants.IS_TABLET)
            (activity as HomeTvActivity).changeDummyMac.removeObservers(this)
        else
            (activity as HomeActivity).changeDummyMac.removeObservers(this)
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param macAddress Parameter 2.
         * @return A new instance of fragment DashboradFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(macAddress: String) =
            DashboardFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM2, macAddress)
                }
            }
    }
}