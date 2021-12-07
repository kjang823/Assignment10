package edu.temple.audiobb

import android.app.DownloadManager
import android.content.*
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.util.SparseArray
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import edu.temple.audlibplayer.PlayerService
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL


class MainActivity : AppCompatActivity(), BookListFragment.BookSelectedInterface , ControlFragment.MediaControlInterface{

    private lateinit var bookListFragment : BookListFragment
    private lateinit var serviceIntent : Intent
    private lateinit var mediaControlBinder : PlayerService.MediaControlBinder
    private var connected = false
    private lateinit var downloadArray : SparseArray<Int>
    private lateinit var preferences:SharedPreferences
    private lateinit var file:File
    private val internalPrefFileName = "my_shared_preferences"
    lateinit var bookProgress:PlayerService.BookProgress
    private var jsonArray: JSONArray = JSONArray()
    var currentProgress = 0
    var bookProg = 0
    var queueID:Long = 0



    val audiobookHandler = Handler(Looper.getMainLooper()) { msg ->

        msg.obj?.let { msgObj ->
            bookProgress = msgObj as PlayerService.BookProgress
            bookProg = bookProgress.progress

            if (playingBookViewModel.getPlayingBook().value == null) {
                Volley.newRequestQueue(this)
                    .add(JsonObjectRequest(Request.Method.GET, API.getBookDataUrl(bookProgress.bookId), null, { jsonObject ->
                        playingBookViewModel.setPlayingBook(Book(jsonObject))

                        if (selectedBookViewModel.getSelectedBook().value == null) {
                            selectedBookViewModel.setSelectedBook(playingBookViewModel.getPlayingBook().value)
                            bookSelected()
                        }
                    }, {}))
            }

            supportFragmentManager.findFragmentById(R.id.controlFragmentContainerView)?.run{
                with (this as ControlFragment) {
                    playingBookViewModel.getPlayingBook().value?.also {
                        setPlayProgress(((bookProgress.progress / it.duration.toFloat()) * 100).toInt())
                    }
                }
            }
        }
        true
    }

    private val searchRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        supportFragmentManager.popBackStack()
        it.data?.run {
            bookListViewModel.copyBooks(getSerializableExtra(BookList.BOOKLIST_KEY) as BookList)
            bookListFragment.bookListUpdated()
            jsonArray = SearchActivity.getJSONArray()
            with(preferences.edit()) {
                putString("bookList", jsonArray.toString())
                    .apply()
            }

        }

    }

    private val serviceConnection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            //cast the PlayerService.MediaControlBinder to its reference
            mediaControlBinder = service as PlayerService.MediaControlBinder
            //set the handler
            mediaControlBinder.setProgressHandler(audiobookHandler)


            connected = true

            if(preferences != null){

                val bookID = preferences.getInt("bookID", 0)
                currentProgress = preferences.getInt("bookProgress", 0)

                val loadedJson = preferences.getString("bookList", "")

                if(loadedJson != ""){

                    var arry = JSONArray(loadedJson)

                    bookListViewModel.populateBooks(arry)
                    bookListFragment.bookListUpdated()

                }
                if(bookID != 0){
                    var book = bookListViewModel.getBookById(bookID)
                    selectedBookViewModel.setSelectedBook(book)

                    bookSelected()

                    playingBookViewModel.setPlayingBook(selectedBookViewModel.getSelectedBook().value)

                    supportFragmentManager.findFragmentById(R.id.controlFragmentContainerView)?.run{
                        with (this as ControlFragment) {
                            playingBookViewModel.getPlayingBook().value?.also {
                                setPlayProgress(((currentProgress / it.duration.toFloat()) * 100).toInt())
                            }
                        }
                    }
                    play()
                }

            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            connected = false
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        downloadArray = SparseArray()

        preferences = getPreferences(MODE_PRIVATE)
        file = File(filesDir, internalPrefFileName)


        var reciever = object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                var id = p1?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)

                if(id == queueID){
                    Toast.makeText(applicationContext, "Book Download is Complete", Toast.LENGTH_LONG).show()
                }
            }
        }
        registerReceiver(reciever, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))



        playingBookViewModel.getPlayingBook().observe(this, {
            if(it != null) {
                (supportFragmentManager.findFragmentById(R.id.controlFragmentContainerView)
                        as ControlFragment).setNowPlaying(it.title)
            }})


        serviceIntent = Intent(this, PlayerService::class.java)


        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE)


        if (supportFragmentManager.findFragmentById(R.id.container1) is BookDetailsFragment
            && selectedBookViewModel.getSelectedBook().value != null) {
            supportFragmentManager.popBackStack()
        }


        if (savedInstanceState == null) {
            bookListFragment = BookListFragment()
            supportFragmentManager.beginTransaction()
                .add(R.id.container1, bookListFragment, BOOKLISTFRAGMENT_KEY)
                .commit()
        } else {
            bookListFragment = supportFragmentManager.findFragmentByTag(BOOKLISTFRAGMENT_KEY) as BookListFragment

            if (isSingleContainer && selectedBookViewModel.getSelectedBook().value != null) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.container1, BookDetailsFragment())
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit()
            }
        }


        if (!isSingleContainer && supportFragmentManager.findFragmentById(R.id.container2) !is BookDetailsFragment)
            supportFragmentManager.beginTransaction()
                .add(R.id.container2, BookDetailsFragment())
                .commit()

        findViewById<ImageButton>(R.id.searchButton).setOnClickListener {
            searchRequest.launch(Intent(this, SearchActivity::class.java))
        }



    }

    override fun play() {

        var download = DownloadRunnable()
        var thd =  Thread(download)
        thd.start()
        thd.join()

        thd =  Thread(download)
        thd.start()


        if (connected && selectedBookViewModel.getSelectedBook().value != null) {
            Log.d("Button pressed", "Play button")
            Log.d("HowPlayed", "Streaming Book")
            mediaControlBinder.play(selectedBookViewModel.getSelectedBook().value!!.id)
            playingBookViewModel.setPlayingBook(selectedBookViewModel.getSelectedBook().value)
            startService(serviceIntent)
        }
        if(this::mediaControlBinder.isInitialized){
            if(mediaControlBinder.isPlaying){
                downloadArray.append(selectedBookViewModel.getSelectedBook().value!!.id, bookProg)
            }
        }
        Thread.sleep(1000)
        checkCurrentProgress(currentProgress)

    }

    override fun bookSelected() {
        if (isSingleContainer && selectedBookViewModel.getSelectedBook().value != null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container1, BookDetailsFragment())
                .setReorderingAllowed(true)
                .addToBackStack(null)
                .commit()
        }
    }



    override fun onBackPressed() {
        super.onBackPressed()
        selectedBookViewModel.setSelectedBook(null)
    }

    private val isSingleContainer : Boolean by lazy{
        findViewById<View>(R.id.container2) == null
    }

    private val selectedBookViewModel : SelectedBookViewModel by lazy {
        ViewModelProvider(this).get(SelectedBookViewModel::class.java)
    }

    private val playingBookViewModel : PlayingBookViewModel by lazy {
        ViewModelProvider(this).get(PlayingBookViewModel::class.java)
    }

    private val bookListViewModel : BookList by lazy {
        ViewModelProvider(this).get(BookList::class.java)
    }

    companion object {
        const val BOOKLISTFRAGMENT_KEY = "BookListFragment"
    }




    fun checkCurrentProgress(_currentProgress:Int){
        mediaControlBinder.seekTo(_currentProgress)

    }

    override fun pause() {
        if (connected)
            currentProgress = bookProgress.progress

        with(preferences.edit()) {
            putInt("bookProgress", bookProgress.progress)
                .apply()

        }
        mediaControlBinder.pause()
    }

    override fun seek(position: Int) {
        if (connected && mediaControlBinder.isPlaying) mediaControlBinder.seekTo((playingBookViewModel.getPlayingBook().value!!.duration * (position.toFloat() / 100)).toInt())

    }

    override fun stop() {
        if (connected) {

            with(preferences.edit()) {
                putInt("bookID", 0)
                putInt("bookProgress", 0)
                    .apply()
            }

            ControlFragment.setNowPlaying("")
            currentProgress = 0

            supportFragmentManager.findFragmentById(R.id.controlFragmentContainerView)?.run{
                with (this as ControlFragment) {
                    playingBookViewModel.getPlayingBook().value?.also {
                        setPlayProgress(((0).toInt()))
                    }
                }
            }


            mediaControlBinder.stop()
            stopService(serviceIntent)
        }
    }



    override fun onDestroy() {

        Log.d("sharedPref", "App Finishing?: " + isFinishing )
        Log.d("sharedPref", "Media playing?: " + mediaControlBinder.isPlaying)

        //check if orientation has changed or the app is being killed
        //if the app is being killed then check if a book is playing
        if(isFinishing && mediaControlBinder.isPlaying) {
            //if a book is playing, stop the audio
            mediaControlBinder.stop()
            //take the book id and current progress and save it to be used when the app is
            //restarted
            with(preferences.edit()) {

                putInt("bookID", bookProgress.bookId)
                putInt("bookProgress", bookProgress.progress)
                commit()
                // Log.d("sharedPref", "Closing book id is: " + bookProgress.bookId.toString())
                //Log.d("sharedPref", "Closing book progress is: " + bookProgress.progress.toString())

            }

        }
        //call super function
        super.onDestroy()
        //unbind the service
        unbindService(serviceConnection)

    }

    inner class DownloadRunnable: Runnable{
        override fun run() {

            var uri = ("https://kamorris.com/lab/audlib/download.php?id=${selectedBookViewModel.getSelectedBook().value!!.id}")

            var totalSize = 0
            var downloadedSize = 0

            val url = URL(uri)
            val urlConnection = url
                .openConnection() as HttpURLConnection

            urlConnection.requestMethod = "POST"
            urlConnection.doOutput = true

            // connect
            urlConnection.connect()
            //Toast.makeText(this, "Download Starting", Toast.LENGTH_LONG).show()

            val myDir: File
            myDir = File(filesDir, "${selectedBookViewModel.getSelectedBook().value!!.id}")
            myDir.mkdirs()

            // create a new file, to save the downloaded file
            val mFileName: String = "${selectedBookViewModel.getSelectedBook().value!!.id}"
            val file = File(myDir, mFileName)

            val fileOutput = FileOutputStream(file)

            // Stream used for reading the data from the internet
            val inputStream = urlConnection.inputStream

            // this is the total size of the file which we are downloading
            totalSize = urlConnection.contentLength

            // create a buffer...
            val buffer = ByteArray(1024)
            var bufferLength = 0

            while (inputStream.read(buffer).also { bufferLength = it } > 0) {
                fileOutput.write(buffer, 0, bufferLength)
                downloadedSize += bufferLength
                // update the progressbar //

            }
            // close the output stream when complete //
            fileOutput.close()
            //Toast.makeText(this, "Download Complete", Toast.LENGTH_LONG).show()
        }


    }



}