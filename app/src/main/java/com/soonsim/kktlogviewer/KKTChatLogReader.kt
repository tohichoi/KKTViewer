package com.soonsim.kktlogviewer

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.loader.content.CursorLoader
import java.io.*
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*


class KKTChatLogReader(private val context: Context) {

    fun readTextLog(uri: Uri?, myName:String?) : ArrayList<KKTMessage> {
        return KKTChatTextReader(context, null, myName).readFile(uri)
    }
}

class KKTChatTextReader(private val context: Context, val uri:Uri?=null, private val myName:String?) {

    var dataDirectory:String=""

    companion object {
        val TIME_PATTERN = """(\d{4})년 (\d{1,2})월 (\d{1,2})일 (오후|오전) (\d{1,2}):(\d{1,2})"""
        val MESSAGE_PATTERN = """, ([^:]+) : (.+)$"""
        val IMAGE_PATTERN="""(jpg|jpeg|gif|bmp|png|tif|tiff|tga|psd|ai)$"""
        val VIDEO_PATTERN="""(mp4|m4v|avi|asf|wmv|mkv|ts|mpg|mpeg|mov|flv|ogv)$"""
        val AUDIO_PATTERN="""(mp3|wav|flac|tta|tak|aac|wma|ogg|m4a)$"""
        val NAME_PATTERN="""[a-hA-H0-9]{64}\."""
        val DATA_PATTERN="""[a-zA-Z0-9]+$"""
        val patarr=listOf(
            Pair(FileType.IMAGE, NAME_PATTERN+IMAGE_PATTERN),
            Pair(FileType.VIDEO, NAME_PATTERN+VIDEO_PATTERN),
            Pair(FileType.AUDIO, NAME_PATTERN+AUDIO_PATTERN),
            Pair(FileType.DATA, NAME_PATTERN+DATA_PATTERN))

        enum class FileType {
            IMAGE, VIDEO, AUDIO, DATA, NONE
        }

        enum class LineType {
            HEADER, SAVING_TIME, CHUNK_START, MESSAGE, MESSAGE_CONTINUED, NONE
        }

        fun parseMedia(text:String) : Pair<FileType, String?> {
            var res1:MatchResult?

            for (item in patarr) {
                res1=Regex(item.second).find(text)
                if (res1 != null)
                    return Pair(item.first, text.trim())
            }

            return Pair(FileType.NONE, null)
        }

        fun parseDateTimeExpr(line:String) : Pair<Date, Int>? {
            val res1:MatchResult? = Regex(TIME_PATTERN).find(line)
            if (res1 != null) {
                val gv=res1.groupValues
                val lt=LocalDateTime.of(
                    gv[1].toInt(),
                    gv[2].toInt(),
                    gv[3].toInt(),
                    if (gv[4] == "오전") gv[5].toInt() else gv[5].toInt().rem(12) + 12,
                    gv[6].toInt()
                )
                val dt2=Date.from(lt.atZone(ZoneId.systemDefault()).toInstant())
                return Pair<Date, Int>(dt2, res1.range.last)
            }
            return null
        }

        fun parseMessageExpr(line:String) : Triple<Date, String, String>? {
            var nick="<Unknown>"
            var text=""
            val dtexpr:Pair<Date, Int>? = parseDateTimeExpr(line)

            if (dtexpr != null) {
                val res1:MatchResult? = Regex(MESSAGE_PATTERN).find(line)
                if (res1 != null) {
                    return Triple(dtexpr.first, res1.groupValues[1], res1.groupValues[2] + "\n")
                }
            }
            return null
        }
    }


    private fun getLineType(line:String) : Pair<LineType, Any?> {
        var mr:MatchResult? = null
        var message=KKTMessage()

        mr=Regex("(.+) 카카오톡 대화$").find(line)
        if (mr != null) {
            return Pair(LineType.HEADER, mr.groupValues[1])
        }

        mr=Regex("저장한 날짜 : $TIME_PATTERN").find(line)
        if (mr != null) {
            return Pair(LineType.SAVING_TIME, parseDateTimeExpr(line.substring(mr.range))?.first)
        }

        mr=Regex(TIME_PATTERN + "$").find(line)
        if (mr != null) {
            return Pair(LineType.CHUNK_START, parseDateTimeExpr(line.substring(mr.range))?.first)
        }

        mr=Regex(TIME_PATTERN + """, ([^:])+: (.+)""").find(line)
        if (mr != null) {
            return Pair(LineType.MESSAGE, parseMessageExpr(line))
        }

        return Pair(LineType.MESSAGE_CONTINUED, line)
    }


    public fun readFile(uri:Uri?=null) : ArrayList<KKTMessage> {
        val messageList=ArrayList<KKTMessage>()
        var count = 0
        var chunkcount = 0
        var ins: InputStream?
        var f: File
        var text: String = ""
        var time:Date?=null
        var nick=""
        var prevlt:LineType = LineType.NONE

        dataDirectory=File(uri?.path!!).parent!!

        if (uri.scheme.equals("file")) {
            f = File(uri.toString())
            ins = FileInputStream(f)
        } else {
            ins = context.contentResolver.openInputStream(uri)
        }

        var message=KKTMessage()
        val br: BufferedReader = BufferedReader(InputStreamReader(ins!!, "UTF-8"))

        try {
            loop@ while (true) {
                val line = br.readLine()
                if (line == null) {
                    if (prevlt == LineType.MESSAGE) {
                        insertMessage(messageList, time!!, nick, text)
                    }
                    break
                }

                val res=getLineType(line)
                when (res.first) {
                    // Pair(LineType.HEADER, chatTitle:String)
                    LineType.HEADER  -> {
                        val chatTitle=res.second as String
                    }
                    // Pair(LineType.SAVING_TIME, Pair<savingTime:Date, startPosition:Int>)
                    LineType.SAVING_TIME -> {
                        val savingTime:Date = res.second as Date
                    }
                    // Pair(LineType.CHUNK_START, Date)
                    LineType.CHUNK_START -> {
                        chunkcount++
                        val chatChunkTime=res.second
                        if (prevlt == LineType.MESSAGE) {
                            val res2= parseDateTimeExpr(line)
                            insertMessage(messageList, time!!, nick, text)
                            time=null
                            nick=""
                            text=""
                        }
                    }
                    // Pair(LineType.MESSAGE, Triple(messageTime:Date, author:String, message:String))
                    LineType.MESSAGE -> {
                        if (prevlt == LineType.MESSAGE || prevlt == LineType.MESSAGE_CONTINUED) {
                            insertMessage(messageList, time!!, nick, text)
                            time=null
                            nick=""
                            text=""
                        }
                        val res2=res.second as Triple<*, *, *>
                        time=res2.first as Date
                        nick=res2.second as String
                        text=res2.third as String
                    }
                    // Pair(LineType.TEXT, line:String)
                    LineType.MESSAGE_CONTINUED -> {
                        if (prevlt == LineType.SAVING_TIME) {
                            continue@loop
                        }
                        else if (prevlt == LineType.MESSAGE) {
                            text += if (line.isEmpty()) "\n" else line
                            continue@loop
                        }
                    }
                    else -> {
                        throw IOException()
                    }
                }
                prevlt = res.first
            }
            count += 1
        } catch (e:InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            br.close()
            Log.d("mike", "Chuck count = $chunkcount")
        }

        return messageList
    }

    private fun insertMessage(messageList:ArrayList<KKTMessage>, time: Date, nick: String, text: String) {
        val msgId=messageList.size.toString()
        val name=if (myName != null && nick=="회원님") myName else nick
        val author=KKTAuthor(name, name, null)
//        val message=KKTMessage(msgId, text.trim(), author, time)
        var newtext=text.trim()
        val fileinfo=parseMedia(text)
        if (fileinfo.first != FileType.NONE) {
            // filename --> uri
//            newtext=uri.toString()
            val f=File(dataDirectory, fileinfo.second)
            if (f.exists()) {
                newtext=f.toURI().toString()
            }
        }

//        val message=KKTMessage(msgId, "[$msgId] $newtext", author, time)
        val message=KKTMessage(msgId, newtext, author, time)

        messageList.add(message)

//        Log.d("mike", message.toString())
    }

    private fun writeMessageToDB(messageList:ArrayList<KKTMessage>, date: String, message: KKTMessage, trimEnd: String) {
        TODO("Not yet implemented")
    }


    private fun getDateFromString(s: String) : Date? {
        // 2018년 12월 5일 오후 4:49
        val fmt = DateTimeFormatter.ofPattern("yyyy'년' MM'월' dd'일' a HH:mm", Locale.getDefault())
        val datetime = try {
            Date.from(LocalDateTime.parse(s, fmt).atZone(ZoneId.systemDefault()).toInstant())
        } catch (e: DateTimeParseException) {
            return null
        }

        return datetime
    }


    public fun readFolder(uri:Uri?=null) : ArrayList<KKTMessage> {
        val messageList=ArrayList<KKTMessage>()

        messageList.addAll(getDummyMessage())

        return messageList
    }

    private fun getDummyMessage() : ArrayList<KKTMessage> {
        val messageList=ArrayList<KKTMessage>()

        for (i in 0..10) {
            val msg=KKTMessage.random()
            messageList.add(msg)
        }

        return messageList
    }
}