package com.soonsim.kktlogviewer;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.stfalcon.chatkit.commons.ImageLoader;
import com.stfalcon.chatkit.commons.ViewHolder;
import com.stfalcon.chatkit.commons.models.IMessage;
import com.stfalcon.chatkit.messages.MessageHolders;
import com.stfalcon.chatkit.messages.MessagesListAdapter;
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Date;

import static org.apache.commons.lang3.math.NumberUtils.max;
import static org.apache.commons.lang3.math.NumberUtils.min;

public class MyMessageListAdapter<MESSAGE extends IMessage> extends MessagesListAdapter<MESSAGE> {

    public MyMessageListAdapter(String senderId, ImageLoader imageLoader) {
        super(senderId, imageLoader);
    }

    public MyMessageListAdapter(String senderId, MessageHolders holders, ImageLoader imageLoader) {
        super(senderId, holders, imageLoader);
    }

//    @NotNull
//    @Override
//    public CharSequence onChange(int i) {
//        // when reversed
//        int i1=max(0, min(items.size()-i, items.size()-1));
////        int i1=i;
//        Wrapper wrapper=items.get(i1);
//        Date t = null;
//        if (wrapper.item instanceof IMessage) {
//            MESSAGE message = (MESSAGE) wrapper.item;
//            t=message.getCreatedAt();
//        } else if (wrapper.item instanceof Date) {
//            t = (Date) wrapper.item;
//        }
//        return DateFormatUtils.ISO_8601_EXTENDED_DATE_FORMAT.format(t);
//    }
}
