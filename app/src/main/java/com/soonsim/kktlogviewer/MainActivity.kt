package com.soonsim.kktlogviewer

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.soonsim.kktlogviewer.DateConversion.Companion.getISOString
import com.stfalcon.chatkit.commons.models.IMessage
import com.stfalcon.chatkit.messages.MessageInput
import com.stfalcon.chatkit.messages.MessagesListAdapter
import com.stfalcon.chatkit.messages.MessagesListAdapter.HoldersConfig
import com.stfalcon.chatkit.utils.DateFormatter
import io.realm.*
import kotlinx.android.synthetic.main.activity_main.*
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.time.DateUtils
import java.lang.Integer.max
import java.lang.Integer.min
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*


class MainActivity : AppCompatActivity(), MessagesListAdapter.SelectionListener,
    MessagesListAdapter.OnLoadMoreListener, MessageInput.InputListener,
    MessageInput.TypingListener, MessageInput.AttachmentsListener {
    private val imageLoader=KKTImageLoader(this)
    lateinit var config:KKTConfig
    private lateinit var mDatePickerDialog: DatePickerDialog
    private var mMessageData=ArrayList<KKTMessage>()
    lateinit var adapter:MessagesListAdapter<KKTMessage>
    private var selectionCount = 0
    private lateinit var realmconfig: RealmConfiguration
    private var mMenu:Menu?=null
    private var searchVisible:Boolean=false
    val onProgressListener=MyOnProgressListener()

    class KKTDateFormatter : DateFormatter.Formatter {
        override fun format(date: Date?): String {
            return getISOString(date!!)
        }
    }

    inner class MyOnProgressListener : KKTChatTextReader.OnProgressListener {
        override fun onProgressChanged(position: Long, totalCount: Long) {
//                Log.d("mike", "$position, $totalCount")
            runOnUiThread {
                progressBar.progress = position.toInt()
                progressBar.max = totalCount.toInt()
                progressText.text = "$position/$totalCount"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        initData()

        val holdersConfig = HoldersConfig()
        holdersConfig.setDateHeaderLayout(R.layout.item_custom_date_header)

        adapter=MessagesListAdapter<KKTMessage>(config.authorId, holdersConfig, imageLoader)
        adapter.setDateHeadersFormatter(KKTDateFormatter())

        Realm.init(this)
        realmconfig = RealmConfiguration.Builder()
            .name("kktviewer.realm")
//            .encryptionKey(getMyKey())
            .schemaVersion(1)
            .migration(KKTRealmMigration())
            .build()
        if (config.useDatabase) {
            val realm=Realm.getInstance(realmconfig)
            mMessageData.addAll(realm.where(KKTMessage::class.java).sort("messageTime").findAll())
            adapter.clear()
            adapter.addToEnd(mMessageData, true)
        }

//        progressBarHolder.visibility=View.GONE

        messagesListView.setAdapter(adapter)
        messagesListView.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                when (newState) {
                    RecyclerView.SCROLL_STATE_IDLE -> {
//                        currentDate
                        fab_down.show()
                        fab_up.show()
                    }
                    else -> {
                        fab_down.hide()
                        fab_up.hide()
                    }
                }
            }
        })


        fab_up.setOnLongClickListener(object: View.OnLongClickListener {
            override fun onLongClick(v: View?): Boolean {
                val date=getDateOfItem(adapter.items.size-1)
                if (date != null) {
                    viewMessageFromDate(date, true)
                    return true
                }
                return false
            }
        })

        fab_down.setOnLongClickListener(object: View.OnLongClickListener {
            override fun onLongClick(v: View?): Boolean {
                val date=getDateOfItem(0)
                if (date != null) {
                    viewMessageFromDate(date, false)
                    return true
                }
                return false
            }
        })

        fab_up.setOnClickListener { view ->
            val pos=(messagesListView.layoutManager as LinearLayoutManager).findLastCompletelyVisibleItemPosition()
            if (pos != RecyclerView.NO_POSITION) {
                val date=getDateOfItem(pos)
                if (date != null)
                    viewMessageFromDate(date, true)
            }
        }
        fab_down.setOnClickListener { view ->
            val pos=(messagesListView.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
            if (pos != RecyclerView.NO_POSITION) {
                val date=getDateOfItem(pos)
                if (date != null)
                    viewMessageFromDate(date, false)
            }
        }

        query.editText?.doOnTextChanged { text, start, count, after ->
            refreshList(text.toString())
        }

        query.editText?.setOnEditorActionListener { v, actionId, event ->
            if (actionId==EditorInfo.IME_ACTION_DONE) {
                toggleFilterView(false)
                true
            }
            false
        }

//        query.addTextChangedListener(object : TextWatcher {
//            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
//
//            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
//                refreshList(charSequence.toString())
//            }
//
//            override fun afterTextChanged(editable: Editable) {}
//        })

//        clearQuery.setOnClickListener {
//            query.text = null
//            refreshList("")
//        }
    }

    fun refreshList(query: String) {
        val realm=Realm.getInstance(realmconfig)
        if (!config.useDatabase || realm.isClosed || realm.isEmpty)
            return

        val results: RealmResults<KKTMessage> = when (StringUtils.isEmpty(query)) {
            true -> {
                realm.where(KKTMessage::class.java)
                    .findAll().sort(arrayOf("messageTime"), arrayOf(Sort.ASCENDING))
            }
            false -> {
                realm.where(KKTMessage::class.java)
                    .contains("messageText", query, Case.INSENSITIVE)
                    .findAll().sort(arrayOf("messageTime"), arrayOf(Sort.ASCENDING))
            }
        }

        mMessageData.clear()
        mMessageData.addAll(results.subList(0, results.size))
//        for (item in l) {
//            Log.d("mike", "item: ${item.toString()}")
//        }

        adapter.clear()
        adapter.addToEnd(mMessageData, true)
        adapter.notifyDataSetChanged()
        adapter.currentQuery = query
    }


    private fun toggleFilterView(isVisible: Boolean) {

//        mFirstTouch = 0F
        val height = if (isVisible) 0F else query.height.toFloat().unaryMinus()
//        if (!isVisible) {
//            this.currentFocus?.let { focusView ->
//                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
//                imm.hideSoftInputFromWindow(focusView.windowToken, 0)
//            }
//        }
//
        if (!isVisible) {
            query.visibility = View.GONE
        }
        else {
            query.visibility = View.VISIBLE
            query.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(query, SHOW_IMPLICIT)

//            /*
//            val llm=(messagesListView.layoutManager as LinearLayoutManager)
//            messagesListView.top=query.height
//            messagesListView.requestLayout()
//             */
        }

//        contents.addView(myCustomView)

        /*
        ObjectAnimator.ofFloat(searchCard, "translationY", height).apply {
            duration = 700
            start()
        }
        */
//        searchCard.visibility=if (isVisible) View.VISIBLE else View.GONE
//        contents.invalidate()
    }


    private fun getDateOfItem(pos:Int) : Date? {
        var date:Date?=null

        if (pos < 0 || pos > adapter.items.size-1)
            return null

        with (adapter.items[pos]) {
            if (item is Date) {
                if (pos == adapter.items.size-1)
                    date=((item) as Date)
                else
                    return getDateOfItem(pos + 1)
            }
            else if (item is IMessage)
                date=((item) as KKTMessage).createdAt!!
        }

        return date!!
    }

    private fun initData() {

        config=KKTConfig(this)

        // authorId
        if (config.authorId.isEmpty())
            config.authorId="최현일"

        val viewDate=if (config.lastViewedDate > 0L)
            DateConversion.millsToLocalDateTime(config.lastViewedDate)
        else
            LocalDateTime.now()
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


    private fun getMessagePositionByDate(date:Date) : Int {
        val idx=mMessageData.asReversed().binarySearch {
            val dt0=it.createdAt!!
            DateUtils.truncatedCompareTo(dt0, date, Calendar.DAY_OF_MONTH)
        }

        return idx
    }

    private fun viewMessageFromDate(date:Date, toBeginning:Boolean) {
        val md=mMessageData.asReversed()
        val idx=getMessagePositionByDate(date)
        if (idx < 0)
            Toast.makeText(this, "Cannot find message", Toast.LENGTH_SHORT).show()
        else {
//            Log.d("mike", "Found position: ${md[idx]}")
            val startIdx=getStartIndexOfDay(idx, toBeginning)
            val datePos=adapter.getDateHeaderPosition(md[startIdx].createdAt)

            val posDate=min(adapter.itemCount-1, max(0, adapter.itemCount - datePos -1))
            val absDatePos=min(adapter.itemCount-1, max(0, adapter.itemCount - posDate - 1))
            val llm=(messagesListView.layoutManager as LinearLayoutManager)

            val vdate=messagesListView.findViewHolderForAdapterPosition(datePos)
//            val dateheadersize=if (adapter.dateHeaderHeight==0) spToPx(resources.getDimension(R.dimen.message_date_header_text_size), this) else adapter.dateHeaderHeight
            val dateheadersize=adapter.dateHeaderHeight
            val offset=messagesListView.bottom - dateheadersize
            llm.scrollToPositionWithOffset(absDatePos, offset)
        }
    }

    private fun viewMessageFromDate(year: Int, month: Int, dayOfMonth: Int, toBeginning:Boolean) {
        val lt=LocalDateTime.of(year, month, dayOfMonth, 0, 0, 0)
        val date=Date.from(lt.atZone(ZoneId.systemDefault()).toInstant())
        viewMessageFromDate(date, toBeginning)
    }

    fun spToPx(sp: Float, context: Context): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            context.getResources().getDisplayMetrics()
        ).toInt()
    }

    private fun getStartIndexOfDay(curIdx: Int, toBeginning:Boolean): Int {
        // 날짜가 바뀌지 않을 때 까지 반복한다
        val ml=mMessageData.asReversed()
        val dt1=ml[curIdx].createdAt!!
        var startIdx=0

        // find next day
        if (!toBeginning) {
            startIdx=mMessageData.size-1
            for (j in curIdx+1 until mMessageData.size){
                val dt0=ml[j].createdAt!!
                if (!DateUtils.isSameDay(dt0, dt1)) {
                    startIdx=j
                    break
                }
            }
        }  else { // find prev day
            for (j in curIdx - 1 downTo 0 step 1) {
                val dt0 = ml[j].createdAt!!
                if (!DateUtils.isSameDay(dt0, dt1)) {
                    startIdx = j + 1
                    break
                }
            }
        }

        return startIdx
    }



    fun toggleMenuItemEnabled(rid:Int, isEnabled:Boolean) {
        if (mMenu == null)
            return

        val item=mMenu?.findItem(rid)
        item?.isEnabled=isEnabled
        item?.icon?.alpha=if (isEnabled) 0xFF else 0x7F
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        mMenu=menu

        // default state
        toggleMenuItemEnabled(R.id.saveLog, !config.useDatabase && mMessageData.isNotEmpty())
        toggleMenuItemEnabled(R.id.moveToDay, mMessageData.isNotEmpty())
        toggleMenuItemEnabled(R.id.findMessage, mMessageData.isNotEmpty())

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
            R.id.findMessage -> {
                searchVisible = !searchVisible
                toggleFilterView(searchVisible)
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    private fun openDb(): Boolean {
        val realm = Realm.getInstance(realmconfig)
        val result=realm.where(KKTMessage::class.java).findAll().sort("messageId", Sort.ASCENDING)
        mMessageData.clear()
        mMessageData.addAll(result.subList(0, result.size))
        adapter.notifyDataSetChanged()
        realm.close()

        return true
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
        val selecteddate:Long

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

        selecteddate=if (config.lastViewedDate == 0L)
            datelist.min()!!
        else
            config.lastViewedDate

        val builder: MaterialDatePicker.Builder<*> =
            MaterialDatePicker.Builder.datePicker().setSelection(selecteddate)
        val constraintsBuilder: CalendarConstraints.Builder =
            CalendarConstraints.Builder().setValidator(DateListValidator(datelist))

        val startdate=datelist.min()!!
        val enddate=datelist.max()!!
        constraintsBuilder.setStart(startdate)
        constraintsBuilder.setEnd(enddate)
        constraintsBuilder.setOpenAt(selecteddate)

        builder.setCalendarConstraints(constraintsBuilder.build())
        val picker = builder.build()
        picker.addOnPositiveButtonClickListener {
            Toast.makeText(this, picker.headerText, Toast.LENGTH_LONG).show()
            val selection=picker.selection as Long
            config.lastViewedDate=selection
            val ldt=DateConversion.millsToLocalDateTime(selection)
            viewMessageFromDate(ldt!!.year, ldt.monthValue, ldt.dayOfMonth, true)
        }
        picker.show(supportFragmentManager, picker.toString())
    }


    private fun saveLog() {
        /*
        val config = RealmConfiguration.Builder()
            .name("kktviewer.realm")
//            .encryptionKey(getMyKey())
            .schemaVersion(0)
            .modules(Realm.getDefaultModule())
            .build()

        val schema = realm.schema

        schema.create("KKTAuthor")
            .addField("authorId", String::class.java)
            .addField("authorAlias", String::class.java)
            .addField("avatarUri", String::class.java)

        schema.create("KKTMessage")
            .addField("messageId", String::class.java)
            .addField("messageText", String::class.java)
            .addField("author", KKTAuthor::class.java)
            .addField("messageTime", String::class.java)
            .addField("imgUrl", String::class.java)
        */

        val realm = Realm.getInstance(realmconfig)

        if (config.useDatabase) {
            realm.executeTransaction {
                it.deleteAll()
            }
            config.useDatabase=false
        }

        realm.executeTransaction {
            for (message in mMessageData) {
                // date message inserted by ChatKit
                if (message.isNull())
                    continue
                val m=message
                it.copyToRealmOrUpdate(m)
            }
        }

        // test
//        var res: RealmResults<KKTMessage>
//        res=realm.where(KKTMessage::class.java).findAll()
//        var resarr=ArrayList<KKTMessage>()
//        resarr.addAll(res.subList(0, res.size))
//        for (item in resarr) {
//            Log.d("mike", "${item.author}")
//        }

        config.useDatabase=true

        toggleMenuItemEnabled(R.id.saveLog, false)

        Toast.makeText(this, "Successfully saved", Toast.LENGTH_SHORT)
    }


    private fun openLog(uri:Uri) {
        val runnable= Runnable {
            val reader=KKTChatTextReader(this, config.authorId)
            reader.setOnProgressChanged(onProgressListener)

            runOnUiThread{
                progressBarHolder.visibility=View.VISIBLE
            }

            mMessageData=reader.readFile(uri)

            runOnUiThread {
                config.lastViewedDate = 0L
                adapter.clear()
                adapter.addToEnd(mMessageData, true)
                adapter.notifyDataSetChanged()

                for (item in listOf(R.id.saveLog, R.id.moveToDay, R.id.findMessage)) {
                    toggleMenuItemEnabled(item, mMessageData.isNotEmpty())
                }

                progressBarHolder.visibility=View.GONE
            }
        }
        val thread=Thread(runnable)
        thread.start()
    }


    private fun initSampleData() {
//        var msgList:List<KKTMessage> = KKTChatLogReader(this).readTextLog(null, config.authorId)
        var msgList:List<KKTMessage> = KKTChatTextReader(this, config.authorId).readFile(null)

        KKTChatTextReader
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
//            .setData(Uri.parse(config.lastOpenedFile))

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
            REQUEST_CODE_OPEN_DB -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    var uri: Uri? = data.data ?: return
                    openLog(uri!!)
                }//The uri with the location of the file
            }
        }
    }


}
