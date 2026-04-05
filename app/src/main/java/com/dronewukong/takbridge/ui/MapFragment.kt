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
 * The activity accesses map views directly via findViewById after the fragment
 * is added to the container.
 */
class MapFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_map, container, false)
}
