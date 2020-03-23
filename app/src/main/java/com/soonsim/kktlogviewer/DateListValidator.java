package com.soonsim.kktlogviewer;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Calendar;
import com.google.android.material.datepicker.CalendarConstraints.DateValidator;


public class DateListValidator implements DateValidator {

    private ArrayList<Long> list=new ArrayList<Long>();

    public DateListValidator() {
    }

    public DateListValidator(ArrayList<Long> orglist) {
        list.addAll(orglist);
    }

    protected DateListValidator(Parcel in) {
        if (in.readByte() == 0x01) {
            list = new ArrayList<Long>();
            in.readList(list, Long.class.getClassLoader());
        } else {
            list = null;
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (list == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeList(list);
        }
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<DateListValidator> CREATOR = new Parcelable.Creator<DateListValidator>() {
        @Override
        public DateListValidator createFromParcel(Parcel in) {
            return new DateListValidator(in);
        }

        @Override
        public DateListValidator[] newArray(int size) {
            return new DateListValidator[size];
        }
    };

    @Override
    public boolean isValid(long date) {

        return list.contains(date);

    }
}
