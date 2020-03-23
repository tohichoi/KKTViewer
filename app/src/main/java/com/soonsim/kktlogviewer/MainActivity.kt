package com.soonsim.kktlogviewer

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.stfalcon.chatkit.messages.MessageInput
import com.stfalcon.chatkit.messages.MessagesListAdapter
import com.stfalcon.chatkit.messages.MessagesListAdapter.HoldersConfig
import kotlinx.android.synthetic.main.activity_main.*
import org.apache.commons.lang3.time.DateUtils
import java.lang.Integer.max
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*


class MainActivity : AppCompatActivity(), MessagesListAdapter.SelectionListener,
    MessagesListAdapter.OnLoadMoreListener, MessageInput.InputListener,
    MessageInput.TypingListener, MessageInput.AttachmentsListener {
    val imageLoader=KKTImageLoader(this)
    lateinit var config:KKTConfig
    private lateinit var mDatePickerDialog: DatePickerDialog
//    private lateinit var mDateDialogBuilder : MaterialDatePicker.Builder<*> = MaterialDatePicker.Builder.datePicker()
//    private lateinit var mmDateDialog : MaterialDatePicker<*> = mDateDialogBuilder.build()
    lateinit var mMessageData:List<KKTMessage>
    lateinit var adapter:MessagesListAdapter<KKTMessage>
    private var selectionCount = 0
    private var scrollTriggered = false
    private var today: Long = 0
    private var nextMonth: Long = 0
    private var janThisYear: Long = 0
    private var decThisYear: Long = 0
    private var oneYearForward: Long = 0
    private var todayPair: Pair<Long, Long>? = null
    private var nextMonthPair: Pair<Long, Long>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
//        setSupportActionBar(toolbar)

        initData()

        val holdersConfig = HoldersConfig()
        holdersConfig.setDateHeaderLayout(R.layout.item_custom_date_header)

        adapter=MessagesListAdapter<KKTMessage>(config.authorId, holdersConfig, imageLoader)
        messagesListView.setAdapter(adapter)

        /*
        val input = findViewById<MessageInput>(R.id.input)
        input.setInputListener(this)
        input.setTypingListener(this)
        input.setAttachmentsListener(this)

         */
    }

    private fun initData() {

        config=KKTConfig(this)

        // authorId
        if (config.authorId.isEmpty())
            config.authorId="최현일"

//        if (config.lastViewedDate == 0L) {
//            config.lastViewedDate=LocalDateTime.now().toInstant().toEpochMilli()
//        }

        val viewDate=if (config.lastViewedDate > 0L)
            DateConversion.millsToLocalDateTime(config.lastViewedDate)
        else
            LocalDateTime.now()

        /*
        mDatePickerDialog = DatePickerDialog(this, { _, year, month, dayOfMonth ->
            // goto data
            viewMessageFromDate(year, month+1, dayOfMonth)
        },
            viewDate.year-1, viewDate.monthValue+1, viewDate.dayOfYear)
         */
    }

    override fun onStartTyping() {
        Log.v("Typing listener", "...")
    }

    override fun onStopTyping() {
        Log.v("Typing listener", "")
    }
    override fun onBackPressed() {
        if (selectionCount == 0) {
            super.onBackPressed()
        } else {
            adapter.unselectAllItems()
        }
    }

    override fun onLoadMore(page: Int, totalItemsCount: Int) {
        Log.i("TAG", "onLoadMore: $page $totalItemsCount")
        /*
        if (totalItemsCount < com.stfalcon.chatkit.sample.features.demo.DemoMessagesActivity.TOTAL_MESSAGES_COUNT) {
            loadMessages()
        }
         */
    }

    override fun onSelectionChanged(count: Int) {
        this.selectionCount = count
        /*
        menu.findItem(R.id.action_delete).setVisible(count > 0)
        menu.findItem(R.id.action_copy).setVisible(count > 0)

         */
    }

    protected fun loadMessages() {
        /*
        Handler().postDelayed({
            val messages: ArrayList<Message> =
                MessagesFixtures.getMessages(lastLoadedDate)
            lastLoadedDate = messages[messages.size - 1].getCreatedAt()
            messagesAdapter.addToEnd(messages, false)
        }, 1000)

         */
    }


    private fun viewMessageFromDate(year: Int, month: Int, dayOfMonth: Int) {
        val lt=LocalDateTime.of(year, month, dayOfMonth, 0, 0, 0)
        val dt1=Date.from(lt.atZone(ZoneId.systemDefault()).toInstant())
        val ml=mMessageData.asReversed()
        val idx=ml.binarySearch {
            val dt0=it.createdAt!!
            DateUtils.truncatedCompareTo(dt0, dt1, Calendar.DAY_OF_MONTH)
        }
        if (idx < 0)
            Toast.makeText(this, "Cannot find message", Toast.LENGTH_SHORT).show()
        else {
            Log.d("mike", "Found position: ${ml[idx]}")
            val startIdx=getStartIndexOfDay(idx)
            val datePos=adapter.getDateHeaderPosition(ml[startIdx].createdAt)

            val posDate=max(0, adapter.itemCount - datePos -1)
            val absDatePos=max(0, adapter.itemCount - posDate - 1)
            val llm=(messagesListView.layoutManager as LinearLayoutManager)
            val offset=messagesListView.bottom - spToPx(resources.getDimension(R.dimen.message_date_header_text_size), this)
            llm.scrollToPositionWithOffset(absDatePos, offset)
        }
    }

    fun spToPx(sp: Float, context: Context): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            context.getResources().getDisplayMetrics()
        ).toInt()
    }

    private fun getStartIndexOfDay(curIdx: Int): Int {
        // 날짜가 바뀌지 않을 때 까지 반복한다
        val ml=mMessageData.asReversed()
        val dt1=ml[curIdx].createdAt!!
        var startIdx=0
        var endIdx=curIdx

        // find prev day
//        for (j in curIdx+1 until mMessageData.size){
//            val dt0=ml[j].createdAt!!
//            if (!DateUtils.isSameDay(dt0, dt1)) {
//                startIdx=j
//                break
//            }
//        }

        // find next day
        for (j in curIdx-1 downTo 0 step 1) {
            val dt0=ml[j].createdAt!!
            if (!DateUtils.isSameDay(dt0, dt1)) {
                startIdx=j+1
                break
            }
        }

//        if (startIdx != curIdx)
        return startIdx
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
                getLogFilePathFromUser()
                true
            }
            R.id.saveLog -> {
                saveLog()
                true
            }
            R.id.moveToDay -> {
                getDateFromUser()
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    override fun onSubmit(input: CharSequence?): Boolean {
        return false
    }

    override fun onAddAttachments() {

    }

    private fun getClearedUtc(): Calendar {
        val utc: Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utc.clear()
        return utc
    }

    private fun getDateFromUser() {

        val datelist=ArrayList<Long>()
        val selday:Long

        for (item in adapter.dateSet) {
            var cal=getClearedUtc()
//            cal.time=DateUtils.truncate(item, Calendar.DAY_OF_MONTH)
            cal.time=item
            cal.set(Calendar.HOUR, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            datelist.add(cal.timeInMillis)
        }

        selday=if (config.lastViewedDate == 0L)
            datelist.min()!!
        else
            config.lastViewedDate

        val builder: MaterialDatePicker.Builder<*> =
            MaterialDatePicker.Builder.datePicker().setSelection(selday)
        val constraintsBuilder: CalendarConstraints.Builder =
            CalendarConstraints.Builder().setValidator(DateListValidator(datelist))

        val startdate=datelist.min()!!
        val enddate=datelist.max()!!
        constraintsBuilder.setStart(startdate)
//        enddate.set(2020, 2, 31)
        constraintsBuilder.setEnd(enddate)

        builder.setCalendarConstraints(constraintsBuilder.build())
        val picker = builder.build()
        picker.addOnPositiveButtonClickListener {
            Toast.makeText(this, picker.headerText, Toast.LENGTH_LONG).show()
            val selection=picker.selection as Long
            config.lastViewedDate=selection
            val ldt=DateConversion.millsToLocalDateTime(selection)
            viewMessageFromDate(ldt!!.year, ldt.monthValue, ldt.dayOfMonth)
        }
        picker.show(supportFragmentManager, picker.toString())
    }

    private fun saveLog() {
        Toast.makeText(this, "Not yet implemented", Toast.LENGTH_SHORT).show()
    }

    private fun openLog(uri:Uri) {
        mMessageData=KKTChatLogReader(this).readTextLog(uri, config.authorId)
        config.lastViewedDate=0L
        adapter.clear()
        adapter.addToEnd(mMessageData, true)
        adapter.notifyDataSetChanged()
    }

    private fun initSampleData() {
        var msgList:List<KKTMessage> = KKTChatLogReader(this).readTextLog(null, config.authorId)

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

    private fun getLogFilePathFromUser()
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
