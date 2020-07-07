@file:Suppress("SpellCheckingInspection")

package com.soonsim.kktlogviewer

import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.soonsim.kktlogviewer.DateConversion.Companion.getISOString
import com.stfalcon.chatkit.commons.models.IMessage
import com.stfalcon.chatkit.messages.MessagesList
import com.stfalcon.chatkit.messages.MessagesListAdapter
import com.stfalcon.chatkit.messages.MessagesListAdapter.HoldersConfig
import com.stfalcon.chatkit.utils.DateFormatter
import io.realm.*
import kotlinx.android.synthetic.main.activity_main.*
import org.apache.commons.lang3.time.DateFormatUtils
import org.apache.commons.lang3.time.DateUtils
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


const val MESSAGE_READ_COMPLETE=100


class MainActivity : AppCompatActivity(),
    MessagesListAdapter.SelectionListener,
    MessagesListAdapter.OnLoadMoreListener,
    MessagesListAdapter.OnMessageLongClickListener<KKTMessage>  {
    private val imageLoader = KKTImageLoader(this)
    lateinit var config: KKTConfig
    private var mMessageData = ArrayList<KKTMessage>()
    lateinit var adapter: MessagesListAdapter<KKTMessage>
    private var selectionCount = 0
    private lateinit var realmconfig: RealmConfiguration
    private var mMenu: Menu? = null
    private var searchVisible: Boolean = false
    private var lastViewedPosition = 0L
    private var lastQueryText = ""
    private var lastViewedDate = 0L
    private var appBarLayoutHeight = 0
    private var handler:Handler? = null
    private lateinit var viewManager:LinearLayoutManager
    lateinit var messagesListViewLayoutParam:ViewGroup.LayoutParams
    private lateinit var messagesListView:MessagesList
    private var realmResult:RealmResults<KKTMessage>?=null
    private var realmQuery:RealmQuery<KKTMessage>?=null
    private lateinit var realmmain:Realm
    private var selectedItemPosition=ArrayList<Int>()
//    private var toolbar:Toolbar? = null
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
                if (progressBar.progress % 100 == 0 || progressBar.max == progressBar.progress) {
                    progressText.text = "$position/$totalCount lines"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))


        // clear the previous logcat and then write the new one to the file

//        val appDirectory =
//            File(Environment.getExternalStorageDirectory().toString())
//
//        val logDirectory = File(appDirectory.toString())

//        val logDirectory: String = filesDir.absolutePath
        val logDirectory=getExternalFilesDir(null)
        val logFile =
//            File(logDirectory, "logcat" + System.currentTimeMillis() + ".txt")
            File(logDirectory, "KKTViewerLog" + ".txt")
        // clear the previous logcat and then write the new one to the file
        try {
            var process = Runtime.getRuntime().exec("logcat -c")
            process = Runtime.getRuntime()
                .exec("logcat -f $logFile *:D MainActivity:D")
        } catch (e: IOException) {
            e.printStackTrace()
        }
        Log.d("mike", "Logging started")

        config = KKTConfig(this)

        // authorId
        if (config.authorId.isEmpty())
            config.authorId = "최현일"

        if (savedInstanceState != null) {
            lastQueryText = savedInstanceState.getString("lastQueryText")!!
            lastViewedDate = savedInstanceState.getLong("lastViewedDate")
            lastViewedPosition = savedInstanceState.getLong("lastViewedPosition")
            selectedItemPosition.addAll(savedInstanceState.getIntArray("selectedItemPosition")!!.toList())
        } else {
            lastViewedPosition = config.lastViewedPosition
            lastQueryText = config.lastQueryText
            lastViewedDate = config.lastViewedDate
            if (config.selectedItemPosition != null)
                selectedItemPosition.addAll(config.selectedItemPosition!!.map { it -> it.toInt()})
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

        viewManager=LinearLayoutManager(this)
        messagesListView=findViewById<MessagesList>(R.id.messagesListView).apply {
            setHasFixedSize(false)
            layoutManager=viewManager
//            adapter=adapter
        }

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
//            Log.d("mike", "appBarHeight: $appBarLayoutHeight, offset: $verticalOffset")

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
            refreshListAndSelect("", doFreshQuery = true)
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
//            lastQueryText = text.toString()
//            refreshListAndSelect(lastQueryText)
//            refreshList(query.editText?.text.toString())
        }

        query.editText?.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                lastQueryText = v.text.toString()
                config.lastQueryText = lastQueryText
                refreshListAndSelect(lastQueryText, doFreshQuery = true)
//                searchVisible = false
//                toggleFilterView(false)
                true
            }
            false
        }

        Realm.init(this)
        realmmain = Realm.getInstance(buildRealmConfig())

        query.editText?.setText(lastQueryText)
        refreshListAndSelect(lastQueryText, false, doFreshQuery = true)

        if (lastViewedPosition >= 0) {
            viewMessage(lastViewedPosition.toInt())
        }

        // onCreate 에서 itemview 접근할 때 null 포인터 방지
        messagesListView.viewTreeObserver.addOnGlobalLayoutListener(object: ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (selectedItemPosition.size > 0) {
                    for (pos in selectedItemPosition.filter {
                        it < adapter.items.size
                    }) {
                        // method#1: 아답터 item 바로 접근. adapter 내의 selectedItemPositions
                        // 데이터 무결성이 깨짐
//                        adapter.items[pos].isSelected=true
//                        adapter.notifyItemChanged(pos)

                        // method#2 : pos 에 있는 아이템이 화면에 보이면 동작하나
                        // 화면에 보이지 않을 경우 동작 안함(클릭을 못하니까)
//                        val itemView = messagesListView.getChildAt(pos)
//                        itemView?.performClick()

                        // method3 : 아답터에서 선택하게 함
                        adapter.selectItem(pos)
                    }
                    selectedItemPosition.clear()
                }
                messagesListView.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })

//        if (selectedItemPosition.size > 0) {
//            for (it in selectedItemPosition) {
//                Handler().postDelayed({
//                    messagesListView.findViewHolderForAdapterPosition(it)?.itemView?.performClick()
//                }, 1)
//            }
//        }
        createUpdateUiHandler()
    }

    private fun createUpdateUiHandler() {
        if (handler == null) {
            handler = object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(msg: Message) {
                    // Means the message is sent from child thread.
                    if (msg.what === MESSAGE_READ_COMPLETE) {
//                        if (realmmain.isEmpty) {
//                            realmmain.close()
//                            realmmain=Realm.getInstance(buildRealmConfig())
//                        }
//                        // Update ui in main thread.
//                        lastViewedDate = 0L
//                        lastViewedPosition = 0L
//                        lastQueryText = ""
//
//                        progressBar.isIndeterminate = true
//                        refreshListAndSelect(lastQueryText, false)
//
//                        for (item in listOf(R.id.saveLog, R.id.moveToDay, R.id.findMessage)) {
//                            toggleMenuItemEnabled(item, mMessageData.isNotEmpty())
//                        }
//                        toggleMenuItemVisible(R.id.saveLog, true)
//
//                        progressBarHolder.visibility = View.GONE
//
//                        showToast("Imported ${mMessageData.size} messages")
                    }
                }
            }
        }
    }

    private fun buildRealmConfig(): RealmConfiguration {
        return RealmConfiguration.Builder()
            .directory(getExternalFilesDir(null)!!)
            .name("kktviewer.realm")
//            .encryptionKey(getMyKey())
            .schemaVersion(2)
            .migration(KKTRealmMigration())
            .build()
    }

    private fun refreshListAndSelect(query: String, controlProgress:Boolean=true, doFreshQuery:Boolean=false) {
        // keep selection
        val sellist=HashSet<String>()
        sellist.addAll(adapter.selectedMessages.map {
            it.messageId!!
        })

//        val realm = checkDbStatus() ?: return
        if (controlProgress) {
            progressBarHolder.visibility = View.VISIBLE
            progressBar.isIndeterminate = true
        }

        if (realmQuery == null || doFreshQuery)
            realmQuery=realmmain.where(KKTMessage::class.java)

        if (query.isNotEmpty()) {
            realmQuery = realmQuery!!.contains("messageText", query, Case.INSENSITIVE)
            lastQueryText = query
        }

        if (realmResult == null || doFreshQuery)
            realmResult = realmQuery!!.findAll().sort(arrayOf("messageTime"), arrayOf(Sort.ASCENDING))

//        Log.i("mike", "realmresult: ${realmResult!!.size}")

//        if (realmResult.isNotEmpty()) {
            mMessageData.clear()
            mMessageData.addAll(realmResult!!.toList())

            adapter.clear()
            adapter.addToEnd(mMessageData, true)
            adapter.notifyDataSetChanged()
            adapter.currentQuery = query

            if (sellist.isNotEmpty()) {
                //TODO: Selection by filter
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
//2

        if (controlProgress) {
            progressBarHolder.visibility = View.GONE
        }
//        realmResult.addChangeListener { results, changeSet ->
    }


    private fun toggleFilterView(isVisible: Boolean) {

//        mFirstTouch = 0F
        val height = if (isVisible) 0 else query.height
//        if (!isVisible) {
//            this.currentFocus?.let { focusView ->
//                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
//                imm.hideSoftInputFromWindow(focusView.windowToken, 0)
//            }
//        }
//
        if (!isVisible) {
            query.visibility = View.GONE
//            appBarLayoutHeight=appBarLayout.height - query.height
        } else {
            query.visibility = View.VISIBLE
//            appBarLayoutHeight=appBarLayout.height + query.height
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
        val hs=HashSet<String>()
        hs.addAll(adapter.selectedItemPosition.map { it -> it.toString() } )
        config.selectedItemPosition=hs
    }


    override fun onBackPressed() {
        saveCurrentState()

        realmmain.close()

        super.onBackPressed()
    }

    override fun onLoadMore(page: Int, totalItemsCount: Int) {
//        Log.i("mike", "onLoadMore: $page $totalItemsCount")
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
        //TODO: reversed -> normal
        val idx = mMessageData.asReversed().binarySearch {
            val dt0 = it.createdAt!!
            DateUtils.truncatedCompareTo(dt0, date, Calendar.DAY_OF_MONTH)
        }

        return idx
    }

    private fun viewMessageFromDate(date: Date, toBeginning: Boolean) {
        //TODO: reversed -> normal
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
            var offset = messagesListView.bottom - dateheadersize - appBarLayoutHeight
//            if (absDatePos==adapter.itemCount-1)
//                offset+=appBarLayoutHeight
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
        //TODO: reversed -> normal
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
        outState.putIntArray("selectedItemPosition", adapter.selectedItemPosition.toIntArray())

        saveCurrentState()

        super.onSaveInstanceState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        mMenu = menu

        // default state
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
            R.id.deleteMessages -> {
                deleteMessages()
                true
            }
            R.id.deleteDb -> {
                deleteDb()
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
            R.id.statistics -> {
                showStatistics()
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    private fun deleteMessages() {
        val dlg = androidx.appcompat.app.AlertDialog.Builder(this)
        dlg.setTitle("Delete messages")
        dlg.setMessage("Delete message between 00:00 at start date and 00:00 at end date")
        val inflater: LayoutInflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val dialogView: View = inflater.inflate(R.layout.dialog_delete_messages, null)
        dlg.setView(dialogView)
        val es=dialogView.findViewById<EditText>(R.id.startDate)
        val ee=dialogView.findViewById<EditText>(R.id.endDate)
        es.setText(config.deleteMessagesFrom)
        ee.setText(config.deleteMessagesTo)
        ee.selectAll()
        dlg.setNegativeButton(R.string.cancel, null)
        dlg.setPositiveButton(R.string.ok) { dialog, which ->
            fun q() = realmmain.where(KKTMessage::class.java)
//            val ds=DateUtils.par
            val date1: Date? = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).parse(es.text.toString())
            val date2: Date? = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).parse(ee.text.toString())
            if (date1 == null || date2 == null) {
                showToast("Invalid date format")
                return@setPositiveButton
            }
            val res=q().between("messageTime", date1, date2).findAll()
            val delsize=res.count()
            realmmain.executeTransaction {
                res.deleteAllFromRealm()
            }
            refreshListAndSelect(lastQueryText, true, false)
            showToast("Deleted $delsize messages")

            config.deleteMessagesFrom=es.text.toString()
            config.deleteMessagesTo=ee.text.toString()
        }

        // show ime : NOT WORKING
        /*
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
         */
        dlg.show()
    }


    private fun getStatisticsMessage() : String {
        var msgstr:String=""

        fun q() = realmmain.where(KKTMessage::class.java)
        val allmsg=q().findAll()
        val totalcount=allmsg.count()
        val totallength=allmsg.sumBy {
            it.messageText!!.length
        }

        msgstr += "Count\n"
        // 사람별 메시지 크기(byte)
        val authors=realmmain.where(KKTAuthor::class.java).distinct("authorId").findAll()
        for (a in authors) {
            val m=q().equalTo("author.authorId", a.authorId).findAll()
            val ml=m.count()
            msgstr += a.authorAlias + " : "
            msgstr += "$ml (" + "%.1f%%".format(100 * ml.toFloat() / totalcount) + ")\n"
        }

        msgstr += "\n"
        msgstr += "Length\n"
        for (a in authors) {
            val m=q().equalTo("author.authorId", a.authorId).findAll()
            val ml=m.sumBy {
                it.messageText!!.length
            }
            msgstr += a.authorAlias + " : "
            msgstr += "$ml (" + "%.1f%%".format(100 * ml.toFloat() / totallength) + ")\n"
        }

        return msgstr
    }

    private fun showStatistics() {
        val builder=AlertDialog.Builder(this)
            .setTitle("Simple statistics")
            .setMessage(getStatisticsMessage())
            .setPositiveButton(R.string.ok, null)
        builder.show()
    }

    private fun deleteDb() {
        val builder=AlertDialog.Builder(this)
            .setTitle("Confirm")
            .setMessage("Delete current database?\n(Your file remains)")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                realmmain.executeTransaction {
                    it.deleteAll()
                }
                refreshListAndSelect("")
            }
        builder.show()
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

    private fun getClearedUtc(): Calendar {
        val utc: Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utc.clear()
        return utc
    }

    private fun getDateFromUser() {

        val datelist = ArrayList<Long>()
        var selecteddate: Long = 0L

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

        val startdate = datelist.min()!!
        val enddate = datelist.max()!!
        selecteddate = if (lastViewedDate == 0L)
            datelist.max()!!
        else
            lastViewedDate

        selecteddate= max(startdate, min(selecteddate, enddate))

        val builder: MaterialDatePicker.Builder<*> =
            MaterialDatePicker.Builder.datePicker().setSelection(selecteddate)
        val constraintsBuilder: CalendarConstraints.Builder =
            CalendarConstraints.Builder().setValidator(DateListValidator(datelist))

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


    private fun openLog(uri: Uri) {
//        val offset =
//            appBar.height + progressBarHolder.height + if (progressBar.visibility == View.VISIBLE) progressBar.height else 0
//        val toast = showToast("Importing started ...")

        realmmain.executeTransaction {
            it.deleteAll()
        }
//        deleteDb()

        val runnable = Runnable {
            runOnUiThread {
                progressBarHolder.visibility = View.VISIBLE
                progressBar.isIndeterminate = false
            }

            val realmlocal=Realm.getInstance(buildRealmConfig())

            runOnUiThread {
                progressText.text="Parsing log file ..."
            }

            val reader = KKTChatTextReader(this, config.authorId)
            reader.setOnProgressChanged(onProgressListener)
            val list=reader.readFile(uri)

            runOnUiThread {
                progressBar.isIndeterminate = false
                progressText.text="Inserting messages to database ..."
            }

            realmlocal.executeTransaction { realmit ->
                list.forEachIndexed { index, kktMessage ->
                    realmit.insertOrUpdate(kktMessage)
                    Thread.yield()
                    onProgressListener.onProgressChanged((index+1).toLong(), list.size.toLong())
                }
            }

            realmlocal.close()

            runOnUiThread {
                progressBar.isIndeterminate = true
                progressText.text="Loading messages ..."
            }

            Thread.yield()

            runOnUiThread {
                realmmain.close()
                realmmain=Realm.getInstance(buildRealmConfig())

                lastViewedDate = 0L
                lastViewedPosition = 0L
                lastQueryText = ""

                progressBar.isIndeterminate = true
                refreshListAndSelect(lastQueryText, false, doFreshQuery = true)

                for (item in listOf(R.id.moveToDay, R.id.findMessage)) {
                    toggleMenuItemEnabled(item, mMessageData.isNotEmpty())
                }

                progressBarHolder.visibility = View.GONE

                viewMessageFromDate(mMessageData.first().createdAt!!, true)

//                toast.setText("Imported ${mMessageData.size} messages")
//                toast.show()
            }

//            val sendingmessage=Message()
//            sendingmessage.what=MESSAGE_READ_COMPLETE
//            handler?.sendMessage(sendingmessage)
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
//        val runnable = Runnable {
//            val realmlocal=Realm.getInstance(buildRealmConfig())
            val reader = KKTChatTextReader(this, config.authorId)
            reader.setOnProgressChanged(onProgressListener)

//            runOnUiThread {
                progressBarHolder.visibility = View.VISIBLE
                progressBar.isIndeterminate = false
                progressText.text="Parsing log file ..."
//            }

            val lastIdMessage = mMessageData.maxBy {
                it.messageId?.toInt()!!
            }
            val newMessageData = reader.readFile(uri, lastIdMessage?.messageId?.toInt())

//            runOnUiThread {
                progressBar.isIndeterminate = true
                progressText.text="Analyzing log file ..."
//            }

//            Thread.yield()

//            val threadMessageData=ArrayList<KKTMessage>()
//            threadMessageData.addAll(realmlocal.where(KKTMessage::class.java).findAll().sort(arrayOf("messageTime"), arrayOf(Sort.ASCENDING)))

            val threadMessageData=mMessageData

            val mergedMessageData=mergeMessageData(threadMessageData, newMessageData)

//            runOnUiThread {
                progressBar.isIndeterminate = false
                progressText.text="Inserting messages to database ..."
//            }

            reader.setOnProgressChanged(onProgressListener)
//            realmlocal.executeTransaction { realm ->
//                threadMessageData.forEachIndexed { index, kktMessage ->
//                    realm.insertOrUpdate(kktMessage)
//                    Thread.yield()
//                    runOnUiThread {
//                        onProgressListener.onProgressChanged((index+1).toLong(), threadMessageData.size.toLong())
//                    }
//                }
//            }

            realmmain.executeTransactionAsync( {
                mergedMessageData.forEachIndexed { index, kktMessage ->
                    it.insertOrUpdate(kktMessage)
//                    Thread.yield()
                    Log.d("mike", "realmmain.executeTransactionAsync: $index")
                    runOnUiThread {
                        onProgressListener.onProgressChanged((index+1).toLong(), mergedMessageData.size.toLong())
                    }
                }
            }, {
                lastViewedDate = 0L
                lastViewedPosition = 0L
                lastQueryText = ""

                refreshListAndSelect(lastQueryText, controlProgress = false)

                for (item in listOf(R.id.moveToDay, R.id.findMessage)) {
                    toggleMenuItemEnabled(item, mMessageData.isNotEmpty())
                }

                progressBarHolder.visibility = View.GONE
            }, {

            }
            )

//            runOnUiThread {
//                progressBar.isIndeterminate = true
//                progressText.text="Loading messages ..."
//            }

//            realmlocal.close()

            //TODO: reversed --> normal

//            runOnUiThread {
//                realmmain.close()
//                realmmain=Realm.getInstance(buildRealmConfig())

//                lastViewedDate = 0L
//                lastViewedPosition = 0L
//                lastQueryText = ""
//
//                refreshListAndSelect(lastQueryText, controlProgress = false)
//
//                for (item in listOf(R.id.moveToDay, R.id.findMessage)) {
//                    toggleMenuItemEnabled(item, mMessageData.isNotEmpty())
//                }
//
//                progressBarHolder.visibility = View.GONE
//            }
//        }
//        val thread = Thread(runnable)
//        thread.start()
    }

    private fun mergeMessageData(
        oldMessageData: MutableList<KKTMessage>,
        newMessageData: MutableList<KKTMessage>
    ) : ArrayList<KKTMessage> {
//        val so = oldMessageData.last().messageTime!!
        val so = oldMessageData.minBy { it.messageTime!! }!!.messageTime
        val eo = oldMessageData.maxBy { it.messageTime!! }!!.messageTime
        val sn = newMessageData.minBy { it.messageTime!! }!!.messageTime
        val en = newMessageData.maxBy { it.messageTime!! }!!.messageTime
        val szo=oldMessageData.size
        val diffMessageData=ArrayList<KKTMessage>()

        if (sn!! < so!!) {
            val l=newMessageData.filter{
                it.messageTime!!.toInstant().toEpochMilli() < so.toInstant().toEpochMilli()}
            diffMessageData.addAll(0, l)
        }

        if (en!! > eo!!) {
            val l=newMessageData.filter{
                it.messageTime!!.toInstant().toEpochMilli() > eo.toInstant().toEpochMilli() }
            diffMessageData.addAll(l)
        }

        diffMessageData.sortBy {
            it.messageTime
        }

        return diffMessageData
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
