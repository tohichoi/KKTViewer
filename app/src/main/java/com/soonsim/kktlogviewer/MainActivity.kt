package com.soonsim.kktlogviewer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.stfalcon.chatkit.messages.MessagesListAdapter
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {
    val imageLoader=KKTImageLoader(this)
    val myId="최현일"
    lateinit var messageList:List<KKTMessage>
    lateinit var adapter:MessagesListAdapter<KKTMessage>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
//        setSupportActionBar(toolbar)

        adapter=MessagesListAdapter<KKTMessage>(myId, imageLoader)

        messagesList.setAdapter(adapter)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.openLog -> {
//                openLog()
                getLogFilePath()
                true
            }
            R.id.saveLog -> {
                saveLog()
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    private fun saveLog() {
        Toast.makeText(this, "Not yet implemented", Toast.LENGTH_SHORT).show()
    }

    private fun openLog(uri:Uri) {
        messageList=ChatLogReader(this).readTextLog(uri, myId)
        adapter.addToEnd(messageList, true)
        adapter.notifyDataSetChanged()
    }

    private fun initSampleData() {
        var msgList:List<KKTMessage> = ChatLogReader(this).readTextLog(null, myId)

        //TODO: remove
        for (msg in msgList) {
            msg.author?.avatarUri=
                when (msg.author?.authorAlias) {
                    "John Doe" -> {
                        "android.resource://" + packageName + "/" + R.drawable.neofrodo02
                    }
                    "Jane Doe" -> {
                        "android.resource://" + packageName + "/" + R.drawable.neofrodo03
                    }
                    "Zeus" -> {
                        "android.resource://" + packageName + "/" + R.drawable.neofrodo04
                    }
                    else -> {
                        "android.resource://" + packageName + "/" + R.drawable.neofrodo05
                    }
                }
        }
    }

    private fun getLogFilePath()
    {
        var intent: Intent = Intent()
            .setType("text/*")
            .setAction(Intent.ACTION_GET_CONTENT)

        startActivityForResult(Intent.createChooser(intent, "Select a file"), REQUEST_CODE_IMPORT_TEXT)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_IMPORT_TEXT -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    var uri: Uri? = data.data ?: return
                    openLog(uri!!)
                }//The uri with the location of the file
            }
        }
    }
}
