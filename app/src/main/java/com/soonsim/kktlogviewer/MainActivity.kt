package com.soonsim.kktlogviewer

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isInvisible
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.soonsim.kktlogviewer.DateConversion.Companion.getISOString
import com.stfalcon.chatkit.commons.models.IMessage
import com.stfalcon.chatkit.messages.MessagesListAdapter
import com.stfalcon.chatkit.messages.MessagesListAdapter.HoldersConfig
import com.stfalcon.chatkit.utils.DateFormatter
import io.realm.*
import kotlinx.android.synthetic.main.activity_main.*
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.time.DateFormatUtils
import org.apache.commons.lang3.time.DateUtils
import java.lang.Integer.max
import java.lang.Integer.min
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import kotlin.math.abs
import kotlin.properties.Delegates


class MainActivity : AppCompatActivity(),
    MessagesListAdapter.SelectionListener,
    MessagesListAdapter.OnLoadMoreListener,
    MessagesListAdapter.OnMessageLongClickListener<KKTMessage>  {
    private val imageLoader = KKTImageLoader(this)
    lateinit var config: KKTConfig
    private lateinit var mDatePickerDialog: DatePickerDialog
    private var mMessageData = ArrayList<KKTMessage>()
    lateinit var adapter: MessagesListAdapter<KKTMessage>
    private var selectionCount = 0
    private lateinit var realmconfig: RealmConfiguration
    private var mMenu: Menu? = null
    private var searchVisible: Boolean = false
    private var lastViewedPosition = 0L
    private var lastQueryText = ""
    private var lastViewedDate = 0L
    private var useDatabase = false
    private var appBarLayoutHeight = 0
    val onProgressListener = MyOnProgressListener()

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
//                progressText.text = "$position/$totalCount"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        config = KKTConfig(this)

        // authorId
        if (config.authorId.isEmpty())
            config.authorId = "최현일"

        if (savedInstanceState != null) {
            lastQueryText = savedInstanceState.getString("lastQueryText")!!
            lastViewedDate = savedInstanceState.getLong("lastViewedDate")
            lastViewedPosition = savedInstanceState.getLong("lastViewedPosition")
            useDatabase = savedInstanceState.getBoolean("useDatabase")
        } else {
            lastViewedPosition = config.lastViewedPosition
            lastQueryText = config.lastQueryText
            lastViewedDate = config.lastViewedDate
            useDatabase = config.useDatabase
        }

//        supportActionBar.setDefaultDisplayHomeAsUpEnabled(false)
//        appBar.icon
//        actionBar?.setIcon(R.drawable.logo_small)
//        toolbar.title=""
//
//        initData()

        val holdersConfig = HoldersConfig()
        holdersConfig.setDateHeaderLayout(R.layout.item_custom_date_header)

        adapter = MessagesListAdapter<KKTMessage>(config.authorId, holdersConfig, imageLoader)
        adapter.setDateHeadersFormatter(KKTDateFormatter())
//        adapter.setOnMessageLongClickListener(this)
        adapter.enableSelectionMode(this)

        progressBarHolder.visibility = View.GONE
        query.visibility = View.GONE

        messagesListView.setAdapter(adapter)
        messagesListView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                when (newState) {
                    RecyclerView.SCROLL_STATE_IDLE -> {
//                        currentDate
                        fab_down.show()
                        fab_up.show()
                        val pos =
                            (messagesListView.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
                        if (pos != RecyclerView.NO_POSITION) {
                            lastViewedPosition = pos.toLong()
                            config.lastViewedPosition = lastViewedPosition
                        }
                    }
                    else -> {
                        fab_down.hide()
                        fab_up.hide()
                    }
                }
            }
        })

        appBarLayout.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
            if (abs(verticalOffset) - appBarLayout.totalScrollRange == 0) {
                // Collapsed
                appBarLayoutHeight=0
            } else if (verticalOffset == 0) {
                // Expanded
                appBarLayoutHeight=appBarLayout.height
            } else {
                // Idle
            }
        }
        )

        fab_up.setOnLongClickListener(object : View.OnLongClickListener {
            override fun onLongClick(v: View?): Boolean {
                val date = getDateOfItem(adapter.items.size - 1)
                if (date != null) {
                    viewMessageFromDate(date, true)
                    return true
                }
                return false
            }
        })

        fab_down.setOnLongClickListener(object : View.OnLongClickListener {
            override fun onLongClick(v: View?): Boolean {
                val date = getDateOfItem(0)
                if (date != null) {
                    viewMessageFromDate(date, false)
                    return true
                }
                return false
            }
        })

        fab_up.setOnClickListener { view ->
            val pos =
                (messagesListView.layoutManager as LinearLayoutManager).findLastCompletelyVisibleItemPosition()
            if (pos != RecyclerView.NO_POSITION) {
                val date = getDateOfItem(pos)
                val selpos=adapter.selectedItemPosition.higher(pos)
                if (selpos != null) {
                    viewMessage(selpos)
                } else if (date != null)
                    viewMessageFromDate(date, true)
            }
        }
        fab_down.setOnClickListener { view ->
            val pos =
                (messagesListView.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
            if (pos != RecyclerView.NO_POSITION) {
                val date = getDateOfItem(pos)
                val selpos=adapter.selectedItemPosition.lower(pos)
                if (selpos != null) {
                    viewMessage(selpos)
                } else if (date != null)
                    viewMessageFromDate(date, false)
            }
        }

        query.setEndIconOnLongClickListener {
            query.editText?.setText("")
            searchVisible = false
            toggleFilterView(false)
            showKeyboard(this, false)
            true
        }

        query.setEndIconOnClickListener {
            query.editText?.setText("")
//            searchVisible = false
//            toggleFilterView(false)
            showKeyboard(this, true)
        }

        query.editText?.doOnTextChanged { text, start, count, after ->
            lastQueryText = text.toString()
            refreshListAndSelect(lastQueryText)
//            refreshList(query.editText?.text.toString())
        }

        query.editText?.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                lastQueryText = v.text.toString()
                config.lastQueryText = lastQueryText
//                searchVisible = false
//                toggleFilterView(false)
                true
            }
            false
        }

        Realm.init(this)
        realmconfig = RealmConfiguration.Builder()
            .name("kktviewer.realm")
//            .encryptionKey(getMyKey())
            .schemaVersion(2)
            .migration(KKTRealmMigration())
            .build()
        if (config.useDatabase) {
            query.editText?.setText(lastQueryText)
//            refreshList(config.lastSearchText)
        }

        if (lastViewedPosition >= 0) {
//            val llm = (messagesListView.layoutManager as LinearLayoutManager)
//            llm.scrollToPositionWithOffset(lastViewedPosition.toInt(), appBar.height)
            viewMessage(lastViewedPosition.toInt())
        }
    }

    private fun refreshListAndSelect(lastQueryText: String) {
        val sellist=HashSet<String>()
        sellist.addAll(adapter.selectedMessages.map {
            it.messageId!!
        })

        refreshList(lastQueryText)

        if (sellist.isEmpty()) return

        for (i in 0 until adapter.items.size) {
            if (adapter.items[i].item is KKTMessage) {
                if (sellist.contains((adapter.items[i].item as KKTMessage).messageId)) {
                    adapter.items[i].isSelected = true
                    adapter.selectedItemPosition.add(i)
                    adapter.notifyItemChanged(i)
                }
            }
        }
    }

    fun checkDbStatus(): Realm? {
        if (!useDatabase)
            return null

        val realm = Realm.getInstance(realmconfig)

        return realm
    }

    fun refreshList(query: String) {

        val realm = checkDbStatus() ?: return

        progressBarHolder.visibility = View.VISIBLE
        progressBar.isIndeterminate = true

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
        val reslist = results.subList(0, results.size)
        mMessageData.clear()
        mMessageData.addAll(reslist)
        //        for (item in l) {
        //            Log.d("mike", "item: ${item.toString()}")
        //        }

        adapter.clear()
        adapter.addToEnd(mMessageData, true)
        adapter.notifyDataSetChanged()
        adapter.currentQuery = query

        progressBarHolder.visibility = View.GONE
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
        } else {
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


    private fun getDateOfItem(pos: Int): Date? {
        var date: Date? = null

        if (pos < 0 || pos > adapter.items.size - 1)
            return null

        with(adapter.items[pos]) {
            if (item is Date) {
                if (pos == adapter.items.size - 1)
                    date = ((item) as Date)
                else
                    return getDateOfItem(pos + 1)
            } else if (item is IMessage)
                date = ((item) as KKTMessage).createdAt!!
        }

        return date!!
    }

    private fun initData() {

        config = KKTConfig(this)

        // authorId
        if (config.authorId.isEmpty())
            config.authorId = "최현일"

        val viewDate = if (config.lastViewedDate > 0L)
            DateConversion.millsToLocalDateTime(config.lastViewedDate)
        else
            LocalDateTime.now()
    }


    private fun saveCurrentState() {
        config.lastQueryText = lastQueryText
        config.lastViewedDate = lastViewedDate
        config.lastViewedPosition = lastViewedPosition
        config.useDatabase = useDatabase
    }


    override fun onBackPressed() {
        saveCurrentState()

        super.onBackPressed()
    }

    override fun onLoadMore(page: Int, totalItemsCount: Int) {
        Log.i("mike", "onLoadMore: $page $totalItemsCount")
        /*
        if (totalItemsCount < com.stfalcon.chatkit.sample.features.demo.DemoMessagesActivity.TOTAL_MESSAGES_COUNT) {
            loadMessages()
        }
         */
    }

    override fun onSelectionChanged(count: Int) {
        this.selectionCount = count

        if (mMenu == null) return

        val item:MenuItem?=mMenu?.findItem(R.id.copyMessage)
        if (item == null) return

        item.isVisible=count > 0

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


    private fun viewMessage(position:Int, offset:Int=-1) {
        val newoffset = (messagesListView.height - appBarLayoutHeight) / 2
        val llm = (messagesListView.layoutManager as LinearLayoutManager)
        llm.scrollToPositionWithOffset(position, if (offset >= 0) offset else newoffset)
    }


    private fun getMessagePositionByDate(date: Date): Int {
        val idx = mMessageData.asReversed().binarySearch {
            val dt0 = it.createdAt!!
            DateUtils.truncatedCompareTo(dt0, date, Calendar.DAY_OF_MONTH)
        }

        return idx
    }

    private fun viewMessageFromDate(date: Date, toBeginning: Boolean) {
        val md = mMessageData.asReversed()
        val idx = getMessagePositionByDate(date)
        if (idx < 0)
            Toast.makeText(this, "Cannot find message", Toast.LENGTH_SHORT).show()
        else {
//            Log.d("mike", "Found position: ${md[idx]}")
            val startIdx = getStartIndexOfDay(idx, toBeginning)
            val datePos = adapter.getDateHeaderPosition(md[startIdx].createdAt)

            val posDate = min(adapter.itemCount - 1, max(0, adapter.itemCount - datePos - 1))
            val absDatePos = min(adapter.itemCount - 1, max(0, adapter.itemCount - posDate - 1))
            val llm = (messagesListView.layoutManager as LinearLayoutManager)

            val vdate = messagesListView.findViewHolderForAdapterPosition(datePos)
//            val dateheadersize=if (adapter.dateHeaderHeight==0) spToPx(resources.getDimension(R.dimen.message_date_header_text_size), this) else adapter.dateHeaderHeight
            val dateheadersize = adapter.dateHeaderHeight
            val offset = messagesListView.bottom - dateheadersize - appBarLayoutHeight
            llm.scrollToPositionWithOffset(absDatePos, offset)
        }
    }

    private fun viewMessageFromDate(year: Int, month: Int, dayOfMonth: Int, toBeginning: Boolean) {
        val lt = LocalDateTime.of(year, month, dayOfMonth, 0, 0, 0)
        val date = Date.from(lt.atZone(ZoneId.systemDefault()).toInstant())
        viewMessageFromDate(date, toBeginning)
    }

    fun spToPx(sp: Float, context: Context): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            context.getResources().getDisplayMetrics()
        ).toInt()
    }

    private fun getStartIndexOfDay(curIdx: Int, toBeginning: Boolean): Int {
        // 날짜가 바뀌지 않을 때 까지 반복한다
        val ml = mMessageData.asReversed()
        val dt1 = ml[curIdx].createdAt!!
        var startIdx = 0

        // find next day
        if (!toBeginning) {
            startIdx = mMessageData.size - 1
            for (j in curIdx + 1 until mMessageData.size) {
                val dt0 = ml[j].createdAt!!
                if (!DateUtils.isSameDay(dt0, dt1)) {
                    startIdx = j
                    break
                }
            }
        } else { // find prev day
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


    fun toggleMenuItemEnabled(rid: Int, isEnabled: Boolean) {
        if (mMenu == null)
            return

        val item = mMenu?.findItem(rid)
        item?.isEnabled = isEnabled
        item?.icon?.alpha = if (isEnabled) 0xFF else 0x7F
    }

    fun toggleMenuItemVisible(rid: Int, isVisible: Boolean) {
        if (mMenu == null)
            return

        val item = mMenu?.findItem(rid)
        item?.isVisible = isVisible
//        item?.icon?.alpha=if (isVisible) 0xFF else 0x7F
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("lastQueryText", lastQueryText)
        outState.putLong("lastViewedDate", lastViewedDate)
        outState.putLong("lastViewedPosition", lastViewedPosition)
        outState.putBoolean("useDatabase", useDatabase)

        saveCurrentState()

        super.onSaveInstanceState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        mMenu = menu

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
                getLogFilePathFromUser(REQUEST_CODE_IMPORT_TEXT)
                true
            }
            R.id.mergeLog -> {
                getLogFilePathFromUser(REQUEST_CODE_MERGE_TEXT)
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
                showKeyboard(this, searchVisible)
                toggleFilterView(searchVisible)
                true
            }
            R.id.copyMessage -> {
                copyMessages()
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    private fun copyMessages(): Boolean {

        val sm=adapter.selectedMessageWithPosition
        var s=""
        for (item in sm) {
            s+=DateFormatUtils.ISO_8601_EXTENDED_DATETIME_TIME_ZONE_FORMAT.format(item.second.createdAt) + "\n"
            s+=item.second.messageText + "\n"
        }

        val clipboard=getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip=ClipData.newPlainText("text", s)
        clipboard.setPrimaryClip(clip)

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Confirm")
        builder.setMessage("Unselect copied messages?")
        builder.setPositiveButton(getString(R.string.yes),
            DialogInterface.OnClickListener { dialog, id ->
                // User clicked OK button
                val positions=ArrayList<Int>()
                for (item in sm) {
                    val pos=item.first
                    positions.add(pos)
                }
                adapter.unSelectItems(positions)
            })
        builder.setNegativeButton(getString(R.string.no), null)
        builder.show()

        showToast("${adapter.selectedMessages.size} messages copied")

        return true
    }

    private fun openDb(): Boolean {
        val realm = Realm.getInstance(realmconfig)
        val result = realm.where(KKTMessage::class.java).findAll().sort("messageId", Sort.ASCENDING)
        mMessageData.clear()
        mMessageData.addAll(result.subList(0, result.size))
        adapter.notifyDataSetChanged()
        realm.close()

        return true
    }

    private fun getClearedUtc(): Calendar {
        val utc: Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utc.clear()
        return utc
    }

    private fun getDateFromUser() {

        val datelist = ArrayList<Long>()
        val selecteddate: Long

        for (item in adapter.dateSet) {
            var cal = getClearedUtc()
//            cal.time=DateUtils.truncate(item, Calendar.DAY_OF_MONTH)
            cal.time = item
            cal.set(Calendar.HOUR, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            datelist.add(cal.timeInMillis)
        }

        selecteddate = if (lastViewedDate == 0L)
            datelist.min()!!
        else
            lastViewedDate

        val builder: MaterialDatePicker.Builder<*> =
            MaterialDatePicker.Builder.datePicker().setSelection(selecteddate)
        val constraintsBuilder: CalendarConstraints.Builder =
            CalendarConstraints.Builder().setValidator(DateListValidator(datelist))

        val startdate = datelist.min()!!
        val enddate = datelist.max()!!
        constraintsBuilder.setStart(startdate)
        constraintsBuilder.setEnd(enddate)
        constraintsBuilder.setOpenAt(selecteddate)

        builder.setCalendarConstraints(constraintsBuilder.build())
        val picker = builder.build()
        picker.addOnPositiveButtonClickListener {
//            Toast.makeText(this, picker.headerText, Toast.LENGTH_LONG).show()
            val selection = picker.selection as Long
            lastViewedDate = selection
            val ldt = DateConversion.millsToLocalDateTime(selection)
            viewMessageFromDate(ldt!!.year, ldt.monthValue, ldt.dayOfMonth, true)
        }
        picker.show(supportFragmentManager, picker.toString())
    }


    private fun showToast(text:String, offset:Int=-1) : Toast {
        var newoffset = if (offset >= 0) offset else appBarLayout.height
        val toast = Toast.makeText(this, text, Toast.LENGTH_SHORT)
        toast.setGravity(Gravity.TOP, 0, newoffset)
        toast.show()
        return toast
    }


    private fun saveLog() {

        val toast=showToast("Saving ...")
        val realm = Realm.getInstance(realmconfig) ?: return

        progressBarHolder.visibility = View.VISIBLE
        progressBar.isIndeterminate = false

        if (useDatabase || !realm.isEmpty) {
            realm.executeTransaction {
                it.deleteAll()
            }
            useDatabase = false
            config.useDatabase = useDatabase
        }

        // if we run this code block in background thread, exception occurs.
        //     java.lang.IllegalStateException: Realm access from incorrect thread.
        //     Realm objects can only be accessed on the thread they were created.
        val messagesize=mMessageData.size.toLong()
        realm.executeTransactionAsync {
            for ((index, message) in mMessageData.withIndex()) {
                // date message inserted by ChatKit
                if (message.isNull())
                    continue
                val m = message
//                    it.copyToRealmOrUpdate(m)
                it.insertOrUpdate(m)
                runOnUiThread {
                    onProgressListener.onProgressChanged((index+1).toLong(), messagesize)
                }
            }
        }

        useDatabase = true
        // saved db is important
        config.useDatabase = useDatabase
        progressBarHolder.visibility = View.GONE

        toggleMenuItemVisible(R.id.saveLog, false)
        toast.setText("Successfully saved")
        toast.show()

    }


    private fun openLog(uri: Uri) {
//        val offset =
//            appBar.height + progressBarHolder.height + if (progressBar.visibility == View.VISIBLE) progressBar.height else 0
        val toast = showToast("Importing ...")

        val runnable = Runnable {
            val reader = KKTChatTextReader(this, config.authorId)
            reader.setOnProgressChanged(onProgressListener)

            runOnUiThread {
                progressBarHolder.visibility = View.VISIBLE
                progressBar.isIndeterminate = false
            }

            mMessageData = reader.readFile(uri)

            runOnUiThread {
                lastViewedDate = 0L
                lastViewedPosition = 0L
                useDatabase = false
                lastQueryText = ""

                progressBar.isIndeterminate=true

                adapter.clear()
                adapter.addToEnd(mMessageData, true)
                adapter.notifyDataSetChanged()

                for (item in listOf(R.id.saveLog, R.id.moveToDay, R.id.findMessage)) {
                    toggleMenuItemEnabled(item, mMessageData.isNotEmpty())
                }
                toggleMenuItemVisible(R.id.saveLog, true)

                progressBarHolder.visibility = View.GONE

                toast.setText("Imported ${mMessageData.size} messages")
                toast.show()
            }
        }
        val thread = Thread(runnable)
        thread.start()
    }


    private fun initSampleData() {
//        var msgList:List<KKTMessage> = KKTChatLogReader(this).readTextLog(null, config.authorId)
//        var msgList:List<KKTMessage> = KKTChatTextReader(this, config.authorId).readFile(null)
//
//        //TODO: remove
//        for (msg in msgList) {
//            msg.author?.avatarUri=
//                when (msg.author?.authorAlias) {
//                    "John Doe" -> {
//                        "android.resource://" + packageName + "/" + R.drawable.neofrodo02
//                    }
//                    "Jane Doe" -> {
//                        "android.resource://" + packageName + "/" + R.drawable.neofrodo03
//                    }
//                    "Zeus" -> {
//                        "android.resource://" + packageName + "/" + R.drawable.neofrodo04
//                    }
//                    else -> {
//                        "android.resource://" + packageName + "/" + R.drawable.neofrodo05
//                    }
//                }
//        }
    }

    private fun getLogFilePathFromUser(reqcode:Int) {
        var intent: Intent = Intent()
            .setType("text/*")
            .setAction(Intent.ACTION_GET_CONTENT)
//            .setData(Uri.parse(config.lastOpenedFile))

        startActivityForResult(
            Intent.createChooser(intent, "Select a file"),
            reqcode
        )
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
            REQUEST_CODE_MERGE_TEXT -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    var uri: Uri? = data.data ?: return
                    mergeLog(uri!!)
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

    private fun mergeLog(uri: Uri) {
        val toast = showToast("Merging ...")

        val runnable = Runnable {
            val reader = KKTChatTextReader(this, config.authorId)
            reader.setOnProgressChanged(onProgressListener)

            runOnUiThread {
                progressBarHolder.visibility = View.VISIBLE
                progressBar.isIndeterminate = false
            }

            val newMessageData = reader.readFile(uri, mMessageData.size)

            runOnUiThread {
                progressBar.isIndeterminate = true
            }

            runOnUiThread {
                val delta=mergeMessageData(mMessageData.asReversed(), newMessageData)

                lastViewedDate = 0L
                lastViewedPosition = 0L
                useDatabase = false
                lastQueryText = ""

                adapter.clear()
                adapter.addToEnd(mMessageData, false)
                adapter.notifyDataSetChanged()

                for (item in listOf(R.id.saveLog, R.id.moveToDay, R.id.findMessage)) {
                    toggleMenuItemEnabled(item, mMessageData.isNotEmpty())
                }
                toggleMenuItemVisible(R.id.saveLog, true)

                progressBarHolder.visibility = View.GONE

                toast.setText("Merged $delta messages")
                toast.show()
            }
        }
        val thread = Thread(runnable)
        thread.start()
    }

    private fun mergeMessageData(
        mMessageData: MutableList<KKTMessage>,
        newMessageData: MutableList<KKTMessage>
    ) : Int {
        val s0 = mMessageData.first().messageTime!!
        val e0 = mMessageData.last().messageTime!!
        val s1 = newMessageData.first().messageTime!!
        val e1 = newMessageData.last().messageTime!!
        val size0=mMessageData.size

        if (s0 > s1 && e0 > e1) {
            // 이전 메시지
            mMessageData.addAll(0, newMessageData.filter{
                it.createdAt!!.toInstant().toEpochMilli() <= s0.toInstant().toEpochMilli()
            })
        } else if (s0 > s1 && e0 < e1) {
            mMessageData.addAll(0, newMessageData.filter{
                it.createdAt!!.toInstant().toEpochMilli() <= s0.toInstant().toEpochMilli()
            })
            mMessageData.addAll(newMessageData.filter {
                it.createdAt!!.toInstant().toEpochMilli() >= e0.toInstant().toEpochMilli()
            })
        } else if (s0 < s1 && e0 > e1) {
            // no message to import
        } else { // (s0 < s1 && e0 < e1)
            mMessageData.addAll(newMessageData.filter {
                it.createdAt!!.toInstant().toEpochMilli() >= e0.toInstant().toEpochMilli()
            })
        }

        mMessageData.sortBy {
            it.createdAt
        }

        return (mMessageData.size-size0)
    }


    override fun onMessageLongClick(message: KKTMessage?) {
        val clipboard=getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip=ClipData.newPlainText("text", message!!.messageText);
        clipboard.setPrimaryClip(clip)

        showToast("Copied to clipboard")
    }

    // not working smoothly
    fun showKeyboard(activity: Activity, show:Boolean) {
        val imm  = activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        var view = activity.getCurrentFocus();
        if (view == null) {
            view = View(activity);
        }
        if (show) {
            imm.showSoftInput(view, 0)
        } else {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0)
//            view.clearFocus()
        }
    }
}
