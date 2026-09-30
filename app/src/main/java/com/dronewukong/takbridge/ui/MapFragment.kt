package com.dronewukong.takbridge.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.dronewukong.takbridge.R

/**
 * MapFragment — thin wrapper around the existing map UI layout.
 * MainActivity owns all the map logic; this fragment just inflates the view.
 * Binding happens in onViewCreated: adding the fragment during Activity.onCreate
 * does not guarantee that its view has been created yet.
 */
class MapFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_map, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as MainActivity).onMapViewCreated(view)
    }
}
