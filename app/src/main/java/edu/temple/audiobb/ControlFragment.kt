package edu.temple.audiobb

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView

var nowPlaying: TextView? = null
class ControlFragment : Fragment() {

    lateinit var play: ImageButton
    lateinit var pause: ImageButton
    lateinit var stop: ImageButton
    var seekBar: SeekBar? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val layout = inflater.inflate(R.layout.fragment_control, container, false)

        play = layout.findViewById(R.id._play)
        pause = layout.findViewById(R.id._pause)
        stop = layout.findViewById(R.id._stop)
        seekBar = layout.findViewById(R.id._seekBar)
        nowPlaying = layout.findViewById(R.id._nowPlaying)

        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    (activity as MediaControlInterface).seek(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {

            }
        })

        val onClickListener = View.OnClickListener {
            var parent = activity as MediaControlInterface
            when (it.id) {
                R.id._play -> parent.play()
                R.id._pause -> parent.pause()
                R.id._stop -> parent.stop()
            }
        }

        play.setOnClickListener(onClickListener)
        pause.setOnClickListener(onClickListener)
        stop.setOnClickListener(onClickListener)

        return layout
    }

    fun setNowPlaying(title: String) {
        nowPlaying?.text = title
    }

    fun setPlayProgress(progress: Int) {
        seekBar?.setProgress(progress, true)
    }

    interface MediaControlInterface {
        fun play()
        fun pause()
        fun stop()
        fun seek(position: Int)
    }

    companion object {

        fun setNowPlaying(title: String) {
            nowPlaying?.text = title
        }
    }
}